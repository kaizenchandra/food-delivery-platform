package dev.saffron;
import java.net.URI;
import java.util.Set;
final class TransportPolicy {
 static void validate(String origin,String issuer,String callback,boolean secureCookie,boolean local){
  for(String value:new String[]{origin,issuer,callback}){
   URI uri;try{uri=URI.create(value);}catch(RuntimeException e){throw new IllegalStateException("Invalid identity/application URL");}
   if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getFragment()!=null||uri.getQuery()!=null)throw new IllegalStateException("Invalid identity/application URL");
   if(local){if(!Set.of("localhost","127.0.0.1","[::1]").contains(uri.getHost()))throw new IllegalStateException("Local identity mode requires loopback URLs");}
   else if(!"https".equals(uri.getScheme()))throw new IllegalStateException("HTTPS required for OIDC and application URLs");
  }
  if(!local&&!secureCookie)throw new IllegalStateException("Secure session cookies required outside local mode");
 }
}
