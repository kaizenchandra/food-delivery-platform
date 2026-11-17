package dev.saffron;
import java.util.*;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestClient;
import org.springframework.beans.factory.annotation.Value;
@RestController @RequestMapping("/api/logs") class Logs {
 final KafkaTemplate<String,String> kafka;final RestClient search;
 Logs(KafkaTemplate<String,String> k,@Value("${SEARCH_URL:http://localhost:9208}")String url){kafka=k;search=RestClient.create(url);}
 @PostMapping Object ingest(@RequestBody Map<String,String> body)throws Exception{
 String message=body.getOrDefault("message","");if(message.length()>8192)throw new IllegalArgumentException("Log exceeds 8 KiB");
 String id=UUID.randomUUID().toString();kafka.send("saffron.logs",id,message.replaceAll("(?i)(password|token|otp)=\\S+","$1=[REDACTED]")).get(5,TimeUnit.SECONDS);
 return Map.of("id",id,"acknowledgment","Kafka accepted; indexing asynchronous");}
 @KafkaListener(topics="saffron.logs",groupId="saffron-indexer") void index(org.apache.kafka.clients.consumer.ConsumerRecord<String,String> r){
 search.put().uri("/saffron-local-logs/_doc/{id}",r.key()).body(Map.of("message",r.value(),"service","marketplace","environment","local","timestamp",java.time.Instant.now().toString())).retrieve().toBodilessEntity();}
 @GetMapping Object find(@RequestParam(defaultValue="")String q){if(q.length()>200)throw new IllegalArgumentException("Query too long");Object query=q.isBlank()?Map.of("match_all",Map.of()):Map.of("match",Map.of("message",q));return search.post().uri("/saffron-local-logs/_search").body(Map.of("size",50,"timeout","2s","query",query)).retrieve().body(Map.class);}
}
