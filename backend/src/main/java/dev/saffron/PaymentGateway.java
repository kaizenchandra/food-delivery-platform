package dev.saffron;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
@Component class PaymentGateway {
 private final String mode,key,base,origin;final List<String> webhookSecrets;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(700)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final Semaphore bulkhead=new Semaphore(8);
 PaymentGateway(@Value("${payments.mode:disabled}")String mode,@Value("${payments.secret-key:}")String key,@Value("${payments.webhook-secrets:}")String secrets,@Value("${payments.local-base:http://localhost:8508}")String base,@Value("${app.origin}")String origin,@Value("${app.allow-insecure-local:false}")boolean local){
 if(!Set.of("disabled","local","stripe-test").contains(mode))throw new IllegalStateException("Unknown payment mode");
 if(!mode.equals("disabled")&&(!key.startsWith("sk_test_")||secrets.isBlank()||Arrays.stream(secrets.split(",")).anyMatch(s->s.strip().length()<32)))throw new IllegalStateException("Test-mode payment key and strong webhook secret required");
 URI uri=URI.create(base);if(mode.equals("local")&&(!local||!Set.of("localhost","127.0.0.1").contains(uri.getHost())||!"http".equals(uri.getScheme())||uri.getUserInfo()!=null||uri.getQuery()!=null))throw new IllegalStateException("Local payment sandbox is loopback-only");
 this.mode=mode;this.key=key;this.base=mode.equals("stripe-test")?"https://api.stripe.com":base;this.origin=origin;this.webhookSecrets=secrets.isBlank()?List.of():Arrays.stream(secrets.split(",")).map(String::strip).toList();
 }
 boolean enabled(){return !mode.equals("disabled");}
 String provider(){if(mode.equals("disabled"))throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,"Configure payment sandbox first");return mode;}
 private JsonNode call(String method,String path,Map<String,String> form,String idem){provider();if(!bulkhead.tryAcquire())throw new IllegalStateException("Payment provider busy");CompletableFuture<HttpResponse<byte[]>> pending=null;try{
 var builder=HttpRequest.newBuilder(URI.create(base+path)).timeout(Duration.ofSeconds(2)).header("Authorization","Bearer "+key).header("Stripe-Version","2026-10-28.endive").header("Accept","application/json");
 if(idem!=null)builder.header("Idempotency-Key",idem);
 if(method.equals("GET"))builder.GET();else{String payload=form.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8)).collect(java.util.stream.Collectors.joining("&"));builder.header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(payload));}
 pending=client.sendAsync(builder.build(),info->new RoadRouter.LimitedBody());var response=pending.get(2300,TimeUnit.MILLISECONDS);
 if(response.statusCode()<200||response.statusCode()>=300)throw new IllegalStateException("Provider HTTP "+response.statusCode());JsonNode node=JsonMapper.builder().build().readTree(response.body());if(!node.isObject()||node.path("livemode").asBoolean(false))throw new IllegalStateException("Live-mode or invalid provider response rejected");return node;
 }catch(Exception error){if(pending!=null)pending.cancel(true);if(error instanceof InterruptedException)Thread.currentThread().interrupt();throw new IllegalStateException("Sandbox operation not confirmed");}finally{bulkhead.release();}}
 JsonNode createSession(UUID payment,UUID order,long amount){Map<String,String> form=new TreeMap<>();form.put("mode","payment");form.put("payment_method_types[0]","card");form.put("line_items[0][price_data][currency]","inr");form.put("line_items[0][price_data][unit_amount]",Long.toString(amount));form.put("line_items[0][price_data][product_data][name]","Saffron order "+order);form.put("line_items[0][quantity]","1");form.put("client_reference_id",order.toString());form.put("metadata[paymentId]",payment.toString());form.put("metadata[orderId]",order.toString());form.put("payment_intent_data[metadata][paymentId]",payment.toString());form.put("payment_intent_data[metadata][orderId]",order.toString());form.put("success_url",origin+"/orders/"+order+"?payment_return=success");form.put("cancel_url",origin+"/orders/"+order+"?payment_return=cancel");return call("POST","/v1/checkout/sessions",form,"checkout_"+payment);}
 JsonNode session(String id){return call("GET","/v1/checkout/sessions/"+safe(id)+"?expand%5B%5D=payment_intent",Map.of(),null);}
 JsonNode expire(String id,UUID payment){return call("POST","/v1/checkout/sessions/"+safe(id)+"/expire",Map.of(),"expire_"+payment);}
 JsonNode refund(UUID id,UUID payment,String intent,long amount){return call("POST","/v1/refunds",Map.of("payment_intent",intent,"amount",Long.toString(amount),"metadata[refundId]",id.toString(),"metadata[paymentId]",payment.toString()),"refund_"+id);}
 JsonNode refund(String id){return call("GET","/v1/refunds/"+safe(id),Map.of(),null);}
 JsonNode refunds(String intent){return call("GET","/v1/refunds?payment_intent="+safe(intent)+"&limit=100",Map.of(),null);}
 String safeCheckoutUrl(String value){if(value==null||value.length()>2048)throw new IllegalArgumentException("Invalid checkout URL");URI uri=URI.create(value);boolean allowed=mode.equals("stripe-test")?"https".equals(uri.getScheme())&&"checkout.stripe.com".equals(uri.getHost()):uri.getScheme().equals("http")&&Objects.equals(uri.getHost(),URI.create(base).getHost())&&uri.getPort()==URI.create(base).getPort();if(!allowed||uri.getUserInfo()!=null)throw new IllegalArgumentException("Untrusted checkout host");return value;}
 private static String safe(String id){if(id==null||!id.matches("[a-zA-Z0-9_]{1,200}"))throw new IllegalArgumentException("Invalid provider reference");return id;}
}
