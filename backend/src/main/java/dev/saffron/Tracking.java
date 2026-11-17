package dev.saffron;
import java.util.*;
import java.time.*;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@Service class Tracking {
 final JdbcTemplate db;Tracking(JdbcTemplate db){this.db=db;}
 void authorize(Authentication a,UUID id){var matches=db.queryForList("select o.customer_name,o.driver_name,r.owner_name from orders o join restaurants r on r.id=o.restaurant_id where o.id=?",id);if(matches.isEmpty())throw new ResponseStatusException(NOT_FOUND);var row=matches.getFirst();String subject=a.getName();if(!(Security.has(a,"ADMIN")||(Security.has(a,"CUSTOMER")&&subject.equals(row.get("customer_name")))||(Security.has(a,"RESTAURANT")&&subject.equals(row.get("owner_name")))||(Security.has(a,"DRIVER")&&subject.equals(row.get("driver_name")))))throw new ResponseStatusException(NOT_FOUND);}
 public List<Map<String,Object>> events(UUID id,long cursor){return db.queryForList("select version,payload from order_tracking_events where order_id=? and version>? order by version limit 100",id,cursor);}
 Map<String,Object> snapshot(UUID id){var row=db.queryForMap("select id,state,payment,total,version,latitude,longitude,location_at,case when state='DELIVERED' then 0 when state='CANCELLED' then null when state='PLACED' then 35 when state='ACCEPTED' then 30 when state='PREPARING' then 25 when state='READY' then 20 when latitude is not null then ceil(ST_Distance(ST_SetSRID(ST_Point(longitude,latitude),4326)::geography,ST_SetSRID(ST_Point(delivery_longitude,delivery_latitude),4326)::geography)*1.3/200.0+3)::int else 15 end eta_minutes,customization::text customization from orders where id=?",id);if(row.get("location_at")!=null)row.put("location_at",((java.sql.Timestamp)row.get("location_at")).toInstant().toString());return row;}
 Map<String,Object> visible(Map<String,Object> snapshot,Authentication authentication){var result=new LinkedHashMap<>(snapshot);if(Security.has(authentication,"RESTAURANT")&&!Security.has(authentication,"ADMIN")){result.remove("latitude");result.remove("longitude");result.remove("location_at");}return result;}
 void record(UUID id){var snapshot=snapshot(id);db.update("insert into order_tracking_events(order_id,version,payload) values(?,?,?)",id,snapshot.get("version"),tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(snapshot));}
 record Location(Double latitude,Double longitude,Instant sampledAt){}
 @Transactional public void locate(Authentication a,UUID id,Location location){authorize(a,id);var row=db.queryForMap("select state,driver_name,location_at from orders where id=? for update",id);
 if(!Security.has(a,"DRIVER")||!a.getName().equals(row.get("driver_name"))||!Set.of("ASSIGNED","PICKED_UP").contains(row.get("state")))throw new ResponseStatusException(FORBIDDEN);
 if(location==null||location.latitude==null||location.longitude==null||!Double.isFinite(location.latitude)||!Double.isFinite(location.longitude)||Math.abs(location.latitude)>90||Math.abs(location.longitude)>180||location.sampledAt==null||location.sampledAt.isAfter(Instant.now().plusSeconds(30))||location.sampledAt.isBefore(Instant.now().minusSeconds(120)))throw new IllegalArgumentException("Invalid or stale GPS sample");
 if(row.get("location_at") instanceof java.sql.Timestamp prior&&location.sampledAt.isBefore(prior.toInstant().plusSeconds(2)))throw new ResponseStatusException(CONFLICT,"GPS samples must advance by at least two seconds");
 db.update("update orders set latitude=?,longitude=?,location_at=?,version=version+1 where id=?",location.latitude,location.longitude,java.sql.Timestamp.from(location.sampledAt),id);record(id);
 }
}
