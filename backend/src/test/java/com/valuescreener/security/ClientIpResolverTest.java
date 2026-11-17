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

    private static MockHttpServletRequest viewerRequest(String viewer) {
        MockHttpServletRequest req = request(null);
        if (viewer != null) {
            req.addHeader("CloudFront-Viewer-Address", viewer);
        }
        return req;
    }

    private static ClientIpResolver viaCloudFront(String secret) {
        return new ClientIpResolver(false, secret, "CloudFront-Viewer-Address");
    }

    @Test
    void usesViewerAddressIpv4() {
        assertThat(viaCloudFront("test-secret").resolve(viewerRequest("203.0.113.7:51234"))).isEqualTo("203.0.113.7");
    }

    @Test
    void usesViewerAddressIpv6() {
        assertThat(viaCloudFront("test-secret").resolve(viewerRequest("2001:db8::1:51234"))).isEqualTo("2001:db8::1");
    }

    @Test
    void usesBracketedIpv6ViewerAddress() {
        assertThat(viaCloudFront("test-secret").resolve(viewerRequest("[2001:db8::1]:51234"))).isEqualTo("2001:db8::1");
    }

    @Test
    void fallsBackToRemoteAddrWhenViewerHeaderMissing() {
        assertThat(viaCloudFront("test-secret").resolve(viewerRequest(null))).isEqualTo("9.9.9.9");
    }

    @Test
    void ignoresViewerHeaderWhenOriginVerifyInactive() {
        assertThat(viaCloudFront("").resolve(viewerRequest("203.0.113.7:51234"))).isEqualTo("9.9.9.9");
    }

    @Test
    void ignoresViewerHeaderWhenHeaderNameNotConfigured() {
        ClientIpResolver resolver = new ClientIpResolver(false, "test-secret", "");
        assertThat(resolver.resolve(viewerRequest("203.0.113.7:51234"))).isEqualTo("9.9.9.9");
    }
}
