package dev.saffron;

import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.*;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.*;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.*;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.*;

@Configuration class Security {
 @Bean org.springframework.session.config.SessionRepositoryCustomizer<org.springframework.session.jdbc.JdbcIndexedSessionRepository> sessionQueries(){return repository->repository.setCreateSessionAttributeQuery("INSERT INTO %TABLE_NAME%_ATTRIBUTES (SESSION_PRIMARY_ID, ATTRIBUTE_NAME, ATTRIBUTE_BYTES) VALUES (?, ?, ?) ON CONFLICT (SESSION_PRIMARY_ID, ATTRIBUTE_NAME) DO NOTHING");}
 @Bean SecurityFilterChain chain(HttpSecurity h,ClientRegistrationRepository clients,
 @Value("${app.origin}")String origin,@Value("${spring.security.oauth2.client.provider.keycloak.issuer-uri}")String issuer,
 @Value("${app.allow-insecure-local:false}")boolean local,@Value("${spring.security.oauth2.client.registration.keycloak.redirect-uri}")String callback,@Value("${server.servlet.session.cookie.secure:true}")boolean secureCookie,ProviderSessions providerSessions)throws Exception{
 TransportPolicy.validate(origin,issuer,callback,secureCookie,local);
 org.springframework.security.web.util.matcher.RequestMatcher webhook=request->"POST".equals(request.getMethod())&&"/api/payments/webhooks/stripe".equals(request.getRequestURI());
 var csrfRepository=new org.springframework.security.web.csrf.HttpSessionCsrfTokenRepository();
 var resolver=new DefaultOAuth2AuthorizationRequestResolver(clients,"/oauth2/authorization");
 resolver.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());
 var logout=new OidcClientInitiatedLogoutSuccessHandler(clients);logout.setPostLogoutRedirectUri(origin);
 return h.addFilterAfter(new SessionLifetimeFilter(providerSessions),org.springframework.security.web.authentication.AnonymousAuthenticationFilter.class).csrf(c->c.csrfTokenRepository(csrfRepository).ignoringRequestMatchers(webhook)).authorizeHttpRequests(a->a.dispatcherTypeMatchers(jakarta.servlet.DispatcherType.ASYNC,jakarta.servlet.DispatcherType.ERROR).permitAll().requestMatchers(webhook).permitAll().requestMatchers("/actuator/health","/oauth2/**","/login/**").permitAll().requestMatchers("/api/logs/**").hasRole("LOGS").requestMatchers("/api/ledger").hasRole("ADMIN").anyRequest().authenticated())
 .exceptionHandling(e->e.defaultAuthenticationEntryPointFor((req,res,ex)->res.sendError(401),req->req.getRequestURI().startsWith("/api/")))
 .oauth2Login(o->o.authorizationEndpoint(a->a.authorizationRequestResolver(resolver)).userInfoEndpoint(u->u.userAuthoritiesMapper(authorities->{
 Set<GrantedAuthority> mapped=new HashSet<>();
 for(var authority:authorities)if(authority instanceof org.springframework.security.oauth2.core.oidc.user.OidcUserAuthority oidc){
 Object claim=oidc.getIdToken().getClaims().get("roles");if(claim instanceof Collection<?> roles)for(Object role:roles)if(Set.of("CUSTOMER","RESTAURANT","DRIVER","ADMIN","LOGS").contains(role.toString()))mapped.add(new SimpleGrantedAuthority("ROLE_"+role));}
 return mapped;
 })).successHandler((request,response,authentication)->{csrfRepository.loadDeferredToken(request,response).get();response.sendRedirect(origin);}))
 .oidcLogout(oidc->oidc.backChannel(back->back.logoutHandler((request,response,authentication)->{
 if(!(authentication.getPrincipal() instanceof org.springframework.security.oauth2.client.oidc.authentication.logout.OidcLogoutToken token)){response.setStatus(400);return;}
 try{providerSessions.revoke(token);response.setStatus(200);}catch(IllegalArgumentException e){response.setStatus(400);}
 })))
 .logout(l->l.logoutSuccessHandler(logout).invalidateHttpSession(true).clearAuthentication(true).deleteCookies("SESSION"))
 .sessionManagement(s->s.sessionFixation(f->f.changeSessionId()))
 .headers(c->c.frameOptions(f->f.deny()).contentSecurityPolicy(p->p.policyDirectives("default-src 'none'; frame-ancestors 'none'")))
 .build();
 }
 static boolean has(Authentication a,String role){return a.getAuthorities().stream().anyMatch(x->x.getAuthority().equals("ROLE_"+role));}
 static String role(Authentication a){for(String role:List.of("ADMIN","RESTAURANT","DRIVER","CUSTOMER"))if(has(a,role))return role.toLowerCase();throw new org.springframework.web.server.ResponseStatusException(org.springframework.http.HttpStatus.FORBIDDEN);}
}
@RestController class SessionApi {
 @GetMapping("/api/session") Map<String,Object> session(Authentication a,CsrfToken csrf){return Map.of("subject",a.getName(),"role",Security.role(a),"csrfToken",csrf.getToken(),"csrfHeader",csrf.getHeaderName());}
}
