package com.valuescreener.research;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.FilterType;
import org.springframework.test.web.servlet.MockMvc;

import com.valuescreener.security.LoginThrottleWebConfig;

import java.util.List;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = StockGroupController.class, excludeFilters = @ComponentScan.Filter(
        type = FilterType.ASSIGNABLE_TYPE, classes = LoginThrottleWebConfig.class))
@AutoConfigureMockMvc(addFilters = false)
class StockGroupControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private StockGroupService service;

    private static final StockGroupView BANKS = new StockGroupView("BANKS", "Banks", 10, 2);

    @Test
    void listReturns200() throws Exception {
        when(service.list()).thenReturn(List.of(BANKS));

        mockMvc.perform(get("/api/research/groups"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].key").value("BANKS"))
                .andExpect(jsonPath("$[0].stockCount").value(2));
    }

    @Test
    void createReturns201() throws Exception {
        when(service.create(new CreateStockGroupRequest("BANKS", "Banks"))).thenReturn(BANKS);

        mockMvc.perform(post("/api/research/groups").contentType("application/json")
                        .content("{\"key\":\"BANKS\",\"name\":\"Banks\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Banks"));
    }

    @Test
    void createConflictReturns409WithErrorBody() throws Exception {
        when(service.create(new CreateStockGroupRequest("BANKS", "Banks")))
                .thenThrow(new StockGroupConflictException("Group key already exists: BANKS"));

        mockMvc.perform(post("/api/research/groups").contentType("application/json")
                        .content("{\"key\":\"BANKS\",\"name\":\"Banks\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Group key already exists: BANKS"));
    }

    @Test
    void createWithBlankNameReturns400() throws Exception {
        mockMvc.perform(post("/api/research/groups").contentType("application/json")
                        .content("{\"key\":\"BANKS\",\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createWithInvalidKeyReturns400() throws Exception {
        when(service.create(new CreateStockGroupRequest("1X", "X")))
                .thenThrow(new IllegalArgumentException("key must match [A-Z][A-Z0-9_]{1,39}"));

        mockMvc.perform(post("/api/research/groups").contentType("application/json")
                        .content("{\"key\":\"1X\",\"name\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void patchReturns200() throws Exception {
        when(service.update("BANKS", new UpdateStockGroupRequest("Banking", null)))
                .thenReturn(new StockGroupView("BANKS", "Banking", 10, 2));

        mockMvc.perform(patch("/api/research/groups/BANKS").contentType("application/json")
                        .content("{\"name\":\"Banking\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Banking"));
    }

    @Test
    void patchUnknownReturns404() throws Exception {
        when(service.update("NOPE", new UpdateStockGroupRequest("x", null)))
                .thenThrow(new StockGroupNotFoundException("NOPE"));

        mockMvc.perform(patch("/api/research/groups/NOPE").contentType("application/json")
                        .content("{\"name\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteReturns204() throws Exception {
        mockMvc.perform(delete("/api/research/groups/BANKS")).andExpect(status().isNoContent());
        verify(service).delete("BANKS");
    }

    @Test
    void deleteUsedGroupReturns409() throws Exception {
        doThrow(new StockGroupConflictException("Group BANKS still has 2 stocks")).when(service).delete("BANKS");

        mockMvc.perform(delete("/api/research/groups/BANKS"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Group BANKS still has 2 stocks"));
    }

    @Test
    void unknownGroupReturns422() throws Exception {
        when(service.list()).thenThrow(new UnknownGroupException("NOPE"));

        mockMvc.perform(get("/api/research/groups"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("Unknown group key: NOPE"));
    }
}
