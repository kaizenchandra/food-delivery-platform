package dev.saffron;
import java.nio.charset.StandardCharsets;
import java.security.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.time.Instant;
import java.util.*;
final class WebhookSignature {
 static String digest(byte[] body){try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(body));}catch(Exception e){throw new IllegalStateException(e);}}
 static boolean verify(byte[] body,String header,List<String> secrets,Instant now){
 if(body.length>65536||header==null||header.length()>4096||secrets.isEmpty())return false;
 try{Long timestamp=null;List<String> signatures=new ArrayList<>();for(String field:header.split(",")){String[] pair=field.strip().split("=",2);if(pair.length!=2)return false;if(pair[0].equals("t")){if(timestamp!=null)return false;timestamp=Long.parseLong(pair[1]);}if(pair[0].equals("v1"))signatures.add(pair[1]);}
 if(timestamp==null||signatures.isEmpty()||signatures.size()>8||timestamp<now.getEpochSecond()-300||timestamp>now.getEpochSecond()+300)return false;
 byte[] prefix=(timestamp+".").getBytes(StandardCharsets.UTF_8);boolean matches=false;
 for(String secret:secrets){Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));mac.update(prefix);byte[] expected=mac.doFinal(body);for(String signature:signatures){if(signature.matches("[a-fA-F0-9]{64}"))matches|=MessageDigest.isEqual(expected,HexFormat.of().parseHex(signature));}}
 return matches;
 }catch(Exception error){return false;}
 }
}
