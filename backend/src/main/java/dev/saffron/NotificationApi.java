package dev.saffron;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
@RestController @RequestMapping("/api/notifications") class NotificationApi {
 private final NotificationInbox inbox;
 NotificationApi(NotificationInbox inbox){this.inbox=inbox;}
 @GetMapping Object list(Authentication a,@RequestParam(defaultValue="9223372036854775807")long before){return inbox.list(a.getName(),Security.has(a,"ADMIN"),before);}
 @PostMapping("/{id}/read")Object read(Authentication a,@PathVariable UUID id){inbox.read(a.getName(),Security.has(a,"ADMIN"),id);return Map.of("read",true);}
}
