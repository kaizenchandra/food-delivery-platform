"""Run after notification-channels.cjs, on the local Compose demo only. Restarts the local simulator."""
import argparse,hashlib,json,os,pathlib,subprocess,time,urllib.request,uuid
root=pathlib.Path(__file__).resolve().parents[1]
parser=argparse.ArgumentParser();parser.add_argument('--evidence',default='/tmp/saffron-channels-evidence.json');args=parser.parse_args()
source=str(uuid.UUID(json.load(open(args.evidence))['emailDelivery']))
def sql(query):return subprocess.check_output(['docker','exec','saffron-postgres-1','psql','-U','saffron','-d','saffron','-v','ON_ERROR_STOP=1','-tAc',query],text=True).strip()
reference=sql("select provider_id from notification_deliveries where id='"+source+"' and mode='local'")
assert reference.startswith('sim_')
def digest():
 request=urllib.request.Request('http://localhost:8608/v1/deliveries/'+reference,headers={'Authorization':'Bearer '+os.getenv('LOCAL_CHANNEL_API_KEY','saffron_local_channel_api_fixture_32bytes')})
 with urllib.request.urlopen(request,timeout=5) as response:return hashlib.sha256(response.read()).hexdigest()
before=digest();subprocess.run(['docker','compose','restart','notification-simulator'],cwd=root,check=True,stdout=subprocess.DEVNULL)
for attempt in range(20):
 try:
  assert before==digest();break
 except OSError:
  if attempt==19:raise
  time.sleep(.25)
job=str(uuid.uuid4());notice=str(uuid.uuid4())
try:
 sql(f"insert into notifications(id,event_key,audience,subject,type,resource_id,title,href) select '{notice}','aging-{notice}',n.audience,n.subject,n.type,n.resource_id,n.title,n.href from notifications n join notification_deliveries d on d.notification_id=n.id where d.id='{source}'; insert into notification_deliveries(id,notification_id,subject,channel,mode,destination,subscription,setting_version,state,provider_id,created_at) select '{job}','{notice}',subject,channel,'smtp',destination,subscription,setting_version,'ACCEPTED',provider_id,now()-interval '4 days' from notification_deliveries where id='{source}'")
 for i in range(14):
  time.sleep(.5);assert sql(f"select state from notification_deliveries where id='{job}'")=='ACCEPTED'
finally:sql(f"delete from notification_receipts where notification_id='{notice}'; delete from notification_deliveries where id='{job}'; delete from notifications where id='{notice}'")
result={'result':'PASS','checks':['Simulator preserves the captured receipt after restart','Known SMTP acceptance is not aged into unknown polling status; isolated fixture removed']}
pathlib.Path('/tmp/saffron-channel-durability.json').write_text(json.dumps(result,indent=2));print('PASS notification simulator durability and acceptance aging')
