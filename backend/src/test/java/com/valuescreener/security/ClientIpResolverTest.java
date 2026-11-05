package com.valuescreener.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class ClientIpResolverTest {

    private static MockHttpServletRequest request(String xff) {
        MockHttpServletRequest req = new MockHttpServletRequest();
        req.setRemoteAddr("9.9.9.9");
        if (xff != null) {
            req.addHeader("X-Forwarded-For", xff);
        }
        return req;
    }

    @Test
    void ignoresHeaderWhenNotTrusted() {
        assertThat(new ClientIpResolver(false).resolve(request("1.1.1.1, 2.2.2.2"))).isEqualTo("9.9.9.9");
    }

    @Test
    void takesRightmostEntryWhenTrusted() {
        assertThat(new ClientIpResolver(true).resolve(request("1.1.1.1, 2.2.2.2"))).isEqualTo("2.2.2.2");
    }

    @Test
    void skipsBlankTrailingEntries() {
        assertThat(new ClientIpResolver(true).resolve(request("1.1.1.1, 2.2.2.2, "))).isEqualTo("2.2.2.2");
    }

    @Test
    void fallsBackToRemoteAddrOnBlankHeader() {
        assertThat(new ClientIpResolver(true).resolve(request("  , "))).isEqualTo("9.9.9.9");
        assertThat(new ClientIpResolver(true).resolve(request(null))).isEqualTo("9.9.9.9");
    }
}
