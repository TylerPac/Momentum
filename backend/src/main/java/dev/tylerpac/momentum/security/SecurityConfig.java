package dev.tylerpac.momentum.security;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserRequest;
import org.springframework.security.oauth2.client.oidc.userinfo.OidcUserService;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestCustomizers;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.authentication.logout.LogoutSuccessHandler;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRequestHandler;
import org.springframework.security.web.csrf.XorCsrfTokenRequestAttributeHandler;
import org.springframework.util.StringUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

// Sign-in is delegated to the shared login service. The backend completes the OpenID Connect flow itself and keeps
// the browser signed in with an httpOnly session cookie, so tokens never reach JavaScript.
@Configuration
@EnableWebSecurity
public class SecurityConfig {

        public static final String REGISTRATION_ID = "shared";

        private final String issuer;
        private final String internalUrl;
        private final String clientSecret;
        private final String publicUrl;

        public SecurityConfig(
                @Value("${momentum.login.issuer}") String issuer,
                @Value("${momentum.login.internal-url}") String internalUrl,
                @Value("${momentum.login.client-secret}") String clientSecret,
                @Value("${momentum.public-url}") String publicUrl) {
                this.issuer = issuer;
                this.internalUrl = internalUrl;
                this.clientSecret = clientSecret;
                this.publicUrl = publicUrl;
        }

        // Endpoints are set explicitly instead of discovered, so Momentum still starts when the login service is down.
        @Bean
        public ClientRegistrationRepository clientRegistrationRepository() {
                return new InMemoryClientRegistrationRepository(ClientRegistration.withRegistrationId(REGISTRATION_ID)
                        .clientName("Shared login")
                        .clientId("momentum")
                        .clientSecret(clientSecret)
                        .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                        .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                        .redirectUri(publicUrl + "/login/oauth2/code/" + REGISTRATION_ID)
                        .scope("openid", "profile")
                        .issuerUri(issuer)
                        .authorizationUri(issuer + "/oauth2/authorize")
                        .tokenUri(internalUrl + "/oauth2/token")
                        .jwkSetUri(internalUrl + "/oauth2/jwks")
                        .userNameAttributeName("sub")
                        .build());
        }

        @Bean
        public OidcUserService oidcUserService(LocalAccountService accounts) {
                return new OidcUserService() {
                        @Override
                        public OidcUser loadUser(OidcUserRequest request) {
                                OidcUser user = super.loadUser(request);
                                accounts.ensureAccount(user.getSubject(), user.getFullName());
                                return user;
                        }
                };
        }

        @Bean
        public SecurityFilterChain securityFilterChain(HttpSecurity http, ClientRegistrationRepository registrations,
                                                       OidcUserService oidcUserService) throws Exception {
                DefaultOAuth2AuthorizationRequestResolver authorizationRequests = new DefaultOAuth2AuthorizationRequestResolver(
                        registrations, OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
                authorizationRequests.setAuthorizationRequestCustomizer(OAuth2AuthorizationRequestCustomizers.withPkce());

                return http
                        .cors(Customizer.withDefaults())
                        // A session cookie is sent automatically, so state-changing requests need a CSRF token
                        .csrf(csrf -> csrf
                                .csrfTokenRepository(CookieCsrfTokenRepository.withHttpOnlyFalse())
                                .csrfTokenRequestHandler(new SpaCsrfTokenRequestHandler()))
                        .formLogin(form -> form.disable())
                        .httpBasic(basic -> basic.disable())
                        .authorizeHttpRequests(auth -> auth
                                .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                                .anyRequest().authenticated())
                        .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED)))
                        .oauth2Login(login -> login
                                .authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(authorizationRequests))
                                .userInfoEndpoint(info -> info.oidcUserService(oidcUserService))
                                .defaultSuccessUrl(publicUrl + "/dashboard", true)
                                .failureUrl(publicUrl + "/signin?error"))
                        .logout(logout -> logout
                                .logoutUrl("/api/auth/logout")
                                .logoutSuccessHandler(logoutSuccessHandler()))
                        .build();
        }

        // The frontend sends the browser to this URL, which also ends the shared sign-in
        private LogoutSuccessHandler logoutSuccessHandler() {
                return (HttpServletRequest request, HttpServletResponse response, Authentication authentication) ->
                        writeLogoutUrl(response, authentication);
        }

        private void writeLogoutUrl(HttpServletResponse response, Authentication authentication) throws IOException {
                UriComponentsBuilder logout = UriComponentsBuilder.fromUriString(issuer + "/connect/logout")
                        .queryParam("post_logout_redirect_uri", publicUrl + "/");
                if (authentication != null && authentication.getPrincipal() instanceof OidcUser user) {
                        logout.queryParam("id_token_hint", user.getIdToken().getTokenValue());
                }
                response.setStatus(HttpStatus.OK.value());
                response.setContentType(MediaType.APPLICATION_JSON_VALUE);
                response.setCharacterEncoding(StandardCharsets.UTF_8.name());
                response.getWriter().write("{\"logoutUrl\":\"" + logout.build().encode().toUriString() + "\"}");
        }

        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                CorsConfiguration cors = new CorsConfiguration();
                cors.setAllowedOrigins(List.of(
                        "https://momentum.tylerpac.dev",
                        "http://localhost:5173",
                        "http://127.0.0.1:5173",
                        "http://localhost:3000",
                        "http://127.0.0.1:3000"
                ));
                cors.setAllowedMethods(List.of(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PUT.name(),
                        HttpMethod.DELETE.name(),
                        HttpMethod.OPTIONS.name()
                ));
                cors.setAllowedHeaders(List.of("Content-Type", "X-XSRF-TOKEN"));
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", cors);
                return source;
        }

        // Spring Security's documented setup for single-page applications: the browser reads the cookie and echoes
        // the raw token in a header, while rendered tokens keep their BREACH protection.
        private static final class SpaCsrfTokenRequestHandler implements CsrfTokenRequestHandler {

                private final CsrfTokenRequestHandler plain = new CsrfTokenRequestAttributeHandler();
                private final CsrfTokenRequestHandler xor = new XorCsrfTokenRequestAttributeHandler();

                @Override
                public void handle(HttpServletRequest request, HttpServletResponse response, Supplier<CsrfToken> csrfToken) {
                        xor.handle(request, response, csrfToken);
                        csrfToken.get(); // writes the cookie on every response
                }

                @Override
                public String resolveCsrfTokenValue(HttpServletRequest request, CsrfToken csrfToken) {
                        return (StringUtils.hasText(request.getHeader(csrfToken.getHeaderName())) ? plain : xor)
                                .resolveCsrfTokenValue(request, csrfToken);
                }
        }
}
