package dev.saffron;
import java.util.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
@RestController @RequestMapping("/api/notification-channels") class ChannelApi {
 private final ChannelOperations operations;
 ChannelApi(ChannelOperations operations){this.operations=operations;}
 private String verified(Authentication a,String field){if(a.getPrincipal() instanceof OidcUser u&&Boolean.TRUE.equals(u.getClaim(field+"_verified")))return u.getClaimAsString(field);return null;}
 @GetMapping Object settings(Authentication a){var result=new HashMap<String,Object>();result.put("preferences",operations.settings(a.getName()));result.put("verifiedEmail",Objects.requireNonNullElse(verified(a,"email"),""));result.put("verifiedPhone",Objects.requireNonNullElse(verified(a,"phone_number"),""));return result;}
 @PostMapping("/{channel}")Object save(Authentication a,@PathVariable String channel,@RequestBody ChannelOperations.Setting input){return operations.save(a.getName(),channel,input,verified(a,"email"),verified(a,"phone_number"));}
 @GetMapping("/deliveries")Object history(Authentication a){return operations.history(a.getName());}
 @GetMapping("/deliveries/{id}/local-message")Object message(Authentication a,@PathVariable UUID id)throws Exception{return operations.localMessage(a.getName(),id);}
 @GetMapping("/local-push")Object push(Authentication a,@RequestParam String device){return operations.localPush(a.getName(),device);}
 @PostMapping("/deliveries/{id}/displayed")Object displayed(Authentication a,@PathVariable UUID id){operations.displayed(a.getName(),id);return Map.of("displayed",true);}
}
