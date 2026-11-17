"""Independent, durable Stripe-shaped LOCAL sandbox. No real money or PAN collection."""
import os,json,sqlite3,time,uuid,hmac,hashlib,threading,http.server,urllib.parse,urllib.request,urllib.error,html,secrets
DB=os.getenv('SANDBOX_DB','/data/provider.sqlite');os.makedirs(os.path.dirname(DB),exist_ok=True)
API_KEY=os.getenv('API_KEY','sk_test_saffron_local_fixture_only')
HOOK_SECRET=os.getenv('WEBHOOK_SECRET','whsec_saffron_local_webhook_fixture_only_32bytes')
CONTROL=os.getenv('CONTROL_TOKEN','saffron_local_operator_control_fixture')
PUBLIC=os.getenv('PUBLIC_URL','http://localhost:8508');HOOK=os.getenv('WEBHOOK_URL','http://host.docker.internal:8088/api/payments/webhooks/stripe')
def connect():
 c=sqlite3.connect(DB,timeout=10);c.row_factory=sqlite3.Row;c.execute('PRAGMA journal_mode=WAL');return c
with connect() as c:
 c.executescript('''CREATE TABLE IF NOT EXISTS objects(id TEXT PRIMARY KEY,kind TEXT NOT NULL,payload TEXT NOT NULL);
 CREATE TABLE IF NOT EXISTS idempotency(endpoint TEXT,key TEXT,fingerprint TEXT,response TEXT,PRIMARY KEY(endpoint,key));
 CREATE TABLE IF NOT EXISTS events(id TEXT PRIMARY KEY,payload TEXT NOT NULL,delivered INTEGER DEFAULT 0,attempts INTEGER DEFAULT 0,next_attempt REAL DEFAULT 0);
 CREATE TABLE IF NOT EXISTS settings(key TEXT PRIMARY KEY,value TEXT NOT NULL);''')
def setting(c,key,default):
 row=c.execute('SELECT value FROM settings WHERE key=?',(key,)).fetchone();return json.loads(row[0]) if row else default
def set_setting(c,key,value):c.execute('INSERT INTO settings VALUES(?,?) ON CONFLICT(key) DO UPDATE SET value=excluded.value',(key,json.dumps(value)))
def get(c,id):
 row=c.execute('SELECT payload FROM objects WHERE id=?',(id,)).fetchone();return json.loads(row[0]) if row else None
def save(c,obj,kind):c.execute('INSERT INTO objects VALUES(?,?,?) ON CONFLICT(id) DO UPDATE SET payload=excluded.payload',(obj['id'],kind,json.dumps(obj)))
def event(c,kind,obj,id=None):
 body={'id':id or 'evt_'+uuid.uuid4().hex,'object':'event','type':kind,'created':int(time.time()),'livemode':False,'data':{'object':obj}}
 c.execute('INSERT INTO events(id,payload) VALUES(?,?)',(body['id'],json.dumps(body,separators=(',',':'))));return body['id']
def deliver(body,offset=0):
 timestamp=str(int(time.time())+offset);signature=hmac.new(HOOK_SECRET.encode(),timestamp.encode()+b'.'+body,hashlib.sha256).hexdigest()
 request=urllib.request.Request(HOOK,data=body,headers={'Content-Type':'application/json','Stripe-Signature':'t='+timestamp+',v1='+signature})
 try:
  with urllib.request.urlopen(request,timeout=3) as response:return response.status
 except urllib.error.HTTPError as error:return error.code
 except Exception:return 0
def dispatcher():
 while True:
  time.sleep(.5)
  with connect() as c:
   if not setting(c,'webhooks_enabled',True):continue
   rows=c.execute('SELECT * FROM events WHERE delivered=0 AND next_attempt<=? AND attempts<10 LIMIT 5',(time.time(),)).fetchall()
  for row in rows:
   status=deliver(row['payload'].encode())
   with connect() as c:c.execute('UPDATE events SET delivered=?,attempts=attempts+1,next_attempt=? WHERE id=?',(int(status==200),time.time()+min(30,2**min(row['attempts'],5)),row['id']))
def capture(c,obj,outcome):
 if outcome=='cancel':return obj['cancel_url']
 if obj['payment_status']=='paid':return obj['success_url']
 if obj['status']=='expired':raise ValueError('Session expired')
 if outcome=='decline':event(c,'checkout.session.async_payment_failed',obj);return obj['cancel_url']
 obj['status']='complete'
 if outcome=='pending':obj['payment_status']='unpaid';event(c,'checkout.session.completed',obj)
 else:obj['payment_status']='paid';obj['payment_intent']=obj['payment_intent'] or 'pi_'+uuid.uuid4().hex;event(c,'checkout.session.async_payment_succeeded' if obj.get('was_pending') else 'checkout.session.completed',obj)
 obj['was_pending']=outcome=='pending';save(c,obj,'session');return obj['success_url']
