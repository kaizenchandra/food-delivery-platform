package dev.saffron;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import jakarta.servlet.http.HttpSession;
import jakarta.annotation.PreDestroy;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import org.springframework.security.core.Authentication;
import org.springframework.http.MediaType;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@RestController class TrackingStream {
 final org.springframework.session.SessionRepository<?> sessions;
 final ProviderSessions providerSessions;
 final Tracking tracking;final ConcurrentMap<UUID,Client> clients=new ConcurrentHashMap<>();
 final ScheduledExecutorService scheduler=Executors.newSingleThreadScheduledExecutor();
 final ExecutorService sends=Executors.newFixedThreadPool(8);
 record Client(UUID order,Authentication auth,HttpSession session,SseEmitter emitter,long[] cursor,AtomicBoolean inFlight,long expires){}
 TrackingStream(Tracking t,org.springframework.session.SessionRepository<?> sessions,ProviderSessions providerSessions){this.providerSessions=providerSessions;this.sessions=sessions;tracking=t;scheduler.scheduleWithFixedDelay(this::tick,0,1,TimeUnit.SECONDS);}
 @GetMapping(value="/api/orders/{id}/events",produces=MediaType.TEXT_EVENT_STREAM_VALUE)
 synchronized SseEmitter events(Authentication a,HttpSession session,jakarta.servlet.http.HttpServletResponse response,@PathVariable UUID id,@RequestHeader(value="Last-Event-ID",required=false)String header,@RequestParam(defaultValue="-1")long after){
 response.setHeader("Cache-Control","no-cache, no-store, no-transform");response.setHeader("X-Accel-Buffering","no");
 tracking.authorize(a,id);if(header!=null){try{after=Long.parseLong(header);}catch(NumberFormatException e){throw new IllegalArgumentException("Invalid cursor");}}
 if(after< -1)throw new IllegalArgumentException("Invalid cursor");
 if(clients.size()>=200||clients.values().stream().filter(c->c.auth.getName().equals(a.getName())).count()>=5)throw new ResponseStatusException(TOO_MANY_REQUESTS);
 var emitter=new SseEmitter(120_000L);var key=UUID.randomUUID();var c=new Client(id,a,session,emitter,new long[]{after},new AtomicBoolean(),System.currentTimeMillis()+115_000);clients.put(key,c);
 emitter.onCompletion(()->clients.remove(key));emitter.onTimeout(()->clients.remove(key));emitter.onError(e->clients.remove(key));return emitter;
 }
 void tick(){clients.forEach((key,c)->{if(c.inFlight.compareAndSet(false,true))sends.execute(()->{try{
 if(System.currentTimeMillis()>c.expires){c.emitter.complete();clients.remove(key);return;}
 if(!SessionLifetimeFilter.fresh(c.auth)||!providerSessions.active(c.session.getId())||sessions.findById(c.session.getId())==null){c.emitter.send(SseEmitter.event().name("revoked").data(Map.of("reason","SESSION_REVOKED")));c.emitter.complete();clients.remove(key);return;}
 tracking.authorize(c.auth,c.order);
 var latest=tracking.snapshot(c.order);long version=((Number)latest.get("version")).longValue();
 if(c.cursor[0]<0||c.cursor[0]>version){c.emitter.send(SseEmitter.event().name("snapshot").id(Long.toString(version)).data(tracking.visible(latest,c.auth)));c.cursor[0]=version;}
 else {
 var events=tracking.events(c.order,c.cursor[0]);
 if((events.isEmpty()&&c.cursor[0]<version)||(!events.isEmpty()&&((Number)events.getFirst().get("version")).longValue()!=c.cursor[0]+1)){c.emitter.send(SseEmitter.event().name("snapshot").id(Long.toString(version)).data(tracking.visible(latest,c.auth)));c.cursor[0]=version;}
 else for(var event:events){long v=((Number)event.get("version")).longValue();c.emitter.send(SseEmitter.event().name("update").id(Long.toString(v)).data(tracking.visible(tools.jackson.databind.json.JsonMapper.builder().build().readValue(event.get("payload").toString(),Map.class),c.auth),MediaType.APPLICATION_JSON));c.cursor[0]=v;}
 }
 c.emitter.send(SseEmitter.event().comment("heartbeat"));
 }catch(Exception e){org.slf4j.LoggerFactory.getLogger(TrackingStream.class).warn("Tracking stream closed: {}",e.toString());clients.remove(key);c.emitter.complete();}finally{c.inFlight.set(false);}});});}
 @PostMapping("/api/orders/{id}/location")void location(Authentication a,@PathVariable UUID id,@RequestBody Tracking.Location l){tracking.locate(a,id,l);}
 @PreDestroy void stop(){scheduler.shutdownNow();clients.values().forEach(c->c.emitter.complete());sends.shutdownNow();}
}
