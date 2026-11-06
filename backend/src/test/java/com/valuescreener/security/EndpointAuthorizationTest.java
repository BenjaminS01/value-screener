package com.valuescreener.security;

import com.valuescreener.portfolio.PortfolioController;
import com.valuescreener.portfolio.PortfolioService;
import com.valuescreener.research.CompanySnapshotController;
import com.valuescreener.research.CompanySnapshotService;
import com.valuescreener.research.StockGroupController;
import com.valuescreener.research.StockGroupService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PortfolioController.class, CompanySnapshotController.class, StockGroupController.class})
@Import(SecuritySliceTestConfig.class)
class EndpointAuthorizationTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PortfolioService portfolioService;

    @MockBean
    private CompanySnapshotService companySnapshotService;

    @MockBean
    private StockGroupService stockGroupService;

    static Stream<String> writeEndpoints() {
        return Stream.of("/api/portfolio", "/api/portfolio/sell");
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

    static Stream<MockHttpServletRequestBuilder> portfolioItemWrites() {
        String body = "{\"ticker\":\"AAPL\",\"companyName\":\"Apple\",\"quantity\":1,\"entryPrice\":1,\"purchaseDate\":\"2026-04-01\"}";
        return Stream.of(
                put("/api/portfolio/US0378331005").contentType("application/json").content(body),
                delete("/api/portfolio/US0378331005"));
    }

    @ParameterizedTest
    @MethodSource("portfolioItemWrites")
    void userPutAndDeleteAre403(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(user("alice").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("portfolioItemWrites")
    void adminPutAndDeleteAreNot403(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request.with(user("admin").roles("ADMIN")).with(csrf())).andReturn();
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }

    @ParameterizedTest
    @MethodSource("portfolioItemWrites")
    void anonymousPutAndDeleteAre401(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(csrf())).andExpect(status().isUnauthorized());
    }

    @Test
    void oldPostSnapshotsEndpointIsGone() throws Exception {
        mockMvc.perform(write("/api/research/snapshots").with(user("admin").roles("ADMIN")).with(csrf()))
                .andExpect(status().isMethodNotAllowed());
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

    @Test
    void groupListAsUserIs200() throws Exception {
        mockMvc.perform(get("/api/research/groups").with(user("alice").roles("USER")))
                .andExpect(status().isOk());
    }

    static Stream<MockHttpServletRequestBuilder> groupWrites() {
        return Stream.of(
                post("/api/research/groups").contentType("application/json").content("{\"key\":\"X_Y\",\"name\":\"X\"}"),
                patch("/api/research/groups/BANKS").contentType("application/json").content("{\"name\":\"X\"}"),
                delete("/api/research/groups/BANKS"));
    }

    @ParameterizedTest
    @MethodSource("groupWrites")
    void groupWritesAsUserAre403(MockHttpServletRequestBuilder request) throws Exception {
        mockMvc.perform(request.with(user("alice").roles("USER")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @MethodSource("groupWrites")
    void groupWritesAsAdminAreNotRejectedBySecurity(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request.with(user("admin").roles("ADMIN")).with(csrf())).andReturn();
        assertThat(result.getResponse().getStatus()).isNotIn(401, 403);
    }
}
