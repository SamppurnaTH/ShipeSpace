package com.shipspace.util;

import com.shipspace.dto.ProjectResponse;
import com.shipspace.dto.ShipResponse;
import com.shipspace.model.Project;
import com.shipspace.model.Ship;
import org.springframework.stereotype.Component;

/**
 * Utility for mapping entities to DTOs.
 * Centralizes DTO mapping logic to avoid duplication and ensure consistency.
 */
@Component
public class DtoMapper {

    /**
     * Maps a Ship entity to ShipResponse DTO.
     */
    public ShipResponse toShipResponse(Ship ship) {
        if (ship == null) return null;
        return ShipResponse.builder()
                .id(ship.getId())
                .userId(ship.getUserId())
                .projectId(ship.getProjectId())
                .content(ship.getContent())
                .images(ship.getImages())
                .externalLinks(ship.getExternalLinks())
                .likeCount(ship.getLikeCount())
                .commentCount(ship.getCommentCount())
                .trendingScore(ship.getTrendingScore())
                .editWindowOpen(ship.isEditWindowOpen())
                .editWindowExpires(ship.getEditWindowExpires())
                .createdAt(ship.getCreatedAt())
                .updatedAt(ship.getUpdatedAt())
                .build();
    }

    /**
     * Maps a Project entity to ProjectResponse DTO.
     */
    public ProjectResponse toProjectResponse(Project project) {
        if (project == null) return null;
        return ProjectResponse.builder()
                .id(project.getId())
                .userId(project.getUserId())
                .name(project.getName())
                .description(project.getDescription())
                .techTags(project.getTechTags())
                .externalLinks(project.getExternalLinks())
                .shipCount(project.getShipCount())
                .lastShipAt(project.getLastShipAt())
                .createdAt(project.getCreatedAt())
                .updatedAt(project.getUpdatedAt())
                .build();
    }
}
