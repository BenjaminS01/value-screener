package com.valuescreener.security;

import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.csrf.CsrfTokenRepository;

import static org.assertj.core.api.Assertions.assertThat;

class CsrfCookieAttributesTest {

    /** The repository adds a servlet Cookie (SameSite is a cookie attribute that the container serializes). */
    private static Cookie xsrfCookie(boolean secure) {
        CsrfTokenRepository repository = new SecurityConfig().csrfTokenRepository(secure);
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        CsrfToken token = repository.generateToken(request);
        repository.saveToken(token, request, response);
        return response.getCookie("XSRF-TOKEN");
    }

    @Test
    void cookieIsSameSiteStrictSecureAndReadableBySpa() {
        Cookie cookie = xsrfCookie(true);
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(cookie.getSecure()).isTrue();
        assertThat(cookie.isHttpOnly()).isFalse();
    }

    @Test
    void cookieIsNotSecureWhenSessionCookieSecureIsFalse() {
        Cookie cookie = xsrfCookie(false);
        assertThat(cookie.getAttribute("SameSite")).isEqualTo("Strict");
        assertThat(cookie.getSecure()).isFalse();
        assertThat(cookie.isHttpOnly()).isFalse();
    }
}
