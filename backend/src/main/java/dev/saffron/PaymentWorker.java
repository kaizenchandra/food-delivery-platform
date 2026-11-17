package dev.saffron;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.beans.factory.annotation.Value;
@Component class PaymentWorker {
 private final PaymentGateway gateway;private final PaymentOperations operations;private final boolean enabled;
 PaymentWorker(PaymentGateway gateway,PaymentOperations operations,@Value("${payments.worker-enabled:true}")boolean enabled){this.gateway=gateway;this.operations=operations;this.enabled=enabled;}
 @Scheduled(fixedDelay=1000) public void tick(){if(!enabled||!gateway.enabled())return;
 for(var p:operations.claimPayments()){UUID id=(UUID)p.get("id");try{
 var session=p.get("session_id")==null?gateway.createSession(id,(UUID)p.get("order_id"),PaymentOperations.money(p,"amount")):gateway.session(p.get("session_id").toString());
 if(Boolean.TRUE.equals(p.get("cancel_requested"))&&!session.path("payment_status").asString("").equals("paid")&&session.path("status").asString("").equals("open"))session=gateway.expire(session.path("id").asString(),id);
 if(!operations.session(id,session,"lookup"))throw new IllegalStateException("Provider snapshot quarantined");
 tools.jackson.databind.JsonNode refunds=null;String intent=PaymentOperations.reference(session.path("payment_intent"));
 if(!intent.isBlank()){refunds=gateway.refunds(intent);for(var refund:refunds.path("data"))operations.refund(id,refund);}
 operations.report(id,session,refunds);
 }catch(Exception error){operations.paymentError(id);}}
 for(var r:operations.claimRefunds()){UUID id=(UUID)r.get("id"),payment=(UUID)r.get("payment_id");try{var refund=r.get("provider_id")==null?gateway.refund(id,payment,r.get("intent_id").toString(),PaymentOperations.money(r,"amount")):gateway.refund(r.get("provider_id").toString());operations.refund(payment,refund);}catch(Exception error){operations.refundError(id);}}
 }
}
