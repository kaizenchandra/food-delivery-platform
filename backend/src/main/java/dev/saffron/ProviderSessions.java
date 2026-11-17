package dev.saffron;

import java.util.*;
import java.time.*;
import java.sql.Timestamp;
import org.springframework.stereotype.Service;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.oauth2.client.oidc.session.*;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;

@Service public class ProviderSessions implements OidcSessionRegistry {
 private final JdbcTemplate db;
 private final org.springframework.session.SessionRepository<?> sessions;
 private final String clientId;
 ProviderSessions(JdbcTemplate db,org.springframework.session.SessionRepository<?> sessions,ClientRegistrationRepository clients){this.db=db;this.sessions=sessions;this.clientId=clients.findByRegistrationId("keycloak").getClientId();}
 private void lock(String issuer){db.queryForObject("select pg_advisory_xact_lock(hashtextextended(?,0))",Object.class,issuer+":"+clientId+":oidc");}
 @Override @Transactional public void saveSessionInformation(OidcSessionInformation info){
 var user=info.getPrincipal();String issuer=user.getIssuer().toExternalForm();lock(issuer);
 db.update("insert into oidc_provider_sessions(client_session_id,issuer,client_id,subject,provider_sid,authenticated_at) values(?,?,?,?,?,?) on conflict(client_session_id) do nothing",info.getSessionId(),issuer,clientId,user.getSubject(),user.getClaimAsString("sid"),Timestamp.from(user.getIdToken().getIssuedAt()));
 }
 public boolean active(String sessionId){return Boolean.TRUE.equals(db.queryForObject("select exists(select 1 from oidc_provider_sessions p where p.client_session_id=? and p.revoked_at is null and not exists(select 1 from oidc_logout_receipts r where r.issuer=p.issuer and r.client_id=p.client_id and ((r.provider_sid is not null and r.provider_sid=p.provider_sid) or (r.provider_sid is null and r.subject=p.subject and p.authenticated_at<=r.issued_at))))",Boolean.class,sessionId));}
 // Authentication already occurred in Spring's back-channel filter before this method is called.
 @Transactional public int revoke(OidcLogoutToken token){
 validateFreshness(token,Instant.now());String issuer=token.getIssuer().toExternalForm();lock(issuer);
 int inserted=db.update("insert into oidc_logout_receipts(issuer,client_id,jti,subject,provider_sid,issued_at) values(?,?,?,?,?,?) on conflict do nothing",issuer,clientId,token.getId(),token.getSubject(),token.getSessionId(),Timestamp.from(token.getIssuedAt()));
 if(inserted==0)return 0; // Successful, idempotent receipt of a provider retry.
 String predicate=token.getSessionId()!=null?"provider_sid=?":"subject=? and authenticated_at<=?";
 List<Object> args=new ArrayList<>(List.of(issuer,clientId,token.getSessionId()!=null?token.getSessionId():token.getSubject()));if(token.getSessionId()==null)args.add(Timestamp.from(token.getIssuedAt()));
 var matches=db.queryForList("select client_session_id from oidc_provider_sessions where issuer=? and client_id=? and "+predicate,args.toArray());
 db.update("update oidc_provider_sessions set revoked_at=coalesce(revoked_at,now()) where issuer=? and client_id=? and "+predicate,args.toArray());
 for(var row:matches)db.update("delete from spring_session where session_id=?",row.get("client_session_id"));
 db.update("insert into audit(actor,action,resource) values(?,'OIDC_PROVIDER_REVOKED',?)",token.getSubject()==null?"provider":token.getSubject(),"sessions="+matches.size());
 return matches.size();
 }
 static void validateFreshness(OidcLogoutToken token,Instant now){
 if(token.getIssuedAt()==null||token.getIssuedAt().isBefore(now.minusSeconds(600))||token.getIssuedAt().isAfter(now.plusSeconds(60))||token.getId()==null||token.getId().isBlank()||token.getId().length()>200||(token.getSubject()!=null&&(token.getSubject().isBlank()||token.getSubject().length()>255))||(token.getSessionId()!=null&&(token.getSessionId().isBlank()||token.getSessionId().length()>255)))throw new IllegalArgumentException("Invalid logout token freshness or identifier");
 }
 @Override @Transactional public OidcSessionInformation removeSessionInformation(String id){var info=information(id);db.update("update oidc_provider_sessions set revoked_at=coalesce(revoked_at,now()) where client_session_id=?",id);return info;}
 private OidcSessionInformation information(String id){var session=sessions.findById(id);if(session!=null&&session.getAttribute("SPRING_SECURITY_CONTEXT") instanceof SecurityContext context&&context.getAuthentication().getPrincipal() instanceof OidcUser user)return new OidcSessionInformation(id,Map.of(),user);return null;}
 @Override @Transactional public Iterable<OidcSessionInformation> removeSessionInformation(OidcLogoutToken token){revoke(token);return List.of();} // The configured handler invalidates the shared store directly, without loopback HTTP.
 @Scheduled(fixedDelay=3600000) @Transactional public void cleanup(){db.update("delete from oidc_provider_sessions where authenticated_at<now()-interval '24 hours'");db.update("delete from oidc_logout_receipts where received_at<now()-interval '24 hours'");}
}
