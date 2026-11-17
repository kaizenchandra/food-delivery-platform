package dev.saffron;
import java.time.Instant;
import java.util.*;
import java.nio.charset.StandardCharsets;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class PaymentSecurityTest {
 private static final String SECRET="whsec_test_secret_for_unit_verification_only_123";
 private static String signed(byte[] payload,long timestamp,String secret)throws Exception{Mac mac=Mac.getInstance("HmacSHA256");mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8),"HmacSHA256"));mac.update((timestamp+".").getBytes(StandardCharsets.UTF_8));return "t="+timestamp+",v1="+HexFormat.of().formatHex(mac.doFinal(payload));}
 @Test void rawBodySignatureAndRotation()throws Exception{var now=Instant.now();byte[] raw="{\"amount\":100}".getBytes(StandardCharsets.UTF_8);String header=signed(raw,now.getEpochSecond(),SECRET);assertTrue(WebhookSignature.verify(raw,header,List.of("old_key_for_rotation_more_than_32_chars",SECRET),now));assertFalse(WebhookSignature.verify("{ \"amount\":100}".getBytes(),header,List.of(SECRET),now));}
 @Test void duplicateSignatureSupportsRotation()throws Exception{var now=Instant.now();byte[] raw="{}".getBytes();String header=signed(raw,now.getEpochSecond(),SECRET)+",v1="+"0".repeat(64);assertTrue(WebhookSignature.verify(raw,header,List.of(SECRET),now));}
 @Test void staleAndFutureSignaturesRejected()throws Exception{var now=Instant.now();byte[] raw="{}".getBytes();assertFalse(WebhookSignature.verify(raw,signed(raw,now.getEpochSecond()-301,SECRET),List.of(SECRET),now));assertFalse(WebhookSignature.verify(raw,signed(raw,now.getEpochSecond()+301,SECRET),List.of(SECRET),now));}
 @Test void malformedAndOversizedRejected()throws Exception{var now=Instant.now();byte[] raw="{}".getBytes();assertFalse(WebhookSignature.verify(raw,"t=bad,v1=x",List.of(SECRET),now));assertFalse(WebhookSignature.verify(raw,signed(raw,now.getEpochSecond(),SECRET)+",t=1",List.of(SECRET),now));assertFalse(WebhookSignature.verify(new byte[65537],"t=1,v1=x",List.of(SECRET),now));}
 @Test void liveCredentialsCannotBeUsed(){assertThrows(IllegalStateException.class,()->new PaymentGateway("stripe-test","sk_live_never_allowed",SECRET,"http://localhost:8508","https://food.example",false));}
 @Test void testCheckoutHostIsConstrained(){var gateway=new PaymentGateway("stripe-test","sk_test_fixture",SECRET,"http://localhost:8508","https://food.example",false);assertEquals("https://checkout.stripe.com/c/pay/test",gateway.safeCheckoutUrl("https://checkout.stripe.com/c/pay/test"));assertThrows(IllegalArgumentException.class,()->gateway.safeCheckoutUrl("https://evil.example/pay"));}
}
