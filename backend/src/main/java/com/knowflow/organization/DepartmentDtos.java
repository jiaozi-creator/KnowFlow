package com.knowflow.organization;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public final class DepartmentDtos {

    private DepartmentDtos() {
    }

    public record CreateRequest(
            @NotBlank
            @Size(max = 120)
            String name,

            Long parentId,

            Integer sortOrder
    ) {
    }

    public record UpdateRequest(
            @NotBlank
            @Size(max = 120)
            String name,

            Long parentId,

            Integer sortOrder
    ) {
    }

    public record View(
            Long id,
            Long parentId,
            String name,
            Integer sortOrder,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<View> children
    ) {
    }
}