package dev.saffron;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken;
import static org.junit.jupiter.api.Assertions.*;
class ProviderSessionsTest {
 private OidcLogoutToken token(Instant issued,String id){return OidcLogoutToken.withTokenValue("test-only").claims(c->{c.put("iss","https://iam.example/realm");c.put("aud",List.of("saffron-web"));c.put("iat",issued);c.put("jti",id);c.put("sid","provider-session");c.put("events",Map.of("http://schemas.openid.net/event/backchannel-logout",Map.of()));}).build();}
 @Test void freshProviderTokenIsAccepted(){var now=Instant.now();assertDoesNotThrow(()->ProviderSessions.validateFreshness(token(now,"unique"),now));}
 @Test void staleProviderTokenRejected(){var now=Instant.now();assertThrows(IllegalArgumentException.class,()->ProviderSessions.validateFreshness(token(now.minusSeconds(601),"unique"),now));}
 @Test void futureProviderTokenRejected(){var now=Instant.now();assertThrows(IllegalArgumentException.class,()->ProviderSessions.validateFreshness(token(now.plusSeconds(61),"unique"),now));}
 @Test void emptyReplayIdRejected(){var now=Instant.now();assertThrows(IllegalArgumentException.class,()->ProviderSessions.validateFreshness(token(now," "),now));}
}
