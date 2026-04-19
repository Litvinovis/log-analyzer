package com.loganalyzer.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
class LogControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnHealth() throws Exception {
        mockMvc.perform(get("/api/logs/health"))
                .andExpect(status().isOk())
                .andExpect(content().string("OK"));
    }

    @Test
    void shouldReturnPagedErrors() throws Exception {
        mockMvc.perform(get("/api/logs/errors"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(20))
                .andExpect(jsonPath("$.totalPages").isNumber());
    }

    @Test
    void shouldReturnBadRequestForInvalidDate() throws Exception {
        mockMvc.perform(get("/api/logs/errors").param("from", "not-a-date"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnStats() throws Exception {
        mockMvc.perform(get("/api/logs/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalScanned").isNumber())
                .andExpect(jsonPath("$.totalErrors").isNumber())
                .andExpect(jsonPath("$.byLevel").isMap())
                .andExpect(jsonPath("$.topMessages").isArray());
    }

    @Test
    void shouldAcceptAsyncAnalysisRequest() throws Exception {
        mockMvc.perform(post("/api/logs/analyze")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"apps\":null,\"from\":null,\"to\":null,\"levels\":null,\"contains\":null}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isString())
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    void shouldReturnNotFoundForUnknownJob() throws Exception {
        mockMvc.perform(get("/api/logs/jobs/nonexistent-id"))
                .andExpect(status().isNotFound());
    }
}
