package com.helix.console.system.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

/**
 * JWT token provider.
 *
 * <p>Issues and verifies stateless tokens. The payload carries only userId and
 * account; roles/permissions are re-queried from the database when needed, so
 * old tokens do not keep stale permissions after permission changes.</p>
 */
@Slf4j
@Component
public class JwtTokenProvider {

    private static final String CLAIM_USER_ID = "uid";
    private static final String CLAIM_ACCOUNT = "acc";
    private static final String CLAIM_ORGAN_ID = "org";

    private final SecretKey key;
    private final long expireMillis;

    public JwtTokenProvider(
            @Value("${console.jwt.secret:x6-default-secret-key-please-override-in-production-32bytes}") String secret,
            @Value("${console.jwt.expire-hours:12}") long expireHours) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireMillis = expireHours * 3600_000L;
    }

    /** Issue a token */
    public String create(Long userId, String account, Long organId) {
        Map<String, Object> claims = new HashMap<>();
        claims.put(CLAIM_USER_ID, userId);
        claims.put(CLAIM_ACCOUNT, account);
        claims.put(CLAIM_ORGAN_ID, organId);
        Date now = new Date();
        return Jwts.builder()
                .setClaims(claims)
                .setSubject(String.valueOf(userId))
                .setIssuedAt(now)
                .setExpiration(new Date(now.getTime() + expireMillis))
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /** Parse a token; returns null on failure */
    public LoginUser parse(String token) {
        if (token == null || token.isEmpty()) {
            return null;
        }
        try {
            Claims claims = Jwts.parserBuilder().setSigningKey(key).build()
                    .parseClaimsJws(token).getBody();
            LoginUser user = new LoginUser();
            Object uid = claims.get(CLAIM_USER_ID);
            user.setUserId(uid == null ? null : Long.valueOf(String.valueOf(uid)));
            user.setAccount((String) claims.get(CLAIM_ACCOUNT));
            Object org = claims.get(CLAIM_ORGAN_ID);
            user.setOrganId(org == null ? null : Long.valueOf(String.valueOf(org)));
            return user;
        } catch (Exception e) {
            log.debug("Token parsing failed: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract the token from the Authorization header; supports both "Bearer xxx" and a bare token.
     */
    public String resolveToken(String authorizationHeader) {
        if (authorizationHeader == null || authorizationHeader.isEmpty()) {
            return null;
        }
        if (authorizationHeader.regionMatches(true, 0, "Bearer ", 0, 7)) {
            return authorizationHeader.substring(7).trim();
        }
        return authorizationHeader.trim();
    }
}
