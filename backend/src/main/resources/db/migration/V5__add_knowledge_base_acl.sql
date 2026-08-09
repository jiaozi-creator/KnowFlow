-- ============================================================
-- Knowledge Base ACL
-- ============================================================

-- 指定部门可以访问某个知识库
CREATE TABLE knowledge_base_department_acl (
    id BIGSERIAL PRIMARY KEY,

    tenant_id BIGINT NOT NULL
        REFERENCES organization(id)
        ON DELETE CASCADE,

    knowledge_base_id BIGINT NOT NULL
        REFERENCES knowledge_base(id)
        ON DELETE CASCADE,

    department_id BIGINT NOT NULL
        REFERENCES department(id)
        ON DELETE CASCADE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (
        knowledge_base_id,
        department_id
    )
);

CREATE INDEX idx_kb_department_acl_lookup
ON knowledge_base_department_acl (
    tenant_id,
    knowledge_base_id,
    department_id
);


-- 指定成员可以访问某个知识库
CREATE TABLE knowledge_base_member_acl (
    id BIGSERIAL PRIMARY KEY,

    tenant_id BIGINT NOT NULL
        REFERENCES organization(id)
        ON DELETE CASCADE,

    knowledge_base_id BIGINT NOT NULL
        REFERENCES knowledge_base(id)
        ON DELETE CASCADE,

    organization_member_id BIGINT NOT NULL
        REFERENCES organization_member(id)
        ON DELETE CASCADE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (
        knowledge_base_id,
        organization_member_id
    )
);

CREATE INDEX idx_kb_member_acl_lookup
ON knowledge_base_member_acl (
    tenant_id,
    knowledge_base_id,
    organization_member_id
);


-- 防止历史数据中出现非法 visibility
ALTER TABLE knowledge_base
ADD CONSTRAINT chk_knowledge_base_visibility
CHECK (
    visibility IN (
        'TENANT',
        'DEPARTMENT',
        'MEMBER',
        'PRIVATE'
    )
);