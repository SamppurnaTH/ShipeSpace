package com.shipspace.util;

import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.ShipResponse;
import com.shipspace.model.ExternalLink;
import com.shipspace.model.ImageReference;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for DtoMapper — no Spring context, no mocking.
 *
 * These tests exist specifically to catch mapping regressions that are
 * invisible in controller tests because those tests mock DtoMapper entirely.
 * If a field is added to Ship/Project but not to the mapper, these tests fail.
 */
@DisplayName("DtoMapper Unit Tests")
class DtoMapperTest {

    private DtoMapper mapper;

    @BeforeEach
    void setUp() {
        mapper = new DtoMapper();
    }

    // ========== toShipResponse ==========

    @Test
    @DisplayName("toShipResponse maps every Ship field to ShipResponse")
    void toShipResponse_mapsAllFields() {
        Instant now = Instant.now();
        Instant expires = now.plusSeconds(600);

        ImageReference image = ImageReference.builder()
                .multibaseHash("abc123")
                .filename("photo.jpg")
                .contentType("image/jpeg")
                .size(2048L)
                .build();

        ExternalLink link = ExternalLink.builder()
                .type("github")
                .url("https://github.com/org/repo")
                .title("Source Code")
                .description("Project source code")
                .build();

        Ship ship = Ship.builder()
                .id("ship-1")
                .userId("user-1")
                .projectId("proj-1")
                .content("My first ship!")
                .images(List.of(image))
                .externalLinks(List.of(link))
                .likeCount(5)
                .commentCount(3)
                .trendingScore(7.5)
                .editWindowExpires(expires)
                .createdAt(now)
                .updatedAt(now)
                .build();

        ShipResponse response = mapper.toShipResponse(ship);

        assertThat(response.getId()).isEqualTo("ship-1");
        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getProjectId()).isEqualTo("proj-1");
        assertThat(response.getContent()).isEqualTo("My first ship!");
        assertThat(response.getLikeCount()).isEqualTo(5);
        assertThat(response.getCommentCount()).isEqualTo(3);
        assertThat(response.getTrendingScore()).isEqualTo(7.5);
        assertThat(response.getEditWindowExpires()).isEqualTo(expires);
        assertThat(response.getCreatedAt()).isEqualTo(now);
        assertThat(response.getUpdatedAt()).isEqualTo(now);
        assertThat(response.getImages()).hasSize(1);
        assertThat(response.getImages().get(0).getMultibaseHash()).isEqualTo("abc123");
        assertThat(response.getImages().get(0).getFilename()).isEqualTo("photo.jpg");
        assertThat(response.getImages().get(0).getContentType()).isEqualTo("image/jpeg");
        assertThat(response.getImages().get(0).getSize()).isEqualTo(2048L);
        assertThat(response.getExternalLinks()).hasSize(1);
        assertThat(response.getExternalLinks().get(0).getUrl()).isEqualTo("https://github.com/org/repo");
        assertThat(response.getExternalLinks().get(0).getTitle()).isEqualTo("Source Code");
    }

    @Test
    @DisplayName("toShipResponse returns null for null input")
    void toShipResponse_null_returnsNull() {
        assertThat(mapper.toShipResponse(null)).isNull();
    }

    @Test
    @DisplayName("toShipResponse editWindowOpen is true when expiry is in the future")
    void toShipResponse_editWindowOpen_trueWhenExpiryInFuture() {
        Ship ship = Ship.builder()
                .id("s1").userId("u1").projectId("p1")
                .editWindowExpires(Instant.now().plusSeconds(300))
                .build();

        ShipResponse response = mapper.toShipResponse(ship);

        assertThat(response.getEditWindowOpen()).isTrue();
    }

    @Test
    @DisplayName("toShipResponse editWindowOpen is false when expiry is in the past")
    void toShipResponse_editWindowOpen_falseWhenExpiryInPast() {
        Ship ship = Ship.builder()
                .id("s1").userId("u1").projectId("p1")
                .editWindowExpires(Instant.now().minusSeconds(60))
                .build();

        ShipResponse response = mapper.toShipResponse(ship);

        assertThat(response.getEditWindowOpen()).isFalse();
    }

    @Test
    @DisplayName("toShipResponse editWindowOpen is false when expiry is null")
    void toShipResponse_editWindowOpen_falseWhenExpiryNull() {
        Ship ship = Ship.builder()
                .id("s1").userId("u1").projectId("p1")
                .editWindowExpires(null)
                .build();

        ShipResponse response = mapper.toShipResponse(ship);

        assertThat(response.getEditWindowOpen()).isFalse();
    }

    @Test
    @DisplayName("toShipResponse preserves the same image and link list instances")
    void toShipResponse_preservesListReferences() {
        List<ImageReference> images = List.of(ImageReference.builder().multibaseHash("h1").build());
        List<ExternalLink> links = List.of(ExternalLink.builder().url("https://x.com").build());

        Ship ship = Ship.builder()
                .id("s1").userId("u1").projectId("p1")
                .images(images)
                .externalLinks(links)
                .build();

        ShipResponse response = mapper.toShipResponse(ship);

        assertThat(response.getImages()).isSameAs(images);
        assertThat(response.getExternalLinks()).isSameAs(links);
    }

    // ========== toProjectResponse ==========

    @Test
    @DisplayName("toProjectResponse maps every Project field to ProjectResponse")
    void toProjectResponse_mapsAllFields() {
        Instant now = Instant.now();
        Instant lastShip = now.minusSeconds(3600);

        ExternalLink link = ExternalLink.builder()
                .type("demo")
                .url("https://demo.example.com")
                .title("Live Demo")
                .description("Interactive demo")
                .build();

        Project project = Project.builder()
                .id("proj-1")
                .userId("user-1")
                .name("ShipSpace")
                .description("A platform for builders")
                .techTags(List.of("Java", "Spring", "MongoDB"))
                .externalLinks(List.of(link))
                .shipCount(7)
                .lastShipAt(lastShip)
                .createdAt(now)
                .updatedAt(now)
                .build();

        ProjectResponse response = mapper.toProjectResponse(project);

        assertThat(response.getId()).isEqualTo("proj-1");
        assertThat(response.getUserId()).isEqualTo("user-1");
        assertThat(response.getName()).isEqualTo("ShipSpace");
        assertThat(response.getDescription()).isEqualTo("A platform for builders");
        assertThat(response.getTechTags()).containsExactly("Java", "Spring", "MongoDB");
        assertThat(response.getShipCount()).isEqualTo(7);
        assertThat(response.getLastShipAt()).isEqualTo(lastShip);
        assertThat(response.getCreatedAt()).isEqualTo(now);
        assertThat(response.getUpdatedAt()).isEqualTo(now);
        assertThat(response.getExternalLinks()).hasSize(1);
        assertThat(response.getExternalLinks().get(0).getUrl()).isEqualTo("https://demo.example.com");
        assertThat(response.getExternalLinks().get(0).getTitle()).isEqualTo("Live Demo");
    }

    @Test
    @DisplayName("toProjectResponse returns null for null input")
    void toProjectResponse_null_returnsNull() {
        assertThat(mapper.toProjectResponse(null)).isNull();
    }

    @Test
    @DisplayName("toProjectResponse preserves techTags and externalLinks list references")
    void toProjectResponse_preservesListReferences() {
        List<String> tags = List.of("Rust", "WASM");
        List<ExternalLink> links = List.of(ExternalLink.builder().url("https://y.com").build());

        Project project = Project.builder()
                .id("p1").userId("u1").name("Test").description("A description")
                .techTags(tags)
                .externalLinks(links)
                .build();

        ProjectResponse response = mapper.toProjectResponse(project);

        assertThat(response.getTechTags()).isSameAs(tags);
        assertThat(response.getExternalLinks()).isSameAs(links);
    }
}
