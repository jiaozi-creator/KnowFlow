package com.knowflow.organization;

import com.knowflow.common.BusinessException;
import com.knowflow.security.SecurityUtils;
import com.knowflow.security.UserPrincipal;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DepartmentService {

    private final DepartmentMapper mapper;

    public DepartmentService(
            DepartmentMapper mapper
    ) {
        this.mapper = mapper;
    }

    /**
     * 查询当前企业的部门树。
     */
    public List<DepartmentDtos.View> tree() {

        UserPrincipal user =
                SecurityUtils.current();

        List<DepartmentEntity> departments =
                mapper.listByTenant(
                        user.tenantId()
                );

        return buildTree(
                departments
        );
    }

    /**
     * 创建部门。
     *
     * 第一版限制 OWNER / ADMIN。
     */
    public DepartmentDtos.View create(
            DepartmentDtos.CreateRequest request
    ) {

        requireAdmin();

        UserPrincipal user =
                SecurityUtils.current();

        Long parentId =
                request.parentId();

        if (
                parentId != null
        ) {
            require(
                    parentId
            );
        }

        DepartmentEntity entity =
                new DepartmentEntity();

        entity.setTenantId(
                user.tenantId()
        );

        entity.setParentId(
                parentId
        );

        entity.setName(
                request.name().trim()
        );

        entity.setSortOrder(
                request.sortOrder() == null
                        ? 0
                        : request.sortOrder()
        );

        mapper.insert(
                entity
        );

        return toView(
                mapper.selectById(
                        entity.getId()
                ),
                List.of()
        );
    }

    /**
     * 修改部门。
     */
    public DepartmentDtos.View update(
            Long id,
            DepartmentDtos.UpdateRequest request
    ) {

        requireAdmin();

        DepartmentEntity entity =
                require(id);

        Long parentId =
                request.parentId();

        /*
         * 部门不能把自己设为父部门。
         */
        if (
                parentId != null
                        && parentId.equals(id)
        ) {

            throw BusinessException.badRequest(
                    "部门不能作为自己的上级部门"
            );
        }

        /*
         * 新父部门必须属于当前租户。
         */
        if (
                parentId != null
        ) {

            require(
                    parentId
            );

            /*
             * 防止形成：
             *
             * A
             * └── B
             *     └── A
             *
             * 这样的循环结构。
             */
            if (
                    isDescendant(
                            parentId,
                            id
                    )
            ) {

                throw BusinessException.badRequest(
                        "不能将部门移动到自己的子部门下"
                );
            }
        }

        entity.setName(
                request.name().trim()
        );

        entity.setParentId(
                parentId
        );

        entity.setSortOrder(
                request.sortOrder() == null
                        ? entity.getSortOrder()
                        : request.sortOrder()
        );

        entity.setUpdatedAt(
                OffsetDateTime.now()
        );

        mapper.updateById(
                entity
        );

        return toView(
                mapper.selectById(id),
                List.of()
        );
    }

    /**
     * 删除部门。
     *
     * 当前版本：
     * 有子部门时禁止删除。
     *
     * 后续成员模块完成后，
     * 还会增加：
     *
     * 有成员时禁止删除。
     */
    public void delete(
            Long id
    ) {

        requireAdmin();

        DepartmentEntity entity =
                require(id);

        int childCount =
                mapper.countChildren(
                        entity.getTenantId(),
                        id
                );

        if (
                childCount > 0
        ) {

            throw BusinessException.badRequest(
                    "请先删除或移动该部门下的子部门"
            );
        }

        mapper.deleteById(
                id
        );
    }

    /**
     * 获取并校验当前租户部门。
     */
    public DepartmentEntity require(
            Long id
    ) {

        UserPrincipal user =
                SecurityUtils.current();

        DepartmentEntity entity =
                mapper.findByIdAndTenant(
                        id,
                        user.tenantId()
                );

        if (
                entity == null
        ) {

            throw BusinessException.notFound(
                    "部门不存在"
            );
        }

        return entity;
    }

    /**
     * 是否为目标节点的后代。
     */
    private boolean isDescendant(
            Long possibleChildId,
            Long ancestorId
    ) {

        DepartmentEntity current =
                mapper.findByIdAndTenant(
                        possibleChildId,
                        SecurityUtils.current().tenantId()
                );

        while (
                current != null
                        && current.getParentId() != null
        ) {

            if (
                    current.getParentId()
                            .equals(
                                    ancestorId
                            )
            ) {

                return true;
            }

            current =
                    mapper.findByIdAndTenant(
                            current.getParentId(),
                            SecurityUtils.current().tenantId()
                    );
        }

        return false;
    }

    /**
     * 将扁平部门列表组装成树。
     */
    private List<DepartmentDtos.View> buildTree(
            List<DepartmentEntity> departments
    ) {

        Map<Long, List<DepartmentEntity>> byParent =
                departments
                        .stream()
                        .collect(
                                Collectors.groupingBy(
                                        entity ->
                                                entity.getParentId() == null
                                                        ? 0L
                                                        : entity.getParentId()
                                )
                        );

        return buildChildren(
                0L,
                byParent
        );
    }

    private List<DepartmentDtos.View> buildChildren(
            Long parentId,
            Map<Long, List<DepartmentEntity>> byParent
    ) {

        List<DepartmentEntity> children =
                new ArrayList<>(
                        byParent.getOrDefault(
                                parentId,
                                List.of()
                        )
                );

        children.sort(
                Comparator
                        .comparing(
                                DepartmentEntity::getSortOrder
                        )
                        .thenComparing(
                                DepartmentEntity::getId
                        )
        );

        return children
                .stream()
                .map(
                        entity ->
                                toView(
                                        entity,
                                        buildChildren(
                                                entity.getId(),
                                                byParent
                                        )
                                )
                )
                .toList();
    }

    private DepartmentDtos.View toView(
            DepartmentEntity entity,
            List<DepartmentDtos.View> children
    ) {

        return new DepartmentDtos.View(
                entity.getId(),
                entity.getParentId(),
                entity.getName(),
                entity.getSortOrder(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                children
        );
    }

    private void requireAdmin() {

        if (
                !SecurityUtils.isTenantAdmin()
        ) {

            throw BusinessException.forbidden(
                    "只有企业管理员可以管理部门"
            );
        }
    }
}