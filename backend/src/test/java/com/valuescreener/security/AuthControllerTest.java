package com.valuescreener.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import jakarta.servlet.http.Cookie;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.time.Duration;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(SecuritySliceTestConfig.class)
class AuthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MutableClock clock;

    @Autowired
    private LoginAttemptGuard guard;

    @org.springframework.boot.test.mock.mockito.SpyBean
    private org.springframework.security.crypto.password.PasswordEncoder passwordEncoder;

    @Autowired
    private org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping handlerMapping;

    @BeforeEach
    void resetThrottleState() {
        // Guard state lives in the cached context; a long jump expires all windows, blocks and locks.
        clock.advance(Duration.ofHours(2));
    }

    private static RequestPostProcessor fromIp(String ip) {
        return request -> {
            request.setRemoteAddr(ip);
            return request;
        };
    }

    private ResultActions loginFrom(String ip, String username, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").with(csrf()).with(fromIp(ip))
                .contentType("application/json").content(body(username, password)));
    }

    private static String body(String username, String password) {
        return "{\"username\":\"" + username + "\",\"password\":\"" + password + "\"}";
    }

    private MockHttpSession login(String username, String password) throws Exception {
        MockHttpSession session = new MockHttpSession();
        mockMvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType("application/json").content(body(username, password)))
                .andExpect(status().isOk());
        return session;
    }

    @Test
    void csrfEndpointIsPublicAndSetsCookie() throws Exception {
        mockMvc.perform(get("/api/auth/csrf"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headerName").value("X-XSRF-TOKEN"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andExpect(cookie().httpOnly("XSRF-TOKEN", false));
    }

    @Test
    void loginWithoutCsrfTokenIs403() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType("application/json").content(body("admin", "admin-password-123456789")))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"Forbidden\"}"));
    }

    @Test
    void loginSucceedsForAdmin() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType("application/json").content(body("admin", "admin-password-123456789")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"username\":\"admin\",\"role\":\"ADMIN\"}", true));
    }

    @Test
    void loginSessionIsAuthenticated() throws Exception {
        MockHttpSession session = login("admin", "admin-password-123456789");
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("admin"));
    }

    @Test
    void loginSucceedsForUserCaseInsensitive() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType("application/json").content(body(" Alice ", "alice-password-1234")))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"username\":\"alice\",\"role\":\"USER\"}", true));
    }

    @Test
    void loginFailsWithGenericMessage() throws Exception {
        for (String[] creds : new String[][]{{"admin", "wrong"}, {"nobody", "whatever"}}) {
            mockMvc.perform(post("/api/auth/login").with(csrf())
                            .contentType("application/json").content(body(creds[0], creds[1])))
                    .andExpect(status().isUnauthorized())
                    .andExpect(content().json("{\"error\":\"Invalid username or password\"}", true));
        }
    }

    @Test
    void loginChangesSessionId() throws Exception {
        MockHttpSession session = new MockHttpSession();
        session.setAttribute("marker", "x");
        String before = session.getId();
        mockMvc.perform(post("/api/auth/login").session(session).with(csrf())
                        .contentType("application/json").content(body("alice", "alice-password-1234")))
                .andExpect(status().isOk());
        assertThat(session.getId()).isNotEqualTo(before);
    }

    @Test
    void meReturnsCurrentUser() throws Exception {
        MockHttpSession session = login("alice", "alice-password-1234");
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"username\":\"alice\",\"role\":\"USER\"}", true));
    }

    @Test
    void meWithoutSessionIs401WithJsonAndNoWwwAuthenticateHeader() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().doesNotExist("WWW-Authenticate"))
                .andExpect(content().json("{\"error\":\"Authentication required\"}", true));
    }

    @Test
    void logoutInvalidatesSession() throws Exception {
        MockHttpSession session = login("alice", "alice-password-1234");
        mockMvc.perform(post("/api/auth/logout").session(session).with(csrf()))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("XSRF-TOKEN", 0));
        mockMvc.perform(get("/api/auth/me").session(session))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithBlankUsernameIs400WithJsonBody() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType("application/json").content(body("", "x")))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"Invalid request\"}", true));
    }

    @Test
    void loginWithMalformedJsonIs400WithJsonBody() throws Exception {
        mockMvc.perform(post("/api/auth/login").with(csrf())
                        .contentType("application/json").content("{not json"))
                .andExpect(status().isBadRequest())
                .andExpect(content().json("{\"error\":\"Invalid request\"}", true));
    }

    @Test
    void sixthAttemptWithinMinuteIs429WithRetryAfter() throws Exception {
        for (int i = 0; i < 5; i++) {
            loginFrom("10.0.0.1", "nobody", "x").andExpect(status().isUnauthorized());
        }
        loginFrom("10.0.0.1", "admin", "admin-password-123456789")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(content().json("{\"error\":\"Too many login attempts\"}", true));
        // Other IPs are unaffected, and the block ends after a minute.
        loginFrom("10.0.0.2", "admin", "admin-password-123456789").andExpect(status().isOk());
        clock.advance(Duration.ofSeconds(60));
        loginFrom("10.0.0.1", "admin", "admin-password-123456789").andExpect(status().isOk());
    }

    @Test
    void lockedAccountRejectsCorrectPasswordWithGenericMessage() throws Exception {
        for (int i = 0; i < 10; i++) {
            loginFrom("10.1.0." + i, "alice", "wrong").andExpect(status().isUnauthorized());
        }
        loginFrom("10.1.1.1", "alice", "alice-password-1234")
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Invalid username or password\"}", true));
        clock.advance(Duration.ofMinutes(15));
        loginFrom("10.1.1.1", "alice", "alice-password-1234").andExpect(status().isOk());
    }

    @Test
    void lockedAccountStillDoesBcryptWorkAndNeverSucceeds() throws Exception {
        for (int i = 0; i < 10; i++) {
            loginFrom("10.4.0." + i, "alice", "wrong").andExpect(status().isUnauthorized());
        }
        org.mockito.Mockito.clearInvocations(passwordEncoder);
        loginFrom("10.4.1.1", "alice", "alice-password-1234")
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Invalid username or password\"}", true));
        org.mockito.Mockito.verify(passwordEncoder, org.mockito.Mockito.atLeastOnce())
                .matches(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
        assertThat(guard.isAccountLocked("alice")).isTrue();
    }

    @Test
    void loginThrottleInterceptorIsRegisteredForLoginEndpoint() throws Exception {
        var request = new org.springframework.mock.web.MockHttpServletRequest("POST", "/api/auth/login");
        org.springframework.web.util.ServletRequestPathUtils.parseAndCache(request);
        var chain = handlerMapping.getHandler(request);
        assertThat(chain).isNotNull();
        assertThat(chain.getInterceptorList()).anyMatch(i -> i instanceof LoginThrottleInterceptor);

        var other = new org.springframework.mock.web.MockHttpServletRequest("GET", "/api/auth/me");
        org.springframework.web.util.ServletRequestPathUtils.parseAndCache(other);
        assertThat(handlerMapping.getHandler(other).getInterceptorList())
                .noneMatch(i -> i instanceof LoginThrottleInterceptor);
    }

    @Test
    void unknownUsernamesAreNotTracked() throws Exception {
        int trackedBefore = guard.trackedAccountCount(); // the guard is shared across tests in the cached context
        for (int i = 0; i < 50; i++) {
            loginFrom("10.2.0." + i, UUID.randomUUID().toString(), "x")
                    .andExpect(status().isUnauthorized());
        }
        assertThat(guard.trackedAccountCount()).isEqualTo(trackedBefore);
        loginFrom("10.2.9.9", "admin", "admin-password-123456789").andExpect(status().isOk());
    }

    @Test
    void malformedLoginBodyIs400AndCounts() throws Exception {
        String[] bodies = {"{}", "not json", body("admin", "x".repeat(10_000))};
        for (String b : bodies) {
            mockMvc.perform(post("/api/auth/login").with(csrf()).with(fromIp("10.3.0.1"))
                            .contentType("application/json").content(b))
                    .andExpect(status().isBadRequest())
                    .andExpect(content().json("{\"error\":\"Invalid request\"}", true));
        }
        loginFrom("10.3.0.1", "nobody", "x").andExpect(status().isUnauthorized());
        loginFrom("10.3.0.1", "nobody", "x").andExpect(status().isUnauthorized());
        loginFrom("10.3.0.1", "nobody", "x").andExpect(status().isTooManyRequests());
    }

    @Test
    // csrf() swaps the CsrfFilter repository for the shared context, so this test needs a pristine one.
    @DirtiesContext(methodMode = DirtiesContext.MethodMode.BEFORE_METHOD)
    void csrfTokenRotatesOnLogin() throws Exception {
        var r0 = mockMvc.perform(get("/api/auth/csrf")).andReturn();
        String before = r0
                .getResponse().getCookie("XSRF-TOKEN").getValue();
        MockHttpSession session = new MockHttpSession();
        var loginResponse = mockMvc.perform(post("/api/auth/login").session(session)
                        .cookie(new Cookie("XSRF-TOKEN", before)).header("X-XSRF-TOKEN", before)
                        .contentType("application/json").content(body("alice", "alice-password-1234")))
                .andExpect(status().isOk())
                .andExpect(cookie().exists("XSRF-TOKEN"))
                .andReturn().getResponse();
        // The old cookie is cleared first, then the new one is set; the browser applies them in order.
        var setCookies = loginResponse.getHeaders("Set-Cookie");
        assertThat(setCookies.get(0)).contains("XSRF-TOKEN=;");
        String newToken = setCookies.get(setCookies.size() - 1).split(";")[0].substring("XSRF-TOKEN=".length());
        assertThat(newToken).isNotEmpty().isNotEqualTo(before);
        // The browser now holds the new cookie; the new token authorizes state-changing requests.
        mockMvc.perform(post("/api/auth/logout").session(session)
                        .cookie(new Cookie("XSRF-TOKEN", newToken)).header("X-XSRF-TOKEN", newToken))
                .andExpect(status().isNoContent());
    }
}