class Handler(http.server.BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def reply(self,status,obj,kind='application/json'):
  body=obj if isinstance(obj,bytes) else json.dumps(obj).encode();self.send_response(status);self.send_header('Content-Type',kind);self.send_header('Cache-Control','no-store');self.send_header('X-Content-Type-Options','nosniff');self.send_header('Content-Length',str(len(body)));self.end_headers()
  try:self.wfile.write(body)
  except (BrokenPipeError,ConnectionResetError):pass
 def authorized(self,control=False):return hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+(CONTROL if control else API_KEY))
 def body(self):
  n=int(self.headers.get('Content-Length','0'))
  if n>65536:raise ValueError('Payload too large')
  return self.rfile.read(n)
 def do_GET(self):
  path=urllib.parse.urlsplit(self.path);query=urllib.parse.parse_qs(path.query)
  with connect() as c:
   if path.path=='/__stats':
    if not self.authorized(True):self.reply(403,{});return
    sessions=[json.loads(r[0]) for r in c.execute("SELECT payload FROM objects WHERE kind='session'")];refunds=[json.loads(r[0]) for r in c.execute("SELECT payload FROM objects WHERE kind='refund'")]
    self.reply(200,{'sessions':len(sessions),'captures':sum(s['payment_status']=='paid' for s in sessions),'refunds':len(refunds),'refunded_amount':sum(r['amount'] for r in refunds if r['status']=='succeeded'),'events':c.execute('SELECT count(*) FROM events').fetchone()[0]});return
   if path.path.startswith('/checkout/'):
    obj=get(c,path.path.split('/')[-1]);token=query.get('token',[''])[0]
    if not obj or not hmac.compare_digest(token,obj['token']):self.reply(404,{});return
    content=f'''<!doctype html><html lang="en"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>Saffron local payment sandbox</title><style>body{{font:18px Arial;max-width:640px;margin:8vh auto;padding:24px;background:#fffdf9;color:#263c32}}button{{padding:16px;margin:8px;border-radius:10px;border:1px solid #ddd;background:#e95037;color:white;font-size:16px}}small{{display:block}}</style><h1>Local payment sandbox</h1><p>No real money. No card details collected.</p><h2>₹{obj['amount_total']/100:,.2f}</h2><p>Order {html.escape(obj['client_reference_id'])}</p><form method="post" action="/checkout/{obj['id']}"><input type="hidden" name="token" value="{html.escape(token)}"><button name="outcome" value="success">Pay successfully</button><button name="outcome" value="decline">Simulate decline</button><button name="outcome" value="pending">Simulate pending payment</button><button name="outcome" value="cancel">Back without paying</button></form><small>These controls exist only in the local simulator, not Stripe-hosted Checkout.</small></html>'''
    self.reply(200,content.encode(),'text/html; charset=utf-8');return
   if not self.authorized():self.reply(401,{});return
   if setting(c,'api_outage',False):self.reply(503,{});return
   if path.path=='/v1/refunds':
    intent=query.get('payment_intent',[''])[0];values=[json.loads(r[0]) for r in c.execute("SELECT payload FROM objects WHERE kind='refund'")];self.reply(200,{'object':'list','data':[v for v in values if v['payment_intent']==intent][:100],'has_more':len([v for v in values if v['payment_intent']==intent])>100});return
   obj=get(c,path.path.split('/')[-1]);self.reply(200,obj) if obj else self.reply(404,{})
 def do_POST(self):
  try:self.post()
  except (ValueError,KeyError,TypeError,json.JSONDecodeError):self.reply(400,{'error':{'message':'Invalid sandbox operation'}})
 def post(self):
  path=urllib.parse.urlsplit(self.path).path;raw=self.body()
  if path.startswith('/__'):
   if not self.authorized(True):self.reply(403,{});return
   data=json.loads(raw or b'{}')
   with connect() as c:
    c.execute('BEGIN IMMEDIATE')
    if path=='/__control':
     for key,value in data.items():
      if key not in ('webhooks_enabled','api_outage','session_timeout_once','refund_timeout_once','next_refund_status'):raise ValueError()
      set_setting(c,key,value)
     self.reply(200,{'updated':True});return
    if path=='/__complete_refund':
     obj=get(c,data['id']);obj['status']=data.get('status','succeeded');save(c,obj,'refund');event(c,'refund.updated',obj);self.reply(200,{'updated':True});return
    if path=='/__capture':
     obj=get(c,data['id']);capture(c,obj,'success');self.reply(200,{'updated':True});return
    if path=='/__event':
     obj=get(c,data['id']);obj['amount_total']=obj.get('amount_total',0)+data.get('amount_delta',0);ev={'id':data.get('event_id','evt_'+uuid.uuid4().hex),'type':data.get('type','checkout.session.completed'),'livemode':False,'data':{'object':obj}}
     body=json.dumps(ev,separators=(',',':')).encode();c.commit();status=deliver(body,data.get('timestamp_offset',0));self.reply(200,{'delivery_status':status,'event_id':ev['id']});return
    if path=='/__replay':
     row=c.execute('SELECT payload FROM events WHERE id=?',(data['event_id'],)).fetchone();c.commit();status=deliver(row[0].encode(),data.get('timestamp_offset',0));self.reply(200,{'delivery_status':status});return
    if path=='/__events':
     self.reply(200,[{'id':r['id'],'delivered':r['delivered']} for r in c.execute('SELECT id,delivered FROM events ORDER BY rowid DESC LIMIT 100')]);return
   self.reply(404,{});return
  if path.startswith('/checkout/'):
   form=urllib.parse.parse_qs(raw.decode());token=form.get('token',[''])[0]
   with connect() as c:
    c.execute('BEGIN IMMEDIATE');obj=get(c,path.split('/')[-1])
    if not obj or not hmac.compare_digest(token,obj['token']):self.reply(403,{});return
    url=capture(c,obj,form.get('outcome',[''])[0]);c.commit()
   self.send_response(303);self.send_header('Location',url);self.send_header('Content-Length','0');self.end_headers();return
  if not self.authorized():self.reply(401,{});return
  form={key:values[0] for key,values in urllib.parse.parse_qs(raw.decode()).items()};idem=self.headers.get('Idempotency-Key','');fingerprint=json.dumps(form,sort_keys=True,separators=(',',':'))
  if not idem or len(idem)>255:raise ValueError()
  delay=False
  with connect() as c:
   c.execute('BEGIN IMMEDIATE')
   if setting(c,'api_outage',False):self.reply(503,{});return
   prior=c.execute('SELECT * FROM idempotency WHERE endpoint=? AND key=?',(path,idem)).fetchone()
   if prior:
    if prior['fingerprint']!=fingerprint:self.reply(409,{});return
    self.reply(200,json.loads(prior['response']));return
   if path=='/v1/checkout/sessions':
    amount=int(form['line_items[0][price_data][unit_amount]']);currency=form['line_items[0][price_data][currency]']
    if amount<1 or amount>10**12 or currency!='inr':raise ValueError()
    id='cs_test_'+uuid.uuid4().hex;token=secrets.token_urlsafe(32);obj={'id':id,'object':'checkout.session','livemode':False,'amount_total':amount,'currency':currency,'client_reference_id':form['client_reference_id'],'metadata':{'paymentId':form['metadata[paymentId]']},'status':'open','payment_status':'unpaid','payment_intent':None,'success_url':form['success_url'],'cancel_url':form['cancel_url'],'token':token,'url':PUBLIC+'/checkout/'+id+'?token='+token};save(c,obj,'session')
    delay=setting(c,'session_timeout_once',False);set_setting(c,'session_timeout_once',False)
   elif path=='/v1/refunds':
    intent=form['payment_intent'];amount=int(form['amount']);sessions=[json.loads(r[0]) for r in c.execute("SELECT payload FROM objects WHERE kind='session'")];paid=next((s for s in sessions if s['payment_intent']==intent and s['payment_status']=='paid'),None);refunds=[json.loads(r[0]) for r in c.execute("SELECT payload FROM objects WHERE kind='refund'")];reserved=sum(r['amount'] for r in refunds if r['payment_intent']==intent and r['status'] not in ('failed','canceled'))
    if not paid or amount<1 or amount>paid['amount_total']-reserved:raise ValueError()
    status=setting(c,'next_refund_status','succeeded');set_setting(c,'next_refund_status','succeeded');obj={'id':'re_'+uuid.uuid4().hex,'object':'refund','livemode':False,'amount':amount,'currency':'inr','payment_intent':intent,'status':status,'metadata':{'paymentId':form['metadata[paymentId]'],'refundId':form['metadata[refundId]']}};save(c,obj,'refund');event(c,'refund.created',obj);delay=setting(c,'refund_timeout_once',False);set_setting(c,'refund_timeout_once',False)
   elif path.startswith('/v1/checkout/sessions/') and path.endswith('/expire'):
    obj=get(c,path.split('/')[-2])
    if not obj or obj['status']!='open' or obj['payment_status']=='paid':raise ValueError()
    obj['status']='expired';save(c,obj,'session');event(c,'checkout.session.expired',obj)
   else:self.reply(404,{});return
   c.execute('INSERT INTO idempotency VALUES(?,?,?,?)',(path,idem,fingerprint,json.dumps(obj)));c.commit()
  if delay:time.sleep(4)
  self.reply(200,obj)
threading.Thread(target=dispatcher,daemon=True).start()
http.server.ThreadingHTTPServer(('0.0.0.0',8508),Handler).serve_forever()
