package dev.saffron;
import org.springframework.stereotype.Component;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.event.InteractiveAuthenticationSuccessEvent;
import org.springframework.security.authentication.event.LogoutSuccessEvent;
@Component class IdentityAudit {
 private final JdbcTemplate db;
 IdentityAudit(JdbcTemplate db){this.db=db;}
 @EventListener public void login(InteractiveAuthenticationSuccessEvent event){db.update("insert into audit(actor,action,resource) values(?,?,'identity')",event.getAuthentication().getName(),"OIDC_LOGIN");}
 @EventListener public void logout(LogoutSuccessEvent event){if(event.getAuthentication()!=null)db.update("insert into audit(actor,action,resource) values(?,?,'identity')",event.getAuthentication().getName(),"OIDC_LOGOUT");}
}
