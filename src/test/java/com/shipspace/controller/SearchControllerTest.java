package com.shipspace.controller;

import com.shipspace.dto.ShipResponse;
import com.shipspace.dto.UserProfileResponse;
import com.shipspace.exception.TooManyRequestsException;
import com.shipspace.service.RateLimitingService;
import com.shipspace.service.SearchService;
import com.shipspace.util.InputSanitizer;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@DisplayName("SearchController Tests")
class SearchControllerTest {

    @Mock
    private SearchService searchService;

    @Mock
    private RateLimitingService rateLimitingService;

    @Mock
    private InputSanitizer inputSanitizer;

    @Mock
    private Authentication authentication;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @InjectMocks
    private SearchController searchController;

    @BeforeEach
    void setUp() {
        lenient().when(request.getRemoteAddr()).thenReturn("203.0.113.10");
    }

    @Test
    @DisplayName("search: returns users and ships for sanitized public query")
    void search_PublicQuery_ReturnsUsersAndShips() {
        UserProfileResponse user = UserProfileResponse.builder()
                .id("user-1")
                .username("builder")
                .build();
        ShipResponse ship = ShipResponse.builder()
                .id("ship-1")
                .content("React launch")
                .build();

        when(rateLimitingService.isRateLimited(eq("203.0.113.10"), eq("search:anon"), eq(20), eq(60), eq(response)))
                .thenReturn(false);
        when(inputSanitizer.sanitize("  react  ", 100)).thenReturn("react");
        when(searchService.searchUsers("react", 10))
                .thenReturn(new SearchService.SearchResult<>(List.of(user), SearchService.SearchResult.PATH_TEXT));
        when(searchService.searchShips("react", 10))
                .thenReturn(new SearchService.SearchResult<>(List.of(ship), SearchService.SearchResult.PATH_TEXT));

        ResponseEntity<Map<String, Object>> result =
                searchController.search("  react  ", 10, null, request, response);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("users")).isEqualTo(List.of(user));
        assertThat(result.getBody().get("ships")).isEqualTo(List.of(ship));
    }

    @Test
    @DisplayName("search: clamps invalid and oversized result size")
    void search_ClampsSize() {
        when(rateLimitingService.isRateLimited(eq("203.0.113.10"), eq("search:anon"), eq(20), eq(60), eq(response)))
                .thenReturn(false);
        when(inputSanitizer.sanitize("java", 100)).thenReturn("java");
        when(searchService.searchUsers(eq("java"), any(Integer.class)))
                .thenReturn(new SearchService.SearchResult<>(List.of(), SearchService.SearchResult.PATH_EMPTY));
        when(searchService.searchShips(eq("java"), any(Integer.class)))
                .thenReturn(new SearchService.SearchResult<>(List.of(), SearchService.SearchResult.PATH_EMPTY));

        searchController.search("java", -5, null, request, response);

        ArgumentCaptor<Integer> sizeCaptor = ArgumentCaptor.forClass(Integer.class);
        verify(searchService).searchUsers(eq("java"), sizeCaptor.capture());
        assertThat(sizeCaptor.getValue()).isEqualTo(1);

        searchController.search("java", 500, null, request, response);
        verify(searchService, times(2)).searchShips(eq("java"), sizeCaptor.capture());
        assertThat(sizeCaptor.getAllValues()).contains(20);
    }

    @Test
    @DisplayName("search: empty sanitized query returns empty result contract")
    void search_EmptySanitizedQuery_ReturnsEmptyLists() {
        when(rateLimitingService.isRateLimited(eq("203.0.113.10"), eq("search:anon"), eq(20), eq(60), eq(response)))
                .thenReturn(false);
        when(inputSanitizer.sanitize("   ", 100)).thenReturn("");

        ResponseEntity<Map<String, Object>> result =
                searchController.search("   ", 10, null, request, response);

        assertThat(result.getBody()).isNotNull();
        assertThat(result.getBody().get("users")).isEqualTo(List.of());
        assertThat(result.getBody().get("ships")).isEqualTo(List.of());
        verify(searchService, never()).searchUsers(any(), any(Integer.class));
        verify(searchService, never()).searchShips(any(), any(Integer.class));
    }

    @Test
    @DisplayName("search: authenticated users are rate limited by user id")
    void search_AuthenticatedRateLimit_UsesUserId() {
        when(authentication.isAuthenticated()).thenReturn(true);
        when(authentication.getName()).thenReturn("user-1");
        when(rateLimitingService.isRateLimited(eq("user-1"), eq("search"), eq(60), eq(60), eq(response)))
                .thenReturn(true);

        assertThatThrownBy(() -> searchController.search("react", 10, authentication, request, response))
                .isInstanceOf(TooManyRequestsException.class);

        verify(inputSanitizer, never()).sanitize(any(), any(Integer.class));
    }
}
