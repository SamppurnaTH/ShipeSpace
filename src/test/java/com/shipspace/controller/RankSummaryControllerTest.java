package com.shipspace.controller;

import com.shipspace.dto.RankSummaryResponse;
import com.shipspace.exception.ResourceNotFoundException;
import com.shipspace.model.RankTier;
import com.shipspace.model.User;
import com.shipspace.service.ShipScoreService;
import com.shipspace.service.UserService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for RankSummaryController.
 * Tests the rank summary endpoint for user progression data.
 *
 * Validates Requirements 12.2, 12.3, 12.4, 12.5, 12.6
 */
@WebMvcTest(controllers = RankSummaryController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration.class,
        org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class
    },
    excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = com.shipspace.config.MongoAuditingConfig.class))
@AutoConfigureMockMvc
@Import(com.shipspace.config.MockMvcContextPathCustomizer.class)
@ActiveProfiles("test")
@DisplayName("RankSummaryController Unit Tests")
class RankSummaryControllerTest {

    @TestConfiguration
    static class TestConfig {
        @Bean
        public org.springframework.data.mongodb.core.mapping.MongoMappingContext mongoMappingContext() {
            return new org.springframework.data.mongodb.core.mapping.MongoMappingContext();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ShipScoreService shipScoreService;

    @MockBean
    private com.shipspace.util.JwtUtil jwtUtil;

    @MockBean
    private com.shipspace.security.TokenBlacklistService tokenBlacklistService;

    @MockBean
    private UserService userService;

    private User testUser;
    private String testUserId;

    @BeforeEach
    void setUp() {
        testUserId = "user123";

        testUser = User.builder()
                .id(testUserId)
                .githubId("github123")
                .username("testuser")
                .email("test@example.com")
                .shipScore(100)
                .level(3)
                .rank(RankTier.DOCKED)
                .lastShipActivityAt(Instant.now())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/users/{id}/rank-summary - Should return complete response with all fields")
    void getRankSummary_ValidUser_ReturnsCompleteResponse() throws Exception {
        when(userService.getUserById(testUserId)).thenReturn(testUser);
        when(shipScoreService.calculateNextLevelScore(3, 100)).thenReturn(120);

        mockMvc.perform(get("/api/v1/users/{id}/rank-summary", testUserId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.shipScore").value(100))
                .andExpect(jsonPath("$.level").value(3))
                .andExpect(jsonPath("$.rank").value("DOCKED"))
                .andExpect(jsonPath("$.nextLevelScore").value(120));

        verify(userService).getUserById(testUserId);
        verify(shipScoreService).calculateNextLevelScore(3, 100);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/users/{id}/rank-summary - Should return rank as string")
    void getRankSummary_ValidUser_ReturnsRankAsString() throws Exception {
        testUser.setRank(RankTier.LAUNCHER);
        testUser.setLevel(8);
        testUser.setShipScore(280);

        when(userService.getUserById(testUserId)).thenReturn(testUser);
        when(shipScoreService.calculateNextLevelScore(8, 280)).thenReturn(320);

        mockMvc.perform(get("/api/v1/users/{id}/rank-summary", testUserId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.rank").value("LAUNCHER"))
                .andExpect(jsonPath("$.rank").isString());

        verify(userService).getUserById(testUserId);
        verify(shipScoreService).calculateNextLevelScore(8, 280);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/users/{id}/rank-summary - Should calculate nextLevelScore for user below level 30")
    void getRankSummary_UserBelowLevel30_CalculatesNextLevelScore() throws Exception {
        testUser.setLevel(15);
        testUser.setShipScore(560);
        testUser.setRank(RankTier.ORBITER);

        when(userService.getUserById(testUserId)).thenReturn(testUser);
        when(shipScoreService.calculateNextLevelScore(15, 560)).thenReturn(600);

        mockMvc.perform(get("/api/v1/users/{id}/rank-summary", testUserId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.level").value(15))
                .andExpect(jsonPath("$.shipScore").value(560))
                .andExpect(jsonPath("$.nextLevelScore").value(600));

        verify(userService).getUserById(testUserId);
        verify(shipScoreService).calculateNextLevelScore(15, 560);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/users/{id}/rank-summary - Should return null nextLevelScore for user at level 30")
    void getRankSummary_UserAtLevel30_ReturnsNullNextLevelScore() throws Exception {
        testUser.setLevel(30);
        testUser.setShipScore(1200);
        testUser.setRank(RankTier.FLEET_ADMIRAL);

        when(userService.getUserById(testUserId)).thenReturn(testUser);
        when(shipScoreService.calculateNextLevelScore(30, 1200)).thenReturn(null);

        mockMvc.perform(get("/api/v1/users/{id}/rank-summary", testUserId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.level").value(30))
                .andExpect(jsonPath("$.rank").value("FLEET_ADMIRAL"))
                .andExpect(jsonPath("$.nextLevelScore").doesNotExist());

        verify(userService).getUserById(testUserId);
        verify(shipScoreService).calculateNextLevelScore(30, 1200);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/users/{id}/rank-summary - Should return 404 Not Found for invalid user ID")
    void getRankSummary_InvalidUserId_Returns404() throws Exception {
        String invalidUserId = "nonexistent";
        when(userService.getUserById(invalidUserId))
                .thenThrow(new ResourceNotFoundException("User not found with id: " + invalidUserId));

        mockMvc.perform(get("/api/v1/users/{id}/rank-summary", invalidUserId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("User not found with id: " + invalidUserId));

        verify(userService).getUserById(invalidUserId);
        verify(shipScoreService, never()).calculateNextLevelScore(anyInt(), anyInt());
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/users/{id}/rank-summary - Should handle user with zero shipScore")
    void getRankSummary_UserWithZeroShipScore_ReturnsCorrectData() throws Exception {
        testUser.setShipScore(0);
        testUser.setLevel(1);
        testUser.setRank(RankTier.DOCKED);

        when(userService.getUserById(testUserId)).thenReturn(testUser);
        when(shipScoreService.calculateNextLevelScore(1, 0)).thenReturn(40);

        mockMvc.perform(get("/api/v1/users/{id}/rank-summary", testUserId))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.shipScore").value(0))
                .andExpect(jsonPath("$.level").value(1))
                .andExpect(jsonPath("$.rank").value("DOCKED"))
                .andExpect(jsonPath("$.nextLevelScore").value(40));

        verify(userService).getUserById(testUserId);
        verify(shipScoreService).calculateNextLevelScore(1, 0);
    }

    @Test
    @WithMockUser
    @DisplayName("GET /api/v1/users/{id}/rank-summary - Should handle all rank tiers correctly")
    void getRankSummary_AllRankTiers_ReturnsCorrectStrings() throws Exception {
        testUser.setLevel(23);
        testUser.setShipScore(880);
        testUser.setRank(RankTier.COMMANDER);

        when(userService.getUserById(testUserId)).thenReturn(testUser);
        when(shipScoreService.calculateNextLevelScore(23, 880)).thenReturn(920);

        mockMvc.perform(get("/api/v1/users/{id}/rank-summary", testUserId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rank").value("COMMANDER"));

        verify(userService).getUserById(testUserId);
        verify(shipScoreService).calculateNextLevelScore(23, 880);
    }
}
