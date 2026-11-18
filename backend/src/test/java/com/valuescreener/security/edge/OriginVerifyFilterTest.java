package com.valuescreener.security.edge;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OriginVerifyFilterTest {

    private static final String SECRET = "test-secret";

    private MockHttpServletResponse run(OriginVerifyFilter filter, String header, FilterChain chain) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/auth/csrf");
        if (header != null) {
            request.addHeader("X-Origin-Verify", header);
        }
        MockHttpServletResponse response = new MockHttpServletResponse();
        filter.doFilter(request, response, chain);
        return response;
    }

    @Test
    void inactiveWhenSecretBlank() throws Exception {
        for (String blank : new String[] {"", "  ", null}) {
            MockFilterChain chain = new MockFilterChain();
            MockHttpServletResponse response = run(new OriginVerifyFilter(blank), null, chain);
            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(chain.getRequest()).isNotNull();
        }
    }

    @Test
    void rejectsMissingHeaderWith403() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(new OriginVerifyFilter(SECRET), null, chain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(response.getHeaderNames()).isEmpty();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void rejectsWrongHeaderWith403() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(new OriginVerifyFilter(SECRET), "test-secreT", chain);
        assertThat(response.getStatus()).isEqualTo(403);
        assertThat(response.getContentAsString()).isEmpty();
        assertThat(chain.getRequest()).isNull();
    }

    @Test
    void passesCorrectHeader() throws Exception {
        MockFilterChain chain = new MockFilterChain();
        MockHttpServletResponse response = run(new OriginVerifyFilter(SECRET), SECRET, chain);
        assertThat(response.getStatus()).isEqualTo(200);
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    void requiredWithBlankSecretFailsConstruction() {
        for (String blank : new String[] {"", "  ", null}) {
            assertThatThrownBy(() -> new OriginVerifyFilter(blank, true))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("origin-verify");
        }
    }

    @Test
    void requiredWithSecretIsActive() throws Exception {
        OriginVerifyFilter filter = new OriginVerifyFilter(SECRET, true);
        assertThat(run(filter, null, new MockFilterChain()).getStatus()).isEqualTo(403);
        assertThat(run(filter, SECRET, new MockFilterChain()).getStatus()).isEqualTo(200);
    }

    @Test
    void notRequiredWithBlankSecretStaysInactive() {
        new OriginVerifyFilter("", false);
    }
}
