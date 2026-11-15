package com.valuescreener.security;

import com.valuescreener.persistence.DynamoTestSupport;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Full application, DynamoDB Local: the real filter chain with sessions in DynamoDB. */
@SpringBootTest
@AutoConfigureMockMvc
class SessionEndToEndTest extends DynamoTestSupport {

    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("app.dynamodb.table-name", DynamoTestSupport::freshTableName);
        registry.add("app.admin.username", () -> "admin");
        registry.add("app.admin.password-hash", () -> new BCryptPasswordEncoder(4).encode("test-password-123456"));
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private DynamoSessionRepository sessions;

    @Autowired
    private com.valuescreener.persistence.DynamoProperties dynamoProperties;

    private long sessionItemCount() {
        return newClient().scan(b -> b.tableName(dynamoProperties.tableName()).consistentRead(true)).items().stream()
                .filter(i -> i.get("pk").s().startsWith("SESSION#")).count();
    }

    private record Csrf(String token) {
    }

    private Csrf csrf() throws Exception {
        MockHttpServletResponse response = mockMvc.perform(get("/api/auth/csrf")).andReturn().getResponse();
        return new Csrf(response.getCookie("XSRF-TOKEN").getValue());
    }

    private MockHttpServletResponse login(Csrf csrf, String sessionCookie, String ip) throws Exception {
        var request = post("/api/auth/login")
                .with(r -> {
                    r.setRemoteAddr(ip);
                    return r;
                })
                .cookie(new Cookie("XSRF-TOKEN", csrf.token())).header("X-XSRF-TOKEN", csrf.token())
                .contentType("application/json")
                .content("{\"username\":\"admin\",\"password\":\"test-password-123456\"}");
        if (sessionCookie != null) {
            request.cookie(new Cookie("JSESSIONID", sessionCookie));
        }
        return mockMvc.perform(request).andExpect(status().isOk()).andReturn().getResponse();
    }

    private static String sessionSetCookie(MockHttpServletResponse response) {
        List<String> all = response.getHeaders("Set-Cookie");
        return all.stream().filter(c -> c.startsWith("JSESSIONID=")).findFirst().orElseThrow(() -> new AssertionError("no session cookie in " + all));
    }

    private static String cookieValue(String setCookie) {
        return setCookie.split(";")[0].substring("JSESSIONID=".length());
    }

    private static String sessionId(String cookieValue) {
        return new String(Base64.getDecoder().decode(cookieValue));
    }

    @Test
    void loginSetsSessionCookieWithAllFlagsAndSessionWorksAcrossLogout() throws Exception {
        MockHttpServletResponse login = login(csrf(), null, "10.9.0.1");
        String setCookie = sessionSetCookie(login);
        assertThat(setCookie).startsWith("JSESSIONID=")
                .contains("HttpOnly").contains("Secure").contains("SameSite=Strict");
        String cookie = cookieValue(setCookie);

        // the session is in DynamoDB and works for /me
        assertThat(sessions.findById(sessionId(cookie))).isNotNull();
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("JSESSIONID", cookie)))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"username\":\"admin\",\"role\":\"ADMIN\"}", true));

        // logout deletes the item, the same cookie is rejected afterwards
        Csrf csrf = csrf();
        mockMvc.perform(post("/api/auth/logout").cookie(new Cookie("JSESSIONID", cookie))
                        .cookie(new Cookie("XSRF-TOKEN", csrf.token())).header("X-XSRF-TOKEN", csrf.token()))
                .andExpect(status().isNoContent());
        assertThat(sessions.findById(sessionId(cookie))).isNull();
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("JSESSIONID", cookie)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sessionIdChangesOnLogin() throws Exception {
        DynamoSession anonymous = sessions.createSession();
        anonymous.setAttribute("marker", "x");
        sessions.save(anonymous);
        String oldCookie = Base64.getEncoder().encodeToString(anonymous.getId().getBytes());

        MockHttpServletResponse login = login(csrf(), oldCookie, "10.9.0.2");

        String newCookie = cookieValue(sessionSetCookie(login));
        assertThat(newCookie).isNotEqualTo(oldCookie);
        assertThat(sessions.findById(anonymous.getId())).isNull();
        assertThat(sessions.findById(sessionId(newCookie))).isNotNull();
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("JSESSIONID", oldCookie)))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("JSESSIONID", newCookie)))
                .andExpect(status().isOk());
    }

    @Test
    void ordinaryRequestsWithinRefreshIntervalDoNotRewriteTheSession() throws Exception {
        String cookie = cookieValue(sessionSetCookie(login(csrf(), null, "10.9.0.3")));
        var before = sessions.findById(sessionId(cookie)).getLastAccessedTime();
        Thread.sleep(20);
        mockMvc.perform(get("/api/auth/me").cookie(new Cookie("JSESSIONID", cookie))).andExpect(status().isOk());
        assertThat(sessions.findById(sessionId(cookie)).getLastAccessedTime()).isEqualTo(before);
    }

    @Test
    void anonymousRequestToProtectedEndpointCreatesNoSession() throws Exception {
        long before = sessionItemCount();
        for (String path : new String[]{"/api/auth/me", "/api/portfolio", "/api/research/snapshots"}) {
            var response = mockMvc.perform(get(path)).andExpect(status().isUnauthorized()).andReturn().getResponse();
            assertThat(response.getHeaders("Set-Cookie")).noneMatch(c -> c.startsWith("JSESSIONID"));
        }
        assertThat(sessionItemCount()).isEqualTo(before);
    }
}
