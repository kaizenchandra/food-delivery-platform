"""Loopback-only integration harness: real OSRM forwarding plus a secret-protected logout relay.
Logout tokens stay in memory. Neither request paths nor credentials/tokens are logged.
"""
import http.server, urllib.request, urllib.error, urllib.parse, json, secrets, pathlib, os, threading, time, subprocess
CONTROL=pathlib.Path('/tmp/saffron-provider-gate.json')
secret=secrets.token_urlsafe(32);relay_secret=secrets.token_urlsafe(32)
CONTROL.write_text(json.dumps({'secret':secret,'relayPath':'/provider/logout/'+relay_secret,'url':'http://localhost:5090'}));os.chmod(CONTROL,0o600)
state={'mode':'live','road_requests':0,'logout_deliveries':0,'last_status':None};lock=threading.Lock();last_logout=None
class Handler(http.server.BaseHTTPRequestHandler):
 def log_message(self,*args):pass
 def reply(self,status,body,kind='application/json'):
  self.send_response(status);self.send_header('Content-Type',kind);self.send_header('Content-Length',str(len(body)));self.end_headers()
  try:self.wfile.write(body)
  except (BrokenPipeError,ConnectionResetError):pass
 def control(self):return secrets.compare_digest(self.headers.get('X-Test-Control',''),secret)
 def forward_logout(self,body):
  req=urllib.request.Request('http://localhost:8088/logout/connect/back-channel/keycloak',data=body,headers={'Content-Type':'application/x-www-form-urlencoded'})
  try:
   with urllib.request.urlopen(req,timeout=10) as response:return response.status,response.read(16384)
  except urllib.error.HTTPError as error:return error.code,error.read(16384)
 def do_POST(self):
  global last_logout
  size=int(self.headers.get('Content-Length','0'))
  if size>16384:self.reply(413,b'{}');return
  if not (self.path=='/provider/logout/'+relay_secret or self.control()):self.reply(403,b'{}');return
  body=self.rfile.read(size)
  if self.path=='/provider/logout/'+relay_secret:
   if not self.headers.get('Content-Type','').startswith('application/x-www-form-urlencoded'):self.reply(415,b'{}');return
   status,result=self.forward_logout(body)
   if status==200:last_logout=body
   with lock:state['logout_deliveries']+=1;state['last_status']=status
   self.reply(status,result);return
  if self.path=='/__mode':
   mode=json.loads(body).get('mode')
   if mode not in ('live','outage','malformed','slow'):self.reply(400,b'{}');return
   with lock:state['mode']=mode
   self.reply(200,b'{}');return
  if self.path=='/__replay' and last_logout:
   status,result=self.forward_logout(last_logout);self.reply(status,result);return
  if self.path=='/__tamper' and last_logout:
   form=urllib.parse.parse_qs(last_logout.decode());pieces=form['logout_token'][0].split('.');pieces[2]=('A' if pieces[2][0]!='A' else 'B')+pieces[2][1:];status,result=self.forward_logout(urllib.parse.urlencode({'logout_token':'.'.join(pieces)}).encode());self.reply(status,result);return
  self.reply(404,b'{}')
 def do_GET(self):
  if self.path=='/__stats':
   if not self.control():self.reply(403,b'{}');return
   self.reply(200,json.dumps(state).encode());return
  if not self.path.startswith('/route/v1/driving/') or len(self.path)>1000:self.reply(404,b'{}');return
  with lock:mode=state['mode'];state['road_requests']+=1
  if mode=='outage':self.reply(503,b'{}');return
  if mode=='malformed':self.reply(200,b'{invalid-json');return
  if mode=='slow':time.sleep(4)
  try:
   result=subprocess.run(['curl','--fail','--silent','--show-error','--max-time','8','--max-filesize','1048576','--user-agent','Saffron-local-verification/0.1','https://router.project-osrm.org'+self.path],capture_output=True,timeout=9,check=True)
   self.reply(200,result.stdout)
  except Exception:self.reply(503,b'{}')
http.server.ThreadingHTTPServer(('127.0.0.1',5090),Handler).serve_forever()
