package com.shipspace.service;

import com.shipspace.dto.BuilderDiscoveryResponse;
import com.shipspace.model.User;
import com.shipspace.repository.FollowingRepository;
import com.shipspace.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service for builder discovery on the People page.
 * Provides paginated, sorted builder lists with viewer-aware isFollowing enrichment.
 *
 * <p>Discovery modes:
 * <ul>
 *   <li>discover — recently active builders, then by ShipScore, deterministic ID tie-break</li>
 *   <li>top — highest ShipScore descending, deterministic ID tie-break</li>
 *   <li>new — most recently joined (createdAt DESC), deterministic ID tie-break</li>
 * </ul>
 *
 * <p>All modes exclude banned users from results.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BuilderDiscoveryService {

    private final MongoTemplate mongoTemplate;
    private final FollowingRepository followingRepository;

    private static final int MAX_PAGE_SIZE = 50;

    /**
     * Supported sort modes for discovery.
     */
    public enum DiscoverySort {
        /** Default discovery: lastShipActivityAt DESC → shipScore DESC → id ASC */
        DISCOVER,
        /** Top builders: shipScore DESC → id ASC */
        TOP,
        /** Newest builders: createdAt DESC → id ASC */
        NEW
    }

    /**
     * Retrieves a paginated list of discoverable builders.
     *
     * @param page         page number (0-indexed)
     * @param size         page size (clamped to MAX_PAGE_SIZE)
     * @param sortMode     discovery sort mode
     * @param viewerUserId authenticated viewer's user ID (nullable for anonymous)
     * @return paginated builder discovery responses with isFollowing enrichment
     */
    public Page<BuilderDiscoveryResponse> discoverBuilders(int page, int size, DiscoverySort sortMode, String viewerUserId) {
        size = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        page = Math.max(0, page);

        Sort sort = buildSort(sortMode);
        Pageable pageable = PageRequest.of(page, size, sort);

        // Exclude banned users from discovery
        Query query = new Query(Criteria.where("banned").is(false));
        query.with(pageable);

        long total = mongoTemplate.count(new Query(Criteria.where("banned").is(false)), User.class);
        List<User> users = mongoTemplate.find(query, User.class);

        // Batch isFollowing enrichment
        Set<String> followingIds = getFollowingIds(viewerUserId, users);

        List<BuilderDiscoveryResponse> responses = users.stream()
                .map(user -> toResponse(user, followingIds.contains(user.getId()), viewerUserId))
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, total);
    }

    /**
     * Searches builders by username or bio (prefix match on username, text on bio).
     * Excludes banned users. Used specifically for the People page search.
     *
     * @param queryStr     search query
     * @param page         page number (0-indexed)
     * @param size         page size
     * @param viewerUserId authenticated viewer's user ID (nullable)
     * @return paginated search results
     */
    public Page<BuilderDiscoveryResponse> searchBuilders(String queryStr, int page, int size, String viewerUserId) {
        size = Math.min(Math.max(1, size), MAX_PAGE_SIZE);
        page = Math.max(0, page);

        Pageable pageable = PageRequest.of(page, size);

        // Regex search on username (prefix) OR bio (contains), case-insensitive
        // Exclude banned users
        String escapedQuery = java.util.regex.Pattern.quote(queryStr);
        Criteria searchCriteria = new Criteria().andOperator(
                Criteria.where("banned").is(false),
                new Criteria().orOperator(
                        Criteria.where("username").regex(escapedQuery, "i"),
                        Criteria.where("bio").regex(escapedQuery, "i")
                )
        );

        Query query = new Query(searchCriteria)
                .with(PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "shipScore").and(Sort.by(Sort.Direction.ASC, "id"))));

        long total = mongoTemplate.count(new Query(searchCriteria), User.class);
        List<User> users = mongoTemplate.find(query, User.class);

        Set<String> followingIds = getFollowingIds(viewerUserId, users);

        List<BuilderDiscoveryResponse> responses = users.stream()
                .map(user -> toResponse(user, followingIds.contains(user.getId()), viewerUserId))
                .collect(Collectors.toList());

        return new PageImpl<>(responses, pageable, total);
    }

    private Sort buildSort(DiscoverySort mode) {
        return switch (mode) {
            case DISCOVER -> Sort.by(
                    Sort.Order.desc("lastShipActivityAt"),
                    Sort.Order.desc("shipScore"),
                    Sort.Order.asc("id")
            );
            case TOP -> Sort.by(
                    Sort.Order.desc("shipScore"),
                    Sort.Order.asc("id")
            );
            case NEW -> Sort.by(
                    Sort.Order.desc("createdAt"),
                    Sort.Order.asc("id")
            );
        };
    }

    /**
     * Batch fetch isFollowing state for the viewer against a list of users.
     * Returns empty set for anonymous viewers — avoids N+1 queries.
     */
    private Set<String> getFollowingIds(String viewerUserId, List<User> users) {
        if (viewerUserId == null || viewerUserId.isBlank() || users.isEmpty()) {
            return Collections.emptySet();
        }

        Set<String> userIds = users.stream().map(User::getId).collect(Collectors.toSet());
        // Batch query: find all Following docs where followerId=viewer AND followeeId IN userIds
        Query followQuery = new Query(Criteria.where("followerId").is(viewerUserId)
                .and("followeeId").in(userIds));
        return mongoTemplate.find(followQuery, com.shipspace.model.Following.class)
                .stream()
                .map(com.shipspace.model.Following::getFolloweeId)
                .collect(Collectors.toSet());
    }

    private BuilderDiscoveryResponse toResponse(User user, boolean isFollowing, String viewerUserId) {
        // Don't show isFollowing=true for your own card
        Boolean followingState = null;
        if (viewerUserId != null && !viewerUserId.isBlank()) {
            followingState = viewerUserId.equals(user.getId()) ? null : isFollowing;
        }

        return BuilderDiscoveryResponse.builder()
                .id(user.getId())
                .username(user.getUsername())
                .avatarUrl(user.getAvatarUrl())
                .bio(user.getBio())
                .location(user.getLocation())
                .followerCount(user.getFollowerCount())
                .projectCount(user.getProjectCount())
                .shipScore(user.getShipScore())
                .level(user.getLevel())
                .rank(user.getRank() != null ? user.getRank().name() : null)
                .isFollowing(followingState)
                .createdAt(user.getCreatedAt())
                .build();
    }
}
