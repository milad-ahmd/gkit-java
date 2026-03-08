package dev.gkit.auth;

import io.jsonwebtoken.*;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.filter.OncePerRequestFilter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import javax.crypto.SecretKey;

/**
 * JWT-based authentication utilities.
 * Provides Claims, token issuance, and a Spring servlet filter for JWT validation.
 */
public final class Auth {
    private Auth() {}

    /** JWT payload: user ID and roles. */
    public record Claims(String userId, List<String> roles) {
        public boolean hasRole(String role) { return roles \!= null && roles.contains(role); }
        public boolean hasAnyRole(String... required) {
            for (String r : required) { if (hasRole(r)) return true; } return false;
        }
    }

    /** Issues a signed JWT token for the given claims with the specified TTL. */
    public static String issueToken(Claims claims, byte[] secret, Duration ttl) {
        SecretKey key = Keys.hmacShaKeyFor(secret);
        Date now = new Date();
        Date exp = new Date(now.getTime() + ttl.toMillis());
        return Jwts.builder()
                .subject(claims.userId())
                .claim("roles", claims.roles())
                .issuedAt(now)
                .notBefore(now)
                .expiration(exp)
                .signWith(key)
                .compact();
    }

    /** Parses and validates a JWT token, returning Claims on success. */
    @SuppressWarnings("unchecked")
    public static Claims parseToken(String token, byte[] secret) {
        SecretKey key = Keys.hmacShaKeyFor(secret);
        io.jsonwebtoken.Claims jwtClaims = Jwts.parser()
                .verifyWith(key).build()
                .parseSignedClaims(token).getPayload();
        String userId = jwtClaims.getSubject();
        List<String> roles = (List<String>) jwtClaims.getOrDefault("roles", Collections.emptyList());
        return new Claims(userId, roles);
    }

    /** ThreadLocal key for storing Claims in the request context. */
    private static final ThreadLocal<Claims> CLAIMS_HOLDER = new ThreadLocal<>();

    /** Returns the Claims stored in the current request context, or empty. */
    public static Optional<Claims> currentClaims() { return Optional.ofNullable(CLAIMS_HOLDER.get()); }

    /**
     * Spring OncePerRequestFilter that validates the Bearer JWT and stores
     * the parsed Claims in the thread-local context for downstream handlers.
     */
    public static class JwtFilter extends OncePerRequestFilter {
        private final byte[] secret;

        public JwtFilter(String secret) { this.secret = secret.getBytes(StandardCharsets.UTF_8); }
        public JwtFilter(byte[] secret) { this.secret = secret; }

        @Override
        protected void doFilterInternal(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
                throws ServletException, IOException {
            String header = req.getHeader("Authorization");
            String token = null;
            if (header \!= null && header.startsWith("Bearer ")) {
                token = header.substring(7).strip();
            } else {
                Cookie[] cookies = req.getCookies();
                if (cookies \!= null) {
                    for (Cookie c : cookies) { if ("access_token".equals(c.getName())) { token = c.getValue(); break; } }
                }
            }
            if (token == null || token.isEmpty()) {
                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Missing token");
                return;
            }
            try {
                CLAIMS_HOLDER.set(parseToken(token, secret));
                chain.doFilter(req, res);
            } catch (ExpiredJwtException e) {
                res.sendError(HttpServletResponse.SC_FORBIDDEN, "Token expired");
            } catch (JwtException e) {
                res.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
            } finally { CLAIMS_HOLDER.remove(); }
        }
    }
}
