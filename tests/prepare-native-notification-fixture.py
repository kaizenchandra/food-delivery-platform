"""Prepare ephemeral local test TLS/VAPID material. No production secrets or system trust changes."""
import argparse,json,os,pathlib,shutil,subprocess,sys
p=argparse.ArgumentParser();p.add_argument('--directory',default='/tmp/saffron-native-notification-fixture');a=p.parse_args();root=pathlib.Path(a.directory).resolve()
if root.exists() and any(root.iterdir()):raise SystemExit('Use an empty fixture directory; existing files are never overwritten')
root.mkdir(mode=0o700,parents=True,exist_ok=True);root.chmod(0o700)
java_home=os.environ.get('JAVA_HOME')
if not java_home and sys.platform=='darwin':java_home=subprocess.check_output(['/usr/libexec/java_home','-v','21'],text=True).strip()
if not java_home:java_home=str(pathlib.Path(shutil.which('java')).resolve().parents[1])
with (root/'fixture-setup.log').open('w') as log:
 subprocess.run(['openssl','req','-x509','-newkey','rsa:2048','-nodes','-keyout',str(root/'smtp.key'),'-out',str(root/'smtp.crt'),'-days','2','-subj','/CN=localhost','-addext','subjectAltName=DNS:localhost'],check=True,stdout=log,stderr=log)
 (root/'smtp.key').chmod(0o600);shutil.copyfile(pathlib.Path(java_home)/'lib/security/cacerts',root/'truststore')
 subprocess.run([str(pathlib.Path(java_home)/'bin/keytool'),'-importcert','-noprompt','-alias','saffron-smtp-fixture','-file',str(root/'smtp.crt'),'-keystore',str(root/'truststore'),'-storepass','changeit'],check=True,stdout=log,stderr=log)
script="""const fs=require('node:fs'),c=require('node:crypto');const {privateKey}=c.generateKeyPairSync('ec',{namedCurve:'prime256v1'});const j=privateKey.export({format:'jwk'});const pub=Buffer.concat([Buffer.from([4]),Buffer.from(j.x,'base64url'),Buffer.from(j.y,'base64url')]).toString('base64url');fs.writeFileSync(process.argv[1],`EMAIL_MODE=smtp\\nMAIL_HOST=localhost\\nMAIL_PORT=8668\\nMAIL_USERNAME=fixture\\nMAIL_PASSWORD=fixture-password\\nMAIL_FROM=operator@local.invalid\\nPUSH_MODE=webpush\\nVAPID_PUBLIC_KEY=${pub}\\nVAPID_PRIVATE_KEY=${j.d}\\nVAPID_SUBJECT=mailto:operator@example.invalid\\n`,{mode:0o600})"""
subprocess.run(['node','-e',script,str(root/'config.env')],check=True)
print('Prepared private local fixture in '+str(root))
