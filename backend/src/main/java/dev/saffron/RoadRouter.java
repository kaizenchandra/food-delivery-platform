package dev.saffron;

import java.net.*;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.io.ByteArrayOutputStream;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.Flow;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

@Service class RoadRouter {
 record Point(double longitude,double latitude){Point {if(!Double.isFinite(longitude)||!Double.isFinite(latitude)||Math.abs(longitude)>180||Math.abs(latitude)>90)throw new IllegalArgumentException("Invalid route coordinates");}}
 record Step(String instruction,double distanceMeters,double durationSeconds){}
 record Route(String mode,String reason,double distanceMeters,double durationSeconds,List<Point> geometry,List<Step> steps,String attribution,String generatedAt){}
 private final String baseUrl;
 private final HttpClient client=HttpClient.newBuilder().connectTimeout(Duration.ofMillis(700)).followRedirects(HttpClient.Redirect.NEVER).build();
 private final Semaphore bulkhead=new Semaphore(8);
 private final AtomicLong unavailableUntil=new AtomicLong();
 RoadRouter(@Value("${routing.base-url:}")String baseUrl,@Value("${app.allow-insecure-local:false}")boolean local){
 if(!baseUrl.isBlank()){URI uri=URI.create(baseUrl);if(uri.getHost()==null||uri.getUserInfo()!=null||uri.getQuery()!=null||uri.getFragment()!=null||(!"https".equals(uri.getScheme())&&!(local&&"http".equals(uri.getScheme())&&Set.of("localhost","127.0.0.1").contains(uri.getHost()))))throw new IllegalStateException("Routing endpoint must be configured HTTPS (loopback HTTP only in local mode)");}
 this.baseUrl=baseUrl.replaceAll("/+$","");
 }
 Route route(Point from,Point to){
 if(distance(from,to)<10)return simulator(from,to,"AT_DESTINATION");
 if(baseUrl.isBlank())return simulator(from,to,"NOT_CONFIGURED");
 if(System.currentTimeMillis()<unavailableUntil.get())return simulator(from,to,"PROVIDER_BACKOFF");
 if(!bulkhead.tryAcquire())return simulator(from,to,"PROVIDER_BUSY");
 CompletableFuture<HttpResponse<byte[]>> pending=null;
 try{
 String path=String.format(Locale.ROOT,"/route/v1/driving/%.6f,%.6f;%.6f,%.6f?overview=simplified&geometries=geojson&steps=true&alternatives=false",from.longitude,from.latitude,to.longitude,to.latitude);
 var request=HttpRequest.newBuilder(URI.create(baseUrl+path)).timeout(Duration.ofSeconds(2)).header("Accept","application/json").GET().build();
 pending=client.sendAsync(request,info->new LimitedBody());var response=pending.get(2300,TimeUnit.MILLISECONDS);
 if(response.statusCode()!=200)throw new IllegalArgumentException("Routing provider unavailable");
 return parse(response.body(),from,to);
 }catch(Exception error){if(pending!=null)pending.cancel(true);if(error instanceof InterruptedException)Thread.currentThread().interrupt();unavailableUntil.set(System.currentTimeMillis()+5000);return simulator(from,to,"PROVIDER_UNAVAILABLE");}
 finally{bulkhead.release();}
 }
 static Route parse(byte[] bytes,Point from,Point to){
 if(bytes.length>1_048_576)throw new IllegalArgumentException("Route response too large");
 JsonNode data=JsonMapper.builder().build().readTree(bytes);
 if(!"Ok".equals(data.path("code").asString()))throw new IllegalArgumentException("No route");
 JsonNode routes=data.path("routes");if(!routes.isArray()||routes.isEmpty())throw new IllegalArgumentException("No route");JsonNode route=routes.get(0);
 double meters=number(route.path("distance")),seconds=number(route.path("duration"));
 if(meters>200_000||seconds>86400||meters<distance(from,to)*0.5)throw new IllegalArgumentException("Implausible route");
 var coords=route.path("geometry").path("coordinates");if(!"LineString".equals(route.path("geometry").path("type").asString())||!coords.isArray()||coords.size()<2||coords.size()>2000)throw new IllegalArgumentException("Invalid geometry");
 List<Point> geometry=new ArrayList<>();for(var coordinate:coords){if(!coordinate.isArray()||coordinate.size()!=2||!coordinate.get(0).isNumber()||!coordinate.get(1).isNumber())throw new IllegalArgumentException("Invalid coordinate");geometry.add(new Point(coordinate.get(0).asDouble(),coordinate.get(1).asDouble()));}
 if(distance(from,geometry.getFirst())>1500||distance(to,geometry.getLast())>1500)throw new IllegalArgumentException("Route endpoints too far from request");
 List<Step> steps=new ArrayList<>();for(var leg:route.path("legs"))for(var step:leg.path("steps")){if(steps.size()>=80)break;String turn=step.path("maneuver").path("type").asString("continue").replace('_',' ');String modifier=step.path("maneuver").path("modifier").asString("");String road=step.path("name").asString("");String instruction=(turn+" "+modifier+(road.isBlank()?"":" onto "+road)).strip();if(instruction.length()>160)instruction=instruction.substring(0,160);steps.add(new Step(instruction,number(step.path("distance")),number(step.path("duration"))));}
 return new Route("ROAD","OSRM",meters,seconds,List.copyOf(geometry),List.copyOf(steps),"Routing: OSRM · map data © OpenStreetMap contributors",Instant.now().toString());
 }
 static double number(JsonNode n){if(!n.isNumber())throw new IllegalArgumentException("Missing numeric route field");double value=n.asDouble();if(!Double.isFinite(value)||value<0)throw new IllegalArgumentException("Invalid route value");return value;}
 static double distance(Point a,Point b){double lat=Math.toRadians(b.latitude-a.latitude),lon=Math.toRadians(b.longitude-a.longitude);double h=Math.pow(Math.sin(lat/2),2)+Math.cos(Math.toRadians(a.latitude))*Math.cos(Math.toRadians(b.latitude))*Math.pow(Math.sin(lon/2),2);return 6371000*2*Math.atan2(Math.sqrt(h),Math.sqrt(Math.max(0,1-h)));}
 static Route simulator(Point from,Point to,String reason){double meters=distance(from,to)*1.3;return new Route("SIMULATOR",reason,meters,meters/3.33,List.of(from,to),List.of(),"Local simulator · straight-line approximation, not road navigation",Instant.now().toString());}
 static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
 private final CompletableFuture<byte[]> future=new CompletableFuture<>();private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();private Flow.Subscription subscription;
 public CompletionStage<byte[]> getBody(){return future;}
 public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(1);}
 public void onNext(List<ByteBuffer> buffers){try{for(var buffer:buffers){if(bytes.size()+buffer.remaining()>1_048_576){subscription.cancel();future.completeExceptionally(new IllegalArgumentException("Route response too large"));return;}byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);}subscription.request(1);}catch(Exception e){subscription.cancel();future.completeExceptionally(e);}}
 public void onError(Throwable error){future.completeExceptionally(error);}public void onComplete(){future.complete(bytes.toByteArray());}
 }
}
