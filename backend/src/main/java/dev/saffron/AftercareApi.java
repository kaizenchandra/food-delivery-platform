package dev.saffron;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.web.server.ResponseStatusException;
import static org.springframework.http.HttpStatus.*;

@RestController @RequestMapping("/api") class AftercareApi {
 private final Aftercare service;private final JdbcTemplate db;
 AftercareApi(Aftercare service,JdbcTemplate db){this.service=service;this.db=db;}
 private boolean admin(Authentication a){return Security.has(a,"ADMIN");}
 private void customer(Authentication a){if(!Security.has(a,"CUSTOMER"))throw new ResponseStatusException(FORBIDDEN);}
 private void operator(Authentication a){if(!admin(a))throw new ResponseStatusException(FORBIDDEN);}
 private void supportRole(Authentication a){if(!admin(a)&&!Security.has(a,"CUSTOMER"))throw new ResponseStatusException(FORBIDDEN);}
 @PostMapping("/orders/{order}/support")Object create(Authentication a,@PathVariable UUID order,@RequestHeader("Idempotency-Key")String key,@RequestBody Aftercare.Ticket body){customer(a);return service.create(a.getName(),order,key,body);}
 @GetMapping("/support")Object tickets(Authentication a){supportRole(a);return db.queryForList("select t.id,t.order_id,t.category,t.requested_refund,t.status,t.escalation_state,t.response_due_at,t.version,t.created_at,t.updated_at,t.refund_id,r.state refund_state,r.amount refund_amount from support_tickets t left join refunds r on r.id=t.refund_id where (? or t.customer_subject=?) order by case t.escalation_state when 'REQUESTED' then 0 when 'ACKNOWLEDGED' then 1 else 2 end,t.updated_at desc limit 100",admin(a),a.getName());}
 @GetMapping("/support/{id}")Object ticket(Authentication a,@PathVariable UUID id){supportRole(a);var rows=db.queryForList("select t.id,t.order_id,t.category,t.requested_refund,t.status,t.escalation_state,t.response_due_at,t.version,t.escalation_reason,t.escalated_at,t.acknowledgement,t.acknowledged_at,t.refund_id,r.state refund_state from support_tickets t left join refunds r on r.id=t.refund_id where t.id=? and (? or t.customer_subject=?)",id,admin(a),a.getName());if(rows.isEmpty())throw new ResponseStatusException(NOT_FOUND);var result=rows.getFirst();result.put("messages",db.queryForList("select id,author_role,body,created_at from support_messages where ticket_id=? order by created_at,id limit 200",id));return result;}
 record Reply(long version,String message){}
 @PostMapping("/support/{id}/messages")Object reply(Authentication a,@PathVariable UUID id,@RequestBody Reply body){supportRole(a);return service.reply(a.getName(),admin(a),id,body.version,body.message);}
 record Decision(long version,String decision,String message){}
 @PostMapping("/support/{id}/decision")Object decide(Authentication a,@PathVariable UUID id,@RequestBody Decision body){operator(a);return service.decide(a.getName(),id,body.version,body.decision,body.message);}
 @PostMapping("/orders/{order}/review")Object review(Authentication a,@PathVariable UUID order,@RequestBody Aftercare.Review body){customer(a);return service.review(a.getName(),order,body);}
 @GetMapping("/orders/{order}/review")Object ownReview(Authentication a,@PathVariable UUID order){customer(a);if(db.queryForObject("select count(*) from orders where id=? and customer_name=?",Integer.class,order,a.getName())==0)throw new ResponseStatusException(NOT_FOUND);return db.queryForList("select id,rating,body,state,version from order_reviews where order_id=?",order);}
 @GetMapping("/restaurants/{id}/reviews")Object reviews(@PathVariable long id){return db.queryForList("select id,rating,body,created_at from order_reviews where restaurant_id=? and state='PUBLISHED' order by created_at desc limit 50",id);}
 @GetMapping("/admin/reviews")Object moderation(Authentication a){operator(a);return db.queryForList("select id,order_id,restaurant_id,rating,body,state,version,created_at from order_reviews order by created_at desc limit 100");}
 record Moderation(long version,boolean hidden,String reason){}
 @PostMapping("/admin/reviews/{id}/moderation")Object moderate(Authentication a,@PathVariable UUID id,@RequestBody Moderation body){operator(a);return service.moderate(a.getName(),id,body.version,body.hidden,body.reason);}
 record Escalation(long version,String reason){}
 @PostMapping("/support/{id}/escalate")Object escalate(Authentication a,@PathVariable UUID id,@RequestBody Escalation body){supportRole(a);return service.escalate(a.getName(),admin(a),id,body.version,body.reason,false);}
 @PostMapping("/support/{id}/acknowledge")Object acknowledge(Authentication a,@PathVariable UUID id,@RequestBody Escalation body){operator(a);return service.acknowledge(a.getName(),id,body.version,body.reason);}
 record ReviewEdit(long version,int rating,String body){}
 @PostMapping("/orders/{order}/review/edit")Object editReview(Authentication a,@PathVariable UUID order,@RequestBody ReviewEdit body){customer(a);return service.editReview(a.getName(),order,body.version,new Aftercare.Review(body.rating,body.body));}
 @GetMapping("/orders/{order}/review/history")Object history(Authentication a,@PathVariable UUID order){customer(a);if(db.queryForObject("select count(*) from orders where id=? and customer_name=?",Integer.class,order,a.getName())==0)throw new ResponseStatusException(NOT_FOUND);return db.queryForList("select h.version,h.rating,h.body,h.state,h.action,h.created_at from review_revisions h join order_reviews r on r.id=h.review_id where r.order_id=? order by h.version desc limit 100",order);}
 @GetMapping("/admin/reviews/{id}/history")Object adminHistory(Authentication a,@PathVariable UUID id){operator(a);return db.queryForList("select version,rating,body,state,action,created_at from review_revisions where review_id=? order by version desc limit 100",id);}

}
