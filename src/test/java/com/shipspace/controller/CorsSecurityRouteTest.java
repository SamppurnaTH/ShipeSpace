package com.shipspace.controller;

import com.shipspace.config.CorsConfig;
import com.shipspace.config.MockMvcContextPathCustomizer;
import com.shipspace.config.SecurityConfig;
import com.shipspace.model.Ship;
import com.shipspace.security.JwtAuthenticationFilter;
import com.shipspace.security.TokenBlacklistService;
import com.shipspace.service.BehavioralTrackingService;
import com.shipspace.service.EngagementService;
import com.shipspace.service.FeedService;
import com.shipspace.service.PersonalizationService;
import com.shipspace.service.ProjectService;
import com.shipspace.service.UserService;
import com.shipspace.config.SloEnforcementService;
import com.shipspace.util.DtoMapper;
import com.shipspace.util.JwtUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = FeedController.class,
    excludeAutoConfiguration = {
        org.springframework.boot.autoconfigure.data.mongo.MongoDataAutoConfiguration.class,
        org.springframework.boot.autoconfigure.mongo.MongoAutoConfiguration.class
    },
    excludeFilters = @org.springframework.context.annotation.ComponentScan.Filter(
        type = org.springframework.context.annotation.FilterType.ASSIGNABLE_TYPE,
        classes = com.shipspace.config.MongoAuditingConfig.class))
@AutoConfigureMockMvc
@Import({
    CorsConfig.class,
    JwtAuthenticationFilter.class,
    MockMvcContextPathCustomizer.class,
    SecurityConfig.class
})
@TestPropertySource(properties = "cors.allowed-origins=https://krevo-web.onrender.com")
@DisplayName("CORS security route tests")
class CorsSecurityRouteTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private FeedService feedService;

    @MockBean
    private PersonalizationService personalizationService;

    @MockBean
    private BehavioralTrackingService behavioralTrackingService;

    @MockBean
    private ProjectService projectService;

    @MockBean
    private UserService userService;

    @MockBean
    private DtoMapper dtoMapper;

    @MockBean
    private SloEnforcementService sloEnforcementService;

    @MockBean
    private EngagementService engagementService;

    @MockBean
    private JwtUtil jwtUtil;

    @MockBean
    private TokenBlacklistService tokenBlacklistService;

    @Test
    @DisplayName("Public feed includes CORS header for Render frontend origin")
    void publicFeed_includesCorsHeaderForRenderFrontendOrigin() throws Exception {
        when(feedService.getTrendingFeed(any(Pageable.class)))
                .thenReturn(new PageImpl<Ship>(List.of(), PageRequest.of(0, 3), 0));

        mockMvc.perform(get("/api/feeds/trending?page=0&size=3")
                .header("Origin", "https://krevo-web.onrender.com")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(header().string(
                        "Access-Control-Allow-Origin",
                        "https://krevo-web.onrender.com"));
    }
}
