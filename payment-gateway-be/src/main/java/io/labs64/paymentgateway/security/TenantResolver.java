package io.labs64.paymentgateway.security;

import java.util.Map;
import java.util.Optional;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

@Component
public class TenantResolver {
	private static final String TENANT_ID = "tenant_id";
	private static final String TENANT_ID_CAMEL = "tenantId";

	public String resolveTenantId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication instanceof JwtAuthenticationToken jwtAuth) {
			Map<String, Object> claims = jwtAuth.getToken().getClaims();
			return Optional.ofNullable(claims.get(TENANT_ID))
					.or(() -> Optional.ofNullable(claims.get(TENANT_ID_CAMEL)))
					.map(Object::toString)
					.orElseThrow(() -> new IllegalStateException("Missing tenant claim"));
		}
		throw new IllegalStateException("Unsupported authentication type");
	}
}
