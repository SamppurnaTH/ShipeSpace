package com.shipspace.integration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.shipspace.dto.ShipCreateRequest;
import com.shipspace.model.Project;
import com.shipspace.model.RankTier;
import com.shipspace.model.Ship;
import com.shipspace.model.User;
import com.shipspace.repository.ProjectRepository;
import com.shipspace.repository.ShipRepository;
import com.shipspace.repository.UserRepository;
import com.shipspace.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-end integration test for ship creation.
 *
 * Tests the full path: HTTP POST → ShipController → ShipService → MongoDB.
 * Verifies that ships are actually persisted with correct fields and that
 * project metadata (shipCount) is updated atomically.
 *
 * Uses the real local MongoDB (shipspace-integration-test database) and Redis.
 * Flapdoodle embedded MongoDB is explicitly excluded — it cannot download its
 * binary in this environment.
 *
 * MockMvc path convention: post("/api/ships").contextPath("/api")
 *   - The URI must include the context path prefix.
 *   - .contextPath("/api") tells MockMvc the prefix so it sets servletPath = /ships.
 *   - The DispatcherServlet then routes /ships to ShipController correctly.
 */
@SpringBootTest(properties = {
        "spring.autoconfigure.exclude=de.flapdoodle.embed.mongo.spring.autoconfigure.EmbeddedMongoAutoConfiguration",
        "spring.main.allow-bean-definition-overriding=true"
})
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Ship Creation Integration Tests")
class ShipCreationIntegrationTest {

    private static final String SHIPS_URL = "/api/ships";
    private static final String CONTEXT_PATH = "/api";
    private static final String DATABASE_NAME = "shipspace-integration-test-" + System.nanoTime();

    @DynamicPropertySource
    static void registerMongoProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", () -> "mongodb://localhost:27017/" + DATABASE_NAME);
        registry.add("spring.data.mongodb.database", () -> DATABASE_NAME);
        registry.add("spring.data.mongodb.auto-index-creation", () -> "false");
        registry.add("management.endpoint.health.validate-group-membership", () -> "false");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ProjectRepository projectRepository;

    @Autowired
    private ShipRepository shipRepository;

    @Autowired
    private JwtUtil jwtUtil;

    private User testUser;
    private Project testProject;
    private String accessToken;

    @BeforeEach
    void setUp() {
        shipRepository.deleteAll();
        projectRepository.deleteAll();
        userRepository.deleteAll();

        testUser = userRepository.save(User.builder()
                .githubId("gh-ship-integration-" + System.nanoTime())
                .username("shipbuilder-" + System.nanoTime())
                .email("builder-" + System.nanoTime() + "@integration.test")
                .shipScore(0)
                .level(1)
                .rank(RankTier.DOCKED)
                .followerCount(0)
                .followingCount(0)
                .projectCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        testProject = projectRepository.save(Project.builder()
                .userId(testUser.getId())
                .name("Integration Test Project")
                .description("A real project for end-to-end ship creation testing")
                .shipCount(0)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build());

        accessToken = jwtUtil.generateToken(testUser.getId(), testUser.getUsername());
    }

    @Test
    @DisplayName("POST /api/ships — persists ship to MongoDB with correct fields")
    void createShip_persistsToDbWithCorrectFields() throws Exception {
        ShipCreateRequest request = ShipCreateRequest.builder()
                .projectId(testProject.getId())
                .content("Shipped my first feature today!")
                .build();

        mockMvc.perform(post(SHIPS_URL).contextPath(CONTEXT_PATH)
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").value(testUser.getId()))
                .andExpect(jsonPath("$.projectId").value(testProject.getId()))
                .andExpect(jsonPath("$.content").value("Shipped my first feature today!"))
                .andExpect(jsonPath("$.editWindowOpen").value(true))
                .andExpect(jsonPath("$.likeCount").value(0))
                .andExpect(jsonPath("$.commentCount").value(0));

        List<Ship> ships = shipRepository.findAll();
        assertThat(ships).hasSize(1);

        Ship saved = ships.get(0);
        assertThat(saved.getUserId()).isEqualTo(testUser.getId());
        assertThat(saved.getProjectId()).isEqualTo(testProject.getId());
        assertThat(saved.getContent()).isEqualTo("Shipped my first feature today!");
        assertThat(saved.getLikeCount()).isZero();
        assertThat(saved.getCommentCount()).isZero();
        assertThat(saved.getEditWindowExpires()).isAfter(Instant.now());
    }

    @Test
    @DisplayName("POST /api/ships — project shipCount is atomically incremented")
    void createShip_incrementsProjectShipCount() throws Exception {
        ShipCreateRequest request = ShipCreateRequest.builder()
                .projectId(testProject.getId())
                .content("Shipping a feature to verify shipCount")
                .build();

        mockMvc.perform(post(SHIPS_URL).contextPath(CONTEXT_PATH)
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        Project updated = projectRepository.findById(testProject.getId()).orElseThrow();
        assertThat(updated.getShipCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("POST /api/ships — returns 403 when user does not own the project")
    void createShip_returns403WhenUserDoesNotOwnProject() throws Exception {
        User otherUser = userRepository.save(User.builder()
                .githubId("gh-other-" + System.nanoTime())
                .username("other-" + System.nanoTime())
                .email("other-" + System.nanoTime() + "@integration.test")
                .shipScore(0).level(1).rank(RankTier.DOCKED)
                .followerCount(0).followingCount(0).projectCount(0)
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build());

        String otherToken = jwtUtil.generateToken(otherUser.getId(), otherUser.getUsername());

        ShipCreateRequest request = ShipCreateRequest.builder()
                .projectId(testProject.getId())
                .content("Trying to ship on someone else's project")
                .build();

        mockMvc.perform(post(SHIPS_URL).contextPath(CONTEXT_PATH)
                        .with(csrf())
                        .header("Authorization", "Bearer " + otherToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());

        assertThat(shipRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("POST /api/ships — rejects unauthenticated request and persists nothing")
    void createShip_rejectsUnauthenticatedRequest() throws Exception {
        ShipCreateRequest request = ShipCreateRequest.builder()
                .projectId(testProject.getId())
                .content("Anonymous ship attempt")
                .build();

        mockMvc.perform(post(SHIPS_URL).contextPath(CONTEXT_PATH)
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().is4xxClientError());

        assertThat(shipRepository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("POST /api/ships — returns 404 when project does not exist")
    void createShip_returns404WhenProjectDoesNotExist() throws Exception {
        ShipCreateRequest request = ShipCreateRequest.builder()
                .projectId("nonexistent-project-id")
                .content("Ship for a ghost project")
                .build();

        mockMvc.perform(post(SHIPS_URL).contextPath(CONTEXT_PATH)
                        .with(csrf())
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());

        assertThat(shipRepository.findAll()).isEmpty();
    }
}
