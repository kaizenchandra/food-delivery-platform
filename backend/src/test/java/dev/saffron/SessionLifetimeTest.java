package dev.saffron;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import static org.junit.jupiter.api.Assertions.*;
class SessionLifetimeTest {
 @Test void activeSessionCannotOutliveAbsoluteLimit(){var issued=Instant.now().minusSeconds(9*3600);var token=new OidcIdToken("test",issued,issued.plusSeconds(300),Map.of("sub","test-subject"));var user=new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")),token);assertFalse(SessionLifetimeFilter.fresh(new TestingAuthenticationToken(user,null,"ROLE_CUSTOMER")));}
 @Test void recentLoginIsFresh(){var issued=Instant.now();var token=new OidcIdToken("test",issued,issued.plusSeconds(300),Map.of("sub","test-subject"));var user=new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_CUSTOMER")),token);assertTrue(SessionLifetimeFilter.fresh(new TestingAuthenticationToken(user,null,"ROLE_CUSTOMER")));}
}
