package dev.saffron;
import java.util.*;
import java.time.Instant;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.server.ResponseStatusException;
import jakarta.servlet.http.HttpServletRequest;
import tools.jackson.databind.json.JsonMapper;
@RestController @RequestMapping("/api") class PaymentApi {
 private final JdbcTemplate db;private final PaymentOperations operations;private final PaymentGateway gateway;
 PaymentApi(JdbcTemplate db,PaymentOperations operations,PaymentGateway gateway){this.db=db;this.operations=operations;this.gateway=gateway;}
 private void admin(Authentication auth){if(!Security.has(auth,"ADMIN"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);}
 private Map<String,Object> authorized(Authentication auth,UUID order){var rows=db.queryForList("select p.id from payments p join orders o on o.id=p.order_id where o.id=? and (? or o.customer_name=?)",order,Security.has(auth,"ADMIN"),Security.has(auth,"CUSTOMER")?auth.getName():"");if(rows.isEmpty())throw new ResponseStatusException(HttpStatus.NOT_FOUND);UUID id=(UUID)rows.getFirst().get("id");var payment=db.queryForMap("select id,order_id,provider,amount,currency,state,session_id,intent_id,checkout_url,captured_amount,refunded_amount,cancel_requested,last_error,last_reconciled from payments where id=?",id);payment.put("refunds",db.queryForList("select id,amount,state,reason,provider_id,created_at from refunds where payment_id=? order by created_at desc",id));return payment;}
 @GetMapping("/orders/{id}/payment")Object payment(Authentication auth,@PathVariable UUID id){return authorized(auth,id);}
 @PostMapping("/orders/{id}/payment/retry")Object retry(Authentication auth,@PathVariable UUID id){if(!Security.has(auth,"CUSTOMER"))throw new ResponseStatusException(HttpStatus.FORBIDDEN);operations.retry(id,auth.getName());return Map.of("scheduled",true);}
 record Refund(long amount,String reason){}
 @PostMapping("/orders/{id}/refunds")Object refund(Authentication auth,@PathVariable UUID id,@RequestHeader("Idempotency-Key")String key,@RequestBody Refund body){admin(auth);return operations.requestRefund(id,key,body.amount,body.reason,auth.getName());}
 @GetMapping("/admin/payments")Object payments(Authentication auth){admin(auth);return db.queryForList("select p.id,p.order_id,p.provider,p.amount,p.currency,p.state,p.captured_amount,p.refunded_amount,p.last_error,p.last_reconciled,o.state fulfillment_state,o.payment from payments p join orders o on o.id=p.order_id order by p.created_at desc limit 100");}
 @GetMapping("/admin/payments/issues")Object issues(Authentication auth){admin(auth);return db.queryForList("select * from payment_issues order by last_seen desc limit 100");}
 @GetMapping("/admin/payments/reconciliations")Object reconciliations(Authentication auth){admin(auth);return db.queryForList("select * from payment_reconciliations order by checked_at desc limit 100");}
 @PostMapping("/admin/payments/reconcile")Object reconcile(Authentication auth){admin(auth);return Map.of("scheduled",operations.reconcileNow(auth.getName()));}
 @PostMapping("/payments/webhooks/stripe")Object webhook(HttpServletRequest request,@RequestHeader(value="Stripe-Signature",required=false)String signature)throws java.io.IOException {
 if(request.getContentLengthLong()>65536)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);byte[] body=request.getInputStream().readNBytes(65537);if(body.length>65536)throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE);
 if(!WebhookSignature.verify(body,signature,gateway.webhookSecrets,Instant.now()))throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Invalid webhook signature or timestamp");
 tools.jackson.databind.JsonNode event;try{event=JsonMapper.builder().build().readTree(body);}catch(Exception error){throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Malformed event JSON");}
 return operations.webhook(gateway.provider(),event,WebhookSignature.digest(body));
 }
}
