CREATE TABLE department (
    id BIGSERIAL PRIMARY KEY,

    tenant_id BIGINT NOT NULL
        REFERENCES organization(id)
        ON DELETE CASCADE,

    parent_id BIGINT
        REFERENCES department(id)
        ON DELETE SET NULL,

    name VARCHAR(120) NOT NULL,

    sort_order INT NOT NULL DEFAULT 0,

    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),

    UNIQUE (tenant_id, parent_id, name)
);

CREATE INDEX idx_department_tenant
    ON department(tenant_id);

CREATE INDEX idx_department_parent
    ON department(tenant_id, parent_id);