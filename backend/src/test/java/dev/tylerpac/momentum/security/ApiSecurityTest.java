package dev.tylerpac.momentum.security;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oidcLogin;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import dev.tylerpac.momentum.controller.AuthController;
import dev.tylerpac.momentum.dto.common.UserDto;
import dev.tylerpac.momentum.mapper.UserDtoMapper;
import dev.tylerpac.momentum.model.Users;
import dev.tylerpac.momentum.repository.UsersRepository;
import jakarta.servlet.http.Cookie;

@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
@TestPropertySource(properties = {
    "momentum.login.issuer=https://auth.example.test",
    "momentum.login.internal-url=https://auth.example.test",
    "momentum.login.client-secret=secret",
    "momentum.public-url=https://app.example.test"
})
class ApiSecurityTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean UsersRepository users;
    @MockitoBean UserDtoMapper mapper;
    @MockitoBean LocalAccountService accounts;

    @Test
    void apiRequiresSignInAndReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }

    @Test
    void signedInUserIsResolvedByTheSharedAccountId() throws Exception {
        Users user = new Users();
        when(users.findByExternalSubject("account-1")).thenReturn(Optional.of(user));
        when(mapper.toDto(user)).thenReturn(new UserDto(7, "alex", "metric"));

        mockMvc.perform(get("/api/auth/me").with(oidcLogin().idToken(token -> token.subject("account-1"))))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("alex"));
    }

    @Test
    void anAccountWithNoLocalRowIsNotSignedIn() throws Exception {
        when(users.findByExternalSubject("account-2")).thenReturn(Optional.empty());
        mockMvc.perform(get("/api/auth/me").with(oidcLogin().idToken(token -> token.subject("account-2"))))
            .andExpect(status().isUnauthorized());
    }

    @Test
    void changesWithoutACsrfTokenAreRefused() throws Exception {
        mockMvc.perform(post("/api/auth/logout").with(oidcLogin())).andExpect(status().isForbidden());
    }

    @Test
    void theCsrfCookieIsIssuedForTheFrontend() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(cookie().exists("XSRF-TOKEN"));
    }

    @Test
    void signInRedirectUsesPkceAgainstTheSharedService() throws Exception {
        mockMvc.perform(get("/oauth2/authorization/shared"))
            .andExpect(status().is3xxRedirection())
            .andExpect(header().string("Location", containsString("https://auth.example.test/oauth2/authorize")))
            .andExpect(header().string("Location", containsString("code_challenge_method=S256")))
            .andExpect(header().string("Location", containsString("redirect_uri=https://app.example.test/login/oauth2/code/shared")));
    }

    @Test
    void logoutReturnsTheSharedLogoutUrl() throws Exception {
        // The same exchange the frontend performs: read the cookie, send its value back as a header
        Cookie token = mockMvc.perform(get("/api/auth/me")).andReturn().getResponse().getCookie("XSRF-TOKEN");
        mockMvc.perform(post("/api/auth/logout").cookie(token).header("X-XSRF-TOKEN", token.getValue()).with(oidcLogin()))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.logoutUrl", containsString("https://auth.example.test/connect/logout")))
            .andExpect(jsonPath("$.logoutUrl", containsString("post_logout_redirect_uri=https://app.example.test/")));
    }
}
