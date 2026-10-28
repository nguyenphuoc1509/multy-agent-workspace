package com.workspace.identity.infrastructure;

import com.workspace.shared.published.ProblemJson;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfFilter;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Server-side session cookie + cookie-to-header CSRF (brief section 13). Same-origin only: no CORS.
 * Login is a controller (JSON), so session fixation protection and CSRF rotation are applied there
 * through the SessionAuthenticationStrategy bean below.
 */
@Configuration
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfRepo,
                                            SecurityContextRepository contextRepo) throws Exception {
        http
            .httpBasic(AbstractHttpConfigurer::disable)
            .formLogin(AbstractHttpConfigurer::disable)
            .logout(AbstractHttpConfigurer::disable)
            .securityContext(c -> c.securityContextRepository(contextRepo))
            .csrf(csrf -> csrf
                .csrfTokenRepository(csrfRepo)
                // plain (non-BREACH-masked) handler: the SPA echoes the raw cookie value in the header
                .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
            .addFilterAfter(new CsrfCookieFilter(), CsrfFilter.class)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                // only reachable when springdoc is enabled (tests / explicit env var); 404 otherwise
                .requestMatchers("/v3/api-docs", "/v3/api-docs/**", "/v3/api-docs.yaml").permitAll()
                .anyRequest().authenticated())
            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((req, res, e) ->
                    writeProblem(res, HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED", "Authentication required"))
                .accessDeniedHandler((req, res, e) ->
                    writeProblem(res, HttpStatus.FORBIDDEN, "FORBIDDEN", "CSRF token missing or invalid, or access denied")));
        return http.build();
    }

    @Bean
    CsrfTokenRepository csrfTokenRepository() {
        CookieCsrfTokenRepository repo = CookieCsrfTokenRepository.withHttpOnlyFalse(); // XSRF-TOKEN / X-XSRF-TOKEN
        repo.setCookiePath("/");
        repo.setCookieCustomizer(c -> c.sameSite("Lax")); // Secure flag follows request.isSecure()
        return repo;
    }

    @Bean
    SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    /** Applied by AuthController after a successful login: new session id + new CSRF token. */
    @Bean
    SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfRepo) {
        ChangeSessionIdAuthenticationStrategy fixation = new ChangeSessionIdAuthenticationStrategy();
        fixation.setAlwaysCreateSession(true);
        SessionAuthenticationStrategy csrfRotation = (authentication, request, response) ->
                csrfRepo.saveToken(csrfRepo.generateToken(request), request, response);
        return new CompositeSessionAuthenticationStrategy(List.of(fixation, csrfRotation));
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder(); // bcrypt by default
    }

    /** Defining any UserDetailsService stops Boot from creating a default user with a generated password. */
    @Bean
    UserDetailsService noUserDetailsService() {
        return username -> { throw new UsernameNotFoundException("password login is handled by LoginService"); };
    }

    private static void writeProblem(jakarta.servlet.http.HttpServletResponse res, HttpStatus status, String code,
                                     String detail) throws java.io.IOException {
        res.setStatus(status.value());
        res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        res.setCharacterEncoding(StandardCharsets.UTF_8.name());
        res.getWriter().write(ProblemJson.toJson(status, code, detail));
    }
}
