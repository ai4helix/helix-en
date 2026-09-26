package com.helix.console.system.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.helix.console.common.Result;
import com.helix.console.common.ResultCode;
import com.helix.console.system.service.AuthService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * Authentication interceptor.
 *
 * <p>Verifies the JWT in the Authorization header and, on success, puts the
 * user into {@link UserContext}; the ThreadLocal is cleaned up when the request
 * ends (including on exceptions).</p>
 *
 * <p>On authentication failure it <b>writes the JSON response directly</b>
 * ({@code {code:20001}}, HTTP 200) instead of throwing into the global exception
 * resolver -- the resolver chain may be broken (e.g. failed rebind after a nacos
 * config refresh), which would turn a BizException thrown here into a raw
 * Tomcat 500 (the frontend gets no JSON, cannot redirect to the login page, and
 * ERROR logs pile up). The interceptor is the first gate of a request and must
 * carry its own response capability, not depend on downstream resolvers being
 * healthy.</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AuthInterceptor implements HandlerInterceptor {

    private final JwtTokenProvider tokenProvider;
    private final AuthService authService;
    private final ObjectMapper objectMapper;

    /** Whether authentication is enabled; can be turned off for local debugging */
    @Value("${console.auth.enabled:true}")
    private boolean authEnabled;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!authEnabled) {
            return true;
        }
        // Let CORS preflight requests pass directly
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }

        String token = tokenProvider.resolveToken(request.getHeader("Authorization"));
        LoginUser parsed = tokenProvider.parse(token);
        if (parsed == null || parsed.getUserId() == null) {
            return writeUnauthorized(request.getRequestURI(), response);
        }
        // Re-query roles and permissions so permission changes take effect immediately
        LoginUser full = authService.loadLoginUser(parsed.getUserId());
        UserContext.set(full);
        return true;
    }

    /** Not logged in: HTTP 200 + business code 20001 (the frontend request.ts clears the token and redirects to the login page accordingly) */
    private boolean writeUnauthorized(String uri, HttpServletResponse response) {
        try {
            response.setStatus(HttpServletResponse.SC_OK);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.setCharacterEncoding("UTF-8");
            objectMapper.writeValue(response.getWriter(),
                    Result.fail(ResultCode.UNAUTHORIZED, "Not logged in or session expired"));
        } catch (IOException e) {
            log.error("Failed to write unauthorized response uri={}", uri, e);
        }
        return false;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response,
                                Object handler, Exception ex) {
        UserContext.clear();
    }
}
