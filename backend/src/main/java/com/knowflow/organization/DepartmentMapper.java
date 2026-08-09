package com.knowflow.organization;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface DepartmentMapper
        extends BaseMapper<DepartmentEntity> {

    @Select("""
            select *
            from department
            where tenant_id = #{tenantId}
            order by sort_order asc, id asc
            """)
    List<DepartmentEntity> listByTenant(
            Long tenantId
    );

    @Select("""
            select *
            from department
            where id = #{id}
              and tenant_id = #{tenantId}
            """)
    DepartmentEntity findByIdAndTenant(
            Long id,
            Long tenantId
    );

    @Select("""
            select count(*)
            from department
            where tenant_id = #{tenantId}
              and parent_id = #{parentId}
            """)
    int countChildren(
            Long tenantId,
            Long parentId
    );
}