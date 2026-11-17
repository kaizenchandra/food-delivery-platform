package dev.saffron;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.server.ResponseStatusException;
@Component class SupportEscalationWorker {
 private final Aftercare aftercare;
 SupportEscalationWorker(Aftercare aftercare){this.aftercare=aftercare;}
 @Scheduled(fixedDelay=5000) public void tick(){for(var t:aftercare.overdue())try{aftercare.escalate("system",true,(UUID)t.get("id"),((Number)t.get("version")).longValue(),"Unresolved support deadline exceeded",true);}catch(ResponseStatusException conflict){if(conflict.getStatusCode().value()!=409)throw conflict;}}
}
