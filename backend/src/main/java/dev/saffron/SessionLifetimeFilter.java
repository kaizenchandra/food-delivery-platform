package dev.saffron;
import java.io.IOException;
import java.time.Instant;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
final class SessionLifetimeFilter extends OncePerRequestFilter {
 private final ProviderSessions providerSessions;
 SessionLifetimeFilter(ProviderSessions providerSessions){this.providerSessions=providerSessions;}
 static boolean fresh(Authentication authentication){return authentication!=null&&authentication.getPrincipal() instanceof OidcUser user&&user.getIdToken().getIssuedAt()!=null&&user.getIdToken().getIssuedAt().plusSeconds(8*3600).isAfter(Instant.now());}
 @Override protected void doFilterInternal(HttpServletRequest request,HttpServletResponse response,FilterChain chain)throws ServletException,IOException {
 var authentication=SecurityContextHolder.getContext().getAuthentication();
 if(authentication!=null&&authentication.getPrincipal() instanceof OidcUser&&(!fresh(authentication)||request.getSession(false)==null||!providerSessions.active(request.getSession(false).getId()))){
  var session=request.getSession(false);if(session!=null)session.invalidate();SecurityContextHolder.clearContext();response.sendError(401);return;
 }
 chain.doFilter(request,response);
 }
}
