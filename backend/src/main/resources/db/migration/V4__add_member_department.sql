ALTER TABLE organization_member
ADD COLUMN department_id BIGINT;

ALTER TABLE organization_member
ADD CONSTRAINT fk_organization_member_department
FOREIGN KEY (department_id)
REFERENCES department(id)
ON DELETE SET NULL;

CREATE INDEX idx_organization_member_department
ON organization_member(
    organization_id,
    department_id
);