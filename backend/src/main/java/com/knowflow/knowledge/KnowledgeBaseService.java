package com.knowflow.knowledge;

import com.knowflow.auth.OrganizationMemberEntity;
import com.knowflow.auth.OrganizationMemberMapper;
import com.knowflow.common.BusinessException;
import com.knowflow.organization.DepartmentService;
import com.knowflow.security.SecurityUtils;
import com.knowflow.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class KnowledgeBaseService {

    private static final Set<String> VISIBILITIES =
            Set.of(
                    "TENANT",
                    "DEPARTMENT",
                    "MEMBER",
                    "PRIVATE"
            );

    private final KnowledgeBaseMapper mapper;

    private final KnowledgeBaseDepartmentAclMapper departmentAclMapper;

    private final KnowledgeBaseMemberAclMapper memberAclMapper;

    private final DepartmentService departmentService;

    private final OrganizationMemberMapper organizationMemberMapper;

    public KnowledgeBaseService(
            KnowledgeBaseMapper mapper,
            KnowledgeBaseDepartmentAclMapper departmentAclMapper,
            KnowledgeBaseMemberAclMapper memberAclMapper,
            DepartmentService departmentService,
            OrganizationMemberMapper organizationMemberMapper
    ) {

        this.mapper = mapper;

        this.departmentAclMapper =
                departmentAclMapper;

        this.memberAclMapper =
                memberAclMapper;

        this.departmentService =
                departmentService;

        this.organizationMemberMapper =
                organizationMemberMapper;
    }

    /**
     * 当前用户可见知识库列表。
     */
    public List<KnowledgeDtos.View> list() {

        UserPrincipal user =
                SecurityUtils.current();

        List<KnowledgeBaseEntity> entities;

        /*
         * OWNER / ADMIN：
         * 企业内全部知识库。
         */
        if (
                SecurityUtils.isTenantAdmin()
        ) {

            entities =
                    mapper.listByTenant(
                            user.tenantId()
                    );

        } else {

            /*
             * 普通成员：
             * 只查询经过 ACL 判断后可见的数据。
             */
            entities =
                    mapper.listAccessible(
                            user.tenantId(),
                            user.userId()
                    );
        }

        return entities
                .stream()
                .map(this::toView)
                .toList();
    }

    /**
     * 获取单个知识库。
     */
    public KnowledgeDtos.View get(
            Long id
    ) {

        return toView(
                require(id)
        );
    }

    /**
     * 创建知识库。
     */
    @Transactional
    public KnowledgeDtos.View create(
            KnowledgeDtos.CreateRequest request
    ) {

        UserPrincipal user =
                SecurityUtils.current();

        String visibility =
                normalizeVisibility(
                        request.visibility()
                );

        validateAcl(
                visibility,
                request.departmentIds(),
                request.memberIds(),
                user
        );

        KnowledgeBaseEntity entity =
                new KnowledgeBaseEntity();

        entity.setTenantId(
                user.tenantId()
        );

        entity.setName(
                request
                        .name()
                        .trim()
        );

        entity.setDescription(
                request.description()
        );

        entity.setVisibility(
                visibility
        );

        entity.setCreatedBy(
                user.userId()
        );

        mapper.insert(
                entity
        );

        saveAcl(
                entity.getId(),
                visibility,
                request.departmentIds(),
                request.memberIds(),
                user
        );

        return toView(
                mapper.findByIdAndTenant(
                        entity.getId(),
                        user.tenantId()
                )
        );
    }

    /**
     * 修改知识库。
     */
    @Transactional
    public KnowledgeDtos.View update(
            Long id,
            KnowledgeDtos.UpdateRequest request
    ) {

        UserPrincipal user =
                SecurityUtils.current();

        KnowledgeBaseEntity entity =
                requireManage(
                        id
                );

        String visibility =
                request.visibility() == null
                        ? entity.getVisibility()
                        : normalizeVisibility(
                                request.visibility()
                        );

        validateAcl(
                visibility,
                request.departmentIds(),
                request.memberIds(),
                user
        );

        entity.setName(
                request
                        .name()
                        .trim()
        );

        entity.setDescription(
                request.description()
        );

        entity.setVisibility(
                visibility
        );

        entity.setUpdatedAt(
                OffsetDateTime.now()
        );

        mapper.updateById(
                entity
        );

        /*
         * 每次修改 ACL 都先清理旧权限，
         * 避免 TENANT → DEPARTMENT 等切换后残留旧数据。
         */
        clearAcl(
                user.tenantId(),
                id
        );

        saveAcl(
                id,
                visibility,
                request.departmentIds(),
                request.memberIds(),
                user
        );

        return toView(
                mapper.findByIdAndTenant(
                        id,
                        user.tenantId()
                )
        );
    }

    /**
     * 删除知识库。
     *
     * ACL 表使用 ON DELETE CASCADE，
     * 因此知识库删除时 ACL 自动清理。
     */
    @Transactional
    public void delete(
            Long id
    ) {

        requireManage(
                id
        );

        mapper.deleteById(
                id
        );
    }

    /**
     * ============================================================
     * 知识库访问权限判断
     * ============================================================
     *
     * 这是整个系统统一入口。
     *
     * DocumentService / ChatService
     * 调用 require() 后都会自动获得 ACL 保护。
     */
    public KnowledgeBaseEntity require(
            Long id
    ) {

        UserPrincipal user =
                SecurityUtils.current();

        KnowledgeBaseEntity entity;

        /*
         * OWNER / ADMIN：
         * 当前租户内全部允许访问。
         */
        if (
                SecurityUtils.isTenantAdmin()
        ) {

            entity =
                    mapper.findByIdAndTenant(
                            id,
                            user.tenantId()
                    );

        } else {

            /*
             * 普通成员：
             * SQL 中直接进行 ACL 判断。
             */
            entity =
                    mapper.findAccessible(
                            id,
                            user.tenantId(),
                            user.userId()
                    );
        }

        if (
                entity == null
        ) {

            /*
             * 不向无权限用户区分：
             *
             * “资源不存在”
             * 和
             * “存在但无权限”
             *
             * 避免知识库 ID 枚举。
             */
            throw BusinessException.notFound(
                    "知识库不存在或无权访问"
            );
        }

        return entity;
    }

    /**
     * 管理权限：
     *
     * OWNER / ADMIN
     * 或
     * 知识库创建者。
     */
    public KnowledgeBaseEntity requireManage(
            Long id
    ) {

        UserPrincipal user =
                SecurityUtils.current();

        KnowledgeBaseEntity entity =
                mapper.findByIdAndTenant(
                        id,
                        user.tenantId()
                );

        if (
                entity == null
        ) {

            throw BusinessException.notFound(
                    "知识库不存在"
            );
        }

        if (
                !SecurityUtils.isTenantAdmin()
                        && !entity
                        .getCreatedBy()
                        .equals(
                                user.userId()
                        )
        ) {

            throw BusinessException.forbidden(
                    "无权管理该知识库"
            );
        }

        return entity;
    }

    /**
     * ============================================================
     * ACL 参数验证
     * ============================================================
     */
    private void validateAcl(
            String visibility,
            List<Long> departmentIds,
            List<Long> memberIds,
            UserPrincipal user
    ) {

        if (
                "DEPARTMENT".equals(
                        visibility
                )
        ) {

            List<Long> ids =
                    distinctIds(
                            departmentIds
                    );

            if (
                    ids.isEmpty()
            ) {

                throw BusinessException.badRequest(
                        "部门可见知识库必须至少选择一个部门"
                );
            }

            /*
             * DepartmentService.require()
             * 已经带 tenantId 验证。
             */
            for (
                    Long departmentId :
                    ids
            ) {

                departmentService.require(
                        departmentId
                );
            }
        }

        if (
                "MEMBER".equals(
                        visibility
                )
        ) {

            List<Long> ids =
                    distinctIds(
                            memberIds
                    );

            if (
                    ids.isEmpty()
            ) {

                throw BusinessException.badRequest(
                        "指定成员知识库必须至少选择一个成员"
                );
            }

            for (
                    Long memberId :
                    ids
            ) {

                OrganizationMemberEntity member =
                        organizationMemberMapper
                                .findByIdAndOrganization(
                                        memberId,
                                        user.tenantId()
                                );

                if (
                        member == null
                ) {

                    throw BusinessException.badRequest(
                            "存在不属于当前企业的成员"
                    );
                }
            }
        }
    }

    /**
     * 保存 ACL。
     */
    private void saveAcl(
            Long knowledgeBaseId,
            String visibility,
            List<Long> departmentIds,
            List<Long> memberIds,
            UserPrincipal user
    ) {

        if (
                "DEPARTMENT".equals(
                        visibility
                )
        ) {

            for (
                    Long departmentId :
                    distinctIds(
                            departmentIds
                    )
            ) {

                departmentAclMapper.insert(
                        user.tenantId(),
                        knowledgeBaseId,
                        departmentId
                );
            }
        }

        if (
                "MEMBER".equals(
                        visibility
                )
        ) {

            for (
                    Long memberId :
                    distinctIds(
                            memberIds
                    )
            ) {

                memberAclMapper.insert(
                        user.tenantId(),
                        knowledgeBaseId,
                        memberId
                );
            }
        }
    }

    /**
     * 清理知识库现有 ACL。
     */
    private void clearAcl(
            Long tenantId,
            Long knowledgeBaseId
    ) {

        departmentAclMapper
                .deleteByKnowledgeBase(
                        tenantId,
                        knowledgeBaseId
                );

        memberAclMapper
                .deleteByKnowledgeBase(
                        tenantId,
                        knowledgeBaseId
                );
    }

    /**
     * KnowledgeBaseEntity → View。
     */
    private KnowledgeDtos.View toView(
            KnowledgeBaseEntity entity
    ) {

        if (
                entity == null
        ) {

            return null;
        }

        Long tenantId =
                entity.getTenantId();

        Long knowledgeBaseId =
                entity.getId();

        List<Long> departmentIds =
                "DEPARTMENT".equals(
                        entity.getVisibility()
                )
                        ? departmentAclMapper
                        .listDepartmentIds(
                                tenantId,
                                knowledgeBaseId
                        )
                        : Collections.emptyList();

        List<Long> memberIds =
                "MEMBER".equals(
                        entity.getVisibility()
                )
                        ? memberAclMapper
                        .listMemberIds(
                                tenantId,
                                knowledgeBaseId
                        )
                        : Collections.emptyList();

        return new KnowledgeDtos.View(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getVisibility(),
                entity.getCreatedBy(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                departmentIds,
                memberIds
        );
    }

    /**
     * visibility 标准化。
     */
    private String normalizeVisibility(
            String visibility
    ) {

        String value =
                visibility == null
                        || visibility.isBlank()
                        ? "TENANT"
                        : visibility
                        .trim()
                        .toUpperCase(
                                Locale.ROOT
                        );

        if (
                !VISIBILITIES.contains(
                        value
                )
        ) {

            throw BusinessException.badRequest(
                    "知识库可见范围只能是 TENANT、DEPARTMENT、MEMBER 或 PRIVATE"
            );
        }

        return value;
    }

    /**
     * 去重并过滤 null。
     */
    private List<Long> distinctIds(
            List<Long> ids
    ) {

        if (
                ids == null
                        || ids.isEmpty()
        ) {

            return List.of();
        }

        LinkedHashSet<Long> result =
                new LinkedHashSet<>();

        for (
                Long id :
                ids
        ) {

            if (
                    id != null
            ) {

                result.add(
                        id
                );
            }
        }

        return result
                .stream()
                .toList();
    }
}