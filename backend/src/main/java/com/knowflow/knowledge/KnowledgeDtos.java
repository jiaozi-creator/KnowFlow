package com.knowflow.knowledge;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;
import java.util.List;

public final class KnowledgeDtos {

    private KnowledgeDtos() {
    }

    /**
     * 创建知识库。
     *
     * departmentIds：
     * visibility = DEPARTMENT 时使用。
     *
     * memberIds：
     * visibility = MEMBER 时使用。
     */
    public record CreateRequest(

            @NotBlank
            @Size(max = 120)
            String name,

            @Size(max = 500)
            String description,

            String visibility,

            List<Long> departmentIds,

            List<Long> memberIds
    ) {
    }

    /**
     * 修改知识库。
     */
    public record UpdateRequest(

            @NotBlank
            @Size(max = 120)
            String name,

            @Size(max = 500)
            String description,

            String visibility,

            List<Long> departmentIds,

            List<Long> memberIds
    ) {
    }

    /**
     * 前端知识库详情。
     */
    public record View(
            Long id,
            String name,
            String description,
            String visibility,
            Long createdBy,
            OffsetDateTime createdAt,
            OffsetDateTime updatedAt,
            List<Long> departmentIds,
            List<Long> memberIds
    ) {
    }
}