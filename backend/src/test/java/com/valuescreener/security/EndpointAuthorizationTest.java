package com.valuescreener.security;

import com.valuescreener.portfolio.PortfolioController;
import com.valuescreener.portfolio.PortfolioService;
import com.valuescreener.research.CompanySnapshotController;
import com.valuescreener.research.CompanySnapshotService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PortfolioController.class, CompanySnapshotController.class})
@Import(SecuritySliceTestConfig.class)
class EndpointAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioService portfolioService;

    @MockBean
    private CompanySnapshotService companySnapshotService;

    static Stream<String> writeEndpoints() {
        return Stream.of("/api/portfolio", "/api/portfolio/sell", "/api/research/snapshots");
    }

    private MockHttpServletRequestBuilder write(String path) {
        return post(path).contentType("application/json").content("{}");
    }

    @ParameterizedTest
    @MethodSource("writeEndpoints")
    void anonymousWriteIs401(String path) throws Exception {
        mockMvc.perform(write(path).with(csrf()))
                .andExpect(status().isUnauthorized())
                .andExpect(content().json("{\"error\":\"Authentication required\"}"));
    }

    @ParameterizedTest
    @MethodSource("writeEndpoints")
    void userWriteIs403(String path) throws Exception {
        mockMvc.perform(write(path).with(user("alice").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden())
                .andExpect(content().json("{\"error\":\"Forbidden\"}"));
    }

    @ParameterizedTest
    @MethodSource("writeEndpoints")
    void adminWriteIsNotRejectedBySecurity(String path) throws Exception {
        MvcResult result = mockMvc.perform(write(path).with(user("admin").roles("ADMIN")).with(csrf()))
                .andReturn();
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @ParameterizedTest
    @MethodSource("writeEndpoints")
    void adminWriteWithoutCsrfIs403(String path) throws Exception {
        mockMvc.perform(write(path).with(user("admin").roles("ADMIN")))
                .andExpect(status().isForbidden());
    }

    @Test
    void snapshotReadAnonymousIs401() throws Exception {
        mockMvc.perform(get("/api/research/snapshots"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void snapshotReadAsUserIs200() throws Exception {
        mockMvc.perform(get("/api/research/snapshots").with(user("alice").roles("USER")))
                .andExpect(status().isOk());
    }

    @Test
    void errorDispatchIsNotBlockedByAuthorization() throws Exception {
        for (var auth : new org.springframework.test.web.servlet.request.RequestPostProcessor[]{
                user("alice").roles("USER"), request -> request}) {
            MvcResult result = mockMvc.perform(get("/error").with(auth).with(request -> {
                request.setDispatcherType(jakarta.servlet.DispatcherType.ERROR);
                return request;
            })).andReturn();
            assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
        }
    }

    @Test
    void nonErrorDispatchToUnknownPathStillRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/error")).andExpect(status().isUnauthorized());
    }
}
