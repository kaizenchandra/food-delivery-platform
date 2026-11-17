package dev.saffron;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@RestController @RequestMapping("/api") class Api {
 @org.springframework.beans.factory.annotation.Value("${app.allow-insecure-local:false}") private boolean legacyLocal;
 final Marketplace service;final JdbcTemplate db;final Meals meals;final Tracking tracking;
 Api(Marketplace s,JdbcTemplate d,Meals m,Tracking t){service=s;db=d;meals=m;tracking=t;}
 @GetMapping("/restaurants") Object restaurants(@RequestParam(defaultValue="")String q,@RequestParam(defaultValue="12.9716")double lat,@RequestParam(defaultValue="77.5946")double lon){if(!Double.isFinite(lat)||!Double.isFinite(lon)||Math.abs(lat)>90||Math.abs(lon)>180)throw new IllegalArgumentException("Invalid coordinates");return db.queryForList("select id,name,cuisine,(select round(avg(rating),2) from order_reviews v where v.restaurant_id=restaurants.id and v.state='PUBLISHED') rating,(select count(*) from order_reviews v where v.restaurant_id=restaurants.id and v.state='PUBLISHED') review_count,round(ST_Distance(location,ST_SetSRID(ST_Point(?,?),4326)::geography)) distance_m from restaurants where online and ST_DWithin(location,ST_SetSRID(ST_Point(?,?),4326)::geography,8000) and name ilike ? order by distance_m",lon,lat,lon,lat,"%"+q+"%");}
 @GetMapping("/restaurants/{id}/menu")Object menu(@PathVariable long id){var items=db.queryForList("select * from menu where restaurant_id=? and available",id);for(var item:items)item.put("groups",meals.groups(((Number)item.get("id")).longValue()));return items;}
 @PostMapping("/quote")Object quote(Authentication a,@RequestBody Meals.Cart c){customer(a);return meals.quote(c);}
 void customer(Authentication a){if(!Security.has(a,"CUSTOMER"))throw new ResponseStatusException(FORBIDDEN);}
 @PostMapping("/checkout") Object checkout(Authentication a,@RequestHeader("Idempotency-Key")String key,@RequestBody Meals.Cart c){customer(a);if(!legacyLocal)throw new ResponseStatusException(GONE,"Use saved-address cart checkout");return service.checkout(a.getName(),key,c);}
 @GetMapping("/orders")Object orders(Authentication a){String u=a.getName();return switch(Security.role(a)){
 case "admin" -> db.queryForList("select id,state,payment,total,version from orders order by created_at desc limit 100");
 case "restaurant" -> db.queryForList("select o.id,o.state,o.total,o.customization::text customization,o.version from orders o join restaurants r on r.id=o.restaurant_id where r.owner_name=? and o.state<>'AWAITING_PAYMENT' order by o.created_at desc limit 100",u);
 case "driver" -> db.queryForList("select id,state,total,version from orders where state='READY' or driver_name=? order by created_at desc limit 100",u);
 default -> db.queryForList("select id,state,payment,total,otp,customization::text customization,version from orders where customer_name=? order by created_at desc limit 100",u);};}
 @GetMapping("/orders/{id}")Object order(Authentication a,@PathVariable UUID id){tracking.authorize(a,id);return tracking.visible(tracking.snapshot(id),a);}
 record Action(String action,String otp){}
 @PostMapping("/orders/{id}/actions")Object action(Authentication a,@PathVariable UUID id,@RequestBody Action action){return service.transition(a.getName(),Security.role(a),id,action.action,action.otp);}
 @GetMapping("/ledger")Object ledger(){return db.queryForList("select * from ledger order by id desc limit 100");}
}
