package dev.saffron;
import java.util.*;
import java.security.Principal;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;
@Service public class Marketplace {
 final JdbcTemplate db; final Meals meals; final Tracking tracking; final PaymentOperations payments;final NotificationInbox inbox; Marketplace(JdbcTemplate db,Meals meals,Tracking tracking,PaymentOperations payments,NotificationInbox inbox){this.inbox=inbox;this.payments=payments;this.db=db;this.meals=meals;this.tracking=tracking;}
 @Transactional public Map<String,Object> checkout(String user,String key,Meals.Cart cart){
 if(key==null||!key.matches("[a-zA-Z0-9-]{8,100}"))throw new ResponseStatusException(BAD_REQUEST,"Idempotency-Key required");
 db.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,user+":"+key);
 long item=cart.item();int quantity=cart.quantity();String fingerprint=Meals.fingerprint(cart);
 var old=db.queryForList("select * from orders where customer_name=? and idem=?",user,key);
 if(!old.isEmpty()){if(!old.getFirst().get("fingerprint").equals(fingerprint))throw new ResponseStatusException(CONFLICT,"Key reused with different cart");return old.getFirst();}
 var quote=meals.quote(cart);
 var m=db.queryForMap("select m.* from menu m join restaurants r on r.id=m.restaurant_id where m.id=? and m.available and r.online for share of m,r",item);
 long total=quote.total();UUID id=UUID.randomUUID();
 db.update("insert into orders(id,customer_name,restaurant_id,item_id,quantity,total,state,payment,otp,idem,fingerprint) values(?,?,?,?,?,?,'PLACED','PAID','246810',?,?)",id,user,m.get("restaurant_id"),item,quantity,total,key,fingerprint);
 db.update("update orders set customization=?::jsonb where id=?",tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(quote),id);
 db.update("insert into ledger(order_id,kind,amount) values(?,'SIMULATED_CAPTURE',?)",id,total);event(id,"OrderPlaced");return db.queryForMap("select * from orders where id=?",id);
 }
 void event(UUID id,String type){tracking.record(id);inbox.order(id,type);db.update("insert into outbox(id,aggregate_id,event_type,payload) values(?,?,?,?)",UUID.randomUUID(),id,type,"{\"orderId\":\""+id+"\"}");}
 @Transactional public Map<String,Object> transition(String user,String role,UUID id,String action,String otp){
 var o=db.queryForMap("select o.*,r.owner_name from orders o join restaurants r on r.id=o.restaurant_id where o.id=? for update of o",id);
 boolean allowed=switch(action){case "accept","prepare","ready" -> role.equals("restaurant")&&user.equals(o.get("owner_name"));case "assign" -> role.equals("driver");case "pickup","deliver","release" -> role.equals("driver")&&user.equals(o.get("driver_name"));case "cancel" -> user.equals(o.get("customer_name"))||user.equals(o.get("owner_name"));default -> false;};
 if(!allowed)throw new ResponseStatusException(FORBIDDEN);
 if(!action.equals("cancel"))payments.assertFulfillment(id);
 String next=Workflow.next((String)o.get("state"),action);
 if(action.equals("deliver")&&!Objects.equals(otp,o.get("otp")))throw new ResponseStatusException(BAD_REQUEST,"Invalid delivery proof");
 db.update("update orders set state=?,driver_name=?,version=version+1 where id=?",next,action.equals("assign")?user:action.equals("release")?null:o.get("driver_name"),id);
 if(next.equals("CANCELLED")&&payments.exists(id)){payments.cancel(id);}
 else if(next.equals("CANCELLED")){db.update("insert into ledger(order_id,kind,amount) values(?,'SIMULATED_REFUND',?)",id,-((Number)o.get("total")).longValue());db.update("update orders set payment='REFUNDED' where id=?",id);}
 if(next.equals("DELIVERED")){
 if(o.get("pricing_version")==null)db.update("insert into ledger(order_id,kind,amount) values(?,'RESTAURANT_PAYABLE',?)",id,((Number)o.get("total")).longValue()-3900);
 else {db.update("insert into ledger(order_id,kind,amount) values(?,'RESTAURANT_PAYABLE',?),(?,'TAX_PAYABLE',?),(?,'PLATFORM_DELIVERY_REVENUE',?)",id,((Number)o.get("item_subtotal")).longValue()-((Number)o.get("discount_amount")).longValue(),id,o.get("tax_amount"),id,o.get("delivery_fee"));}
 }
 db.update("insert into audit(actor,action,resource) values(?,?,?)",user,action,id.toString());event(id,"Order"+next);return db.queryForMap("select id,state,payment,total,version from orders where id=?",id);
 }
}
