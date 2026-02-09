package io.labs64.paymentgateway.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.web.filter.OncePerRequestFilter;

@Configuration
@Profile("local")
public class LocalSecurityConfig {

	@Bean
	@Order(1)
	public SecurityFilterChain localSecurityFilterChain(HttpSecurity http) throws Exception {
		http.csrf(csrf -> csrf.disable());
		http.authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
		http.addFilterAfter(new LocalMockJwtFilter(), BasicAuthenticationFilter.class);
		return http.build();
	}

	/**
	 * Injects a mock JWT into the security context so TenantResolver works without a real issuer.
	 */
	private static final class LocalMockJwtFilter extends OncePerRequestFilter {
		@Override
		protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
				FilterChain filterChain) throws ServletException, IOException {
			Jwt jwt = Jwt.withTokenValue("local-mock")
					.header("alg", "none")
					.claim("sub", "local")
					.claim("tenant_id", "local-tenant")
					.claim("tenantId", "local-tenant")
					.issuedAt(Instant.now())
					.expiresAt(Instant.now().plusSeconds(3600))
					.build();
			JwtAuthenticationToken auth = new JwtAuthenticationToken(jwt, AuthorityUtils.NO_AUTHORITIES);
			SecurityContextHolder.getContext().setAuthentication(auth);
			try {
				filterChain.doFilter(request, response);
			} finally {
				SecurityContextHolder.clearContext();
			}
		}
	}
}
