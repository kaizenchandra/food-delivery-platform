"""Loopback-only positive SMTP integration fixture; no outbound mail."""
import argparse,socketserver,ssl,base64,json,pathlib,threading
p=argparse.ArgumentParser();p.add_argument('--directory',required=True);p.add_argument('--port',type=int,default=8668);a=p.parse_args();root=pathlib.Path(a.directory);lock=threading.Lock();context=ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER);context.load_cert_chain(root/'smtp.crt',root/'smtp.key')
class SMTP(socketserver.StreamRequestHandler):
 def reply(self,message):self.wfile.write((message+'\r\n').encode());self.wfile.flush()
 def handle(self):
  secure=False;auth=False;recipient='';self.reply('220 loopback acceptance SMTP')
  while True:
   raw=self.rfile.readline(16384)
   if not raw:return
   line=raw.decode(errors='replace').strip();cmd=line.split(' ',1)[0].upper();
   with lock:
    with (root/'smtp-stages.txt').open('a') as trace:trace.write(cmd+' '+('TLS' if secure else 'PLAIN')+'\n')
   if cmd in ('EHLO','HELO'):self.reply('250-localhost\r\n250-AUTH PLAIN\r\n250 STARTTLS' if not secure else '250-localhost\r\n250 AUTH PLAIN')
   elif cmd=='STARTTLS':
    self.reply('220 Ready for TLS');self.connection=context.wrap_socket(self.connection,server_side=True);self.rfile=self.connection.makefile('rb');self.wfile=self.connection.makefile('wb');secure=True
   elif cmd=='AUTH':
    if not secure:self.reply('530 TLS required');continue
    parts=base64.b64decode(line.split()[-1]).split(b'\0');auth=len(parts)==3 and parts[0] in (b'',b'fixture') and parts[-2:]==[b'fixture',b'fixture-password'];self.reply('235 Authenticated' if auth else '535 Invalid credentials')
   elif cmd=='MAIL':self.reply('250 Sender accepted' if secure and auth else '530 TLS/auth required')
   elif cmd=='RCPT':recipient=line;self.reply('250 Recipient accepted')
   elif cmd=='DATA':
    if not secure or not auth:self.reply('530 TLS/auth required');continue
    self.reply('354 End with dot');data=[]
    while True:
     chunk=self.rfile.readline(16384)
     if not chunk or chunk==b'.\r\n':break
     data.append(chunk)
    with lock:
     path=root/'smtp-messages.json';messages=json.loads(path.read_text()) if path.exists() else [];messages.append({'tls':self.connection.version(),'authenticated':auth,'recipient':recipient,'raw':' '.join(c.decode(errors='replace') for c in data)});path.write_text(json.dumps(messages))
    self.reply('250 Message accepted')
   elif cmd=='QUIT':self.reply('221 Bye');return
   else:self.reply('250 OK')
class Server(socketserver.ThreadingTCPServer):allow_reuse_address=True;daemon_threads=True
Server(('127.0.0.1',a.port),SMTP).serve_forever()
