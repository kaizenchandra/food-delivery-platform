"""Local-only email/SMS/push provider. Persistent, independently committed receipts; no outbound sends."""
import os,json,sqlite3,uuid,time,hmac,http.server,urllib.parse,html
DB=os.getenv('SIMULATOR_DB','/data/channels.sqlite');os.makedirs(os.path.dirname(DB),exist_ok=True)
KEY=os.getenv('API_KEY','saffron_local_channel_api_fixture_32bytes');CONTROL=os.getenv('CONTROL_TOKEN','saffron_local_channel_control_fixture_32bytes')
def db():
 c=sqlite3.connect(DB,timeout=10);c.row_factory=sqlite3.Row;c.execute('PRAGMA journal_mode=WAL');return c
with db() as c:
 c.executescript('CREATE TABLE IF NOT EXISTS messages(id TEXT PRIMARY KEY,job TEXT UNIQUE,fingerprint TEXT,payload TEXT,state TEXT);CREATE TABLE IF NOT EXISTS settings(channel TEXT PRIMARY KEY,mode TEXT);')
class Handler(http.server.BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def auth(self,control=False):return hmac.compare_digest(self.headers.get('Authorization',''),'Bearer '+(CONTROL if control else KEY))
 def reply(self,status,data):
  raw=json.dumps(data).encode();self.send_response(status);self.send_header('Content-Type','application/json');self.send_header('Cache-Control','no-store');self.send_header('Content-Length',str(len(raw)));self.end_headers()
  try:self.wfile.write(raw)
  except (BrokenPipeError,ConnectionResetError):pass
 def do_GET(self):
  if not self.auth():self.reply(401,{});return
  with db() as c:
   row=c.execute('SELECT * FROM messages WHERE id=?',(self.path.split('/')[-1],)).fetchone()
   if not row:self.reply(404,{});return
   mode=c.execute('SELECT mode FROM settings WHERE channel=?',(json.loads(row['payload'])['channel'],)).fetchone()
   if mode and mode[0]=='outage':self.reply(503,{});return
   self.reply(200,{'id':row['id'],'state':row['state'],'payload':json.loads(row['payload'])})
 def do_POST(self):
  try:self.post()
  except (ValueError,KeyError,TypeError,json.JSONDecodeError):self.reply(400,{'error':'Invalid simulator request'})
 def post(self):
  if not self.auth(self.path.startswith('/__')):self.reply(401,{});return
  size=int(self.headers.get('Content-Length','0'))
  if size<0 or size>16384:self.reply(413,{});return
  data=json.loads(self.rfile.read(size));delay=False
  with db() as c:
   c.execute('BEGIN IMMEDIATE')
   if self.path=='/__control':
    if data['channel'] not in ('EMAIL','SMS','PUSH') or data['mode'] not in ('ok','outage','timeout_once','permanent','pending','expired'):raise ValueError()
    c.execute('INSERT INTO settings VALUES(?,?) ON CONFLICT(channel) DO UPDATE SET mode=excluded.mode',(data['channel'],data['mode']));self.reply(200,{'updated':True});return
   if self.path=='/__complete':
    if data['state'] not in ('DELIVERED','FAILED'):raise ValueError()
    c.execute('UPDATE messages SET state=? WHERE id=?',(data['state'],data['id']));self.reply(200,{'updated':True});return
   if self.path=='/__stats':self.reply(200,{'messages':c.execute('SELECT count(*) FROM messages').fetchone()[0]});return
   if self.path!='/v1/deliveries':self.reply(404,{});return
   job=self.headers.get('Idempotency-Key','');uuid.UUID(job)
   channel=data['channel']
   if channel not in ('EMAIL','SMS','PUSH'):raise ValueError()
   fingerprint=json.dumps(data,sort_keys=True,separators=(',',':'));old=c.execute('SELECT * FROM messages WHERE job=?',(job,)).fetchone()
   if old:
    if old['fingerprint']!=fingerprint:self.reply(409,{});return
    self.reply(200,{'id':old['id'],'state':old['state']});return
   setting=c.execute('SELECT mode FROM settings WHERE channel=?',(channel,)).fetchone();mode=setting[0] if setting else 'ok'
   if mode=='outage':self.reply(503,{});return
   if mode in ('permanent','expired'):self.reply(410 if mode=='expired' else 400,{});return
   state='ACCEPTED' if mode=='pending' else 'DELIVERED';id='sim_'+uuid.uuid4().hex
   c.execute('INSERT INTO messages VALUES(?,?,?,?,?)',(id,job,fingerprint,json.dumps(data),state))
   if mode=='timeout_once':c.execute("UPDATE settings SET mode='ok' WHERE channel=?",(channel,));delay=True
   c.commit()
  if delay:time.sleep(4)
  self.reply(200,{'id':id,'state':state})
http.server.ThreadingHTTPServer(('0.0.0.0',8608),Handler).serve_forever()
