package com.cleanroute.api.config;

import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.endpoint.EndpointFilter;
import org.springframework.boot.actuate.endpoint.web.ExposableWebEndpoint;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.authority.AuthorityUtils;
import org.springframework.security.web.SecurityFilterChain;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Security for {@code /actuator/**}, separate from the app's JWT chain and checked first.
 * <ul>
 *   <li>{@code /actuator/health} is public (Docker's health check uses it).</li>
 *   <li>Every other actuator endpoint (info, metrics) needs HTTP Basic with
 *       {@code CLEARWAY_METRICS_USER} / {@code CLEARWAY_METRICS_PASSWORD} from .env. There are no
 *       default credentials: when they aren't set, those endpoints are switched off entirely.</li>
 * </ul>
 * App users' JWTs never open these endpoints.
 */
@Configuration
public class ActuatorSecurityConfig {

    private static final Logger log = LoggerFactory.getLogger(ActuatorSecurityConfig.class);

    static final int MIN_PASSWORD_LENGTH = 12;
    private static final String ROLE = "ACTUATOR";

    private final String user;
    private final String password;

    public ActuatorSecurityConfig(@Value("${clearway.metrics.user:}") String user,
                                  @Value("${clearway.metrics.password:}") String password) {
        this.user = user == null ? "" : user.strip();
        this.password = password == null ? "" : password;
        if (this.user.isEmpty() && this.password.isEmpty()) {
            log.info("Actuator: only /actuator/health is on. Set CLEARWAY_METRICS_USER and "
                    + "CLEARWAY_METRICS_PASSWORD to turn on the other endpoints (metrics) behind HTTP Basic.");
        } else if (!credentialsSet()) {
            log.warn("Actuator: CLEARWAY_METRICS_USER and CLEARWAY_METRICS_PASSWORD must both be set, and the "
                    + "password must be at least {} characters; only /actuator/health is on.", MIN_PASSWORD_LENGTH);
        }
    }

    boolean credentialsSet() {
        return !user.isEmpty() && password.length() >= MIN_PASSWORD_LENGTH;
    }

    /** Without credentials, only the health endpoint exists; the rest aren't mapped at all. */
    @Bean
    EndpointFilter<ExposableWebEndpoint> actuatorEndpointsNeedCredentials() {
        return endpoint -> "health".equals(endpoint.getEndpointId().toString()) || credentialsSet();
    }

    @Bean
    @Order(1)
    SecurityFilterChain actuatorFilterChain(HttpSecurity http) throws Exception {
        http
            .securityMatcher("/actuator", "/actuator/**")
            .csrf(AbstractHttpConfigurer::disable)
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .headers(headers -> headers.contentTypeOptions(Customizer.withDefaults()).frameOptions(frame -> frame.deny()))
            .authorizeHttpRequests(auth -> {
                auth.requestMatchers("/actuator/health", "/actuator/health/**").permitAll();
                if (credentialsSet()) {
                    auth.anyRequest().hasRole(ROLE);
                } else {
                    auth.anyRequest().denyAll();
                }
            })
            .authenticationManager(basicCredentials())
            .httpBasic(basic -> basic.realmName("ClearWay actuator").authenticationEntryPoint((req, res, e) -> {
                res.setHeader("WWW-Authenticate", "Basic realm=\"ClearWay actuator\"");
                writeError(res, 401, "Unauthorized", "Authentication required");
            }))
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) -> {
                    res.setHeader("WWW-Authenticate", "Basic realm=\"ClearWay actuator\"");
                    writeError(res, 401, "Unauthorized", "Authentication required");
                })
                .accessDeniedHandler((req, res, e) -> writeError(res, 403, "Forbidden", "Access denied")));
        return http.build();
    }

    /** Checks the one actuator login from .env, in constant time. Used only by this chain. */
    private AuthenticationManager basicCredentials() {
        return authentication -> {
            if (!credentialsSet()) {
                throw new BadCredentialsException("Actuator login is not configured");
            }
            boolean userOk = constantTimeEquals(user, authentication.getName());
            boolean passwordOk = constantTimeEquals(password, String.valueOf(authentication.getCredentials()));
            if (!(userOk && passwordOk)) {
                throw new BadCredentialsException("Bad actuator credentials");
            }
            return UsernamePasswordAuthenticationToken.authenticated(user, null,
                    AuthorityUtils.createAuthorityList("ROLE_" + ROLE));
        };
    }

    private static boolean constantTimeEquals(String expected, String given) {
        return MessageDigest.isEqual(expected.getBytes(StandardCharsets.UTF_8),
                (given == null ? "" : given).getBytes(StandardCharsets.UTF_8));
    }

    private static void writeError(HttpServletResponse res, int status, String error, String message) throws IOException {
        res.setStatus(status);
        res.setContentType(MediaType.APPLICATION_JSON_VALUE);
        res.getWriter().write("{\"status\":" + status + ",\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
