package dev.saffron;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.kafka.core.KafkaTemplate;
import java.util.concurrent.TimeUnit;
@Component class Outbox {
 final JdbcTemplate db;final KafkaTemplate<String,String> kafka;
 Outbox(JdbcTemplate d,KafkaTemplate<String,String> k){db=d;kafka=k;}
 @Scheduled(fixedDelay=2000) @Transactional public void publish()throws Exception{
 for(var row:db.queryForList("select * from outbox where published_at is null order by created_at limit 50 for update skip locked")){
 kafka.send("saffron.events",row.get("aggregate_id").toString(),row.get("payload").toString()).get(5,TimeUnit.SECONDS);
 kafka.send("saffron.logs",row.get("id").toString(),row.get("event_type")+" order="+row.get("aggregate_id")).get(5,TimeUnit.SECONDS);
 db.update("update outbox set published_at=now() where id=?",row.get("id"));}
 }
}
