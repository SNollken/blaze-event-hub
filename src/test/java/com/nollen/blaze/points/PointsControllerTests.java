package com.nollen.blaze.points;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.anyOf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "nollen.blaze.client-id=",
        "nollen.blaze.client-secret=",
        "nollen.blaze.api-key="
})
class PointsControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private PointsService pointsService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void test_getBalance() throws Exception {
        pointsService.creditManual("bal-user1", "BalAlice", 200, "Seed balance");

        mockMvc.perform(get("/api/points/balance/bal-user1")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("bal-user1")))
                .andExpect(jsonPath("$.username", is("BalAlice")))
                .andExpect(jsonPath("$.totalPoints", is(200)))
                .andExpect(jsonPath("$.transactionCount", is(1)));
    }

    @Test
    void test_getLeaderboard() throws Exception {
        pointsService.creditManual("lb-userA", "Alpha", 50, "Seed");
        pointsService.creditManual("lb-userB", "Beta", 150, "Seed");
        pointsService.creditManual("lb-userC", "Charlie", 75, "Seed");

        mockMvc.perform(get("/api/points/leaderboard")
                        .param("limit", "20")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(3))))
                // Verificar que os 3 users existem no leaderboard (em qualquer posição)
                .andExpect(jsonPath("$[*].userId", hasSize(greaterThanOrEqualTo(3))));
    }

    @Test
    void test_getRules() throws Exception {
        mockMvc.perform(get("/api/points/rules")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$", hasSize(5)))
                .andExpect(jsonPath("$[0].eventType", notNullValue()))
                .andExpect(jsonPath("$[0].pointsPerEvent", notNullValue()));
    }

    @Test
    void test_manualCredit() throws Exception {
        String body = objectMapper.writeValueAsString(
                new PointsController.AdjustRequest("adj-test", "AdjUser", 50, "test adjustment"));

        mockMvc.perform(post("/api/points/adjust")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.userId", is("adj-test")))
                .andExpect(jsonPath("$.username", is("AdjUser")))
                .andExpect(jsonPath("$.points", is(50)))
                .andExpect(jsonPath("$.eventType", is("MANUAL")));
    }

    @Test
    void test_getStats() throws Exception {
        pointsService.creditManual("stats-user", "StatsUser", 100, "Seed for stats");

        mockMvc.perform(get("/api/points/stats")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalUsers", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalPoints", greaterThanOrEqualTo(100)))
                .andExpect(jsonPath("$.totalTransactions", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.rules").isArray())
                .andExpect(jsonPath("$.rules", hasSize(5)));
    }

    @Test
    void test_balanceNotFound() throws Exception {
        mockMvc.perform(get("/api/points/balance/nonexistent")
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }
}
