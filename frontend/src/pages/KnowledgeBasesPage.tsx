import {
  ApartmentOutlined,
  DeleteOutlined,
  EditOutlined,
  FileTextOutlined,
  LockOutlined,
  PlusOutlined,
  TeamOutlined,
  UserOutlined,
} from '@ant-design/icons'
import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  Col,
  Empty,
  Form,
  Input,
  Modal,
  Popconfirm,
  Radio,
  Row,
  Select,
  Space,
  Spin,
  Tag,
  TreeSelect,
  Typography,
} from 'antd'
import {
  useMemo,
  useState,
} from 'react'
import {
  useNavigate,
} from 'react-router-dom'

import { api } from '../api/client'
import type {
  KnowledgeBase,
} from '../types'

interface Department {
  id: number
  parentId: number | null
  name: string
  sortOrder: number
  createdAt: string
  updatedAt: string
  children: Department[]
}

interface Member {
  membershipId: number
  userId: number
  email: string
  displayName: string
  status: string
  role: string
  departmentId: number | null
  departmentName: string | null
  createdAt: string
}

interface KnowledgeBaseFormValues {
  name: string
  description?: string

  visibility:
    | 'TENANT'
    | 'DEPARTMENT'
    | 'MEMBER'
    | 'PRIVATE'

  departmentIds?: number[]
  memberIds?: number[]
}

export function KnowledgeBasesPage() {
  const navigate =
    useNavigate()

  const queryClient =
    useQueryClient()

  const { message } =
    App.useApp()

  const [form] =
    Form.useForm<KnowledgeBaseFormValues>()

  const [
    modalOpen,
    setModalOpen,
  ] = useState(false)

  const [
    editing,
    setEditing,
  ] =
    useState<KnowledgeBase | null>(
      null,
    )

  /**
   * 当前 Modal 中选择的 visibility。
   */
  const visibility =
    Form.useWatch(
      'visibility',
      form,
    )

  /**
   * ============================================================
   * Knowledge Base
   * ============================================================
   */
  const {
    data: knowledgeBases = [],
    isLoading,
  } = useQuery({
    queryKey: [
      'knowledge-bases',
    ],

    queryFn: () =>
      api<KnowledgeBase[]>(
        '/api/knowledge-bases',
      ),
  })

  /**
   * ============================================================
   * Department
   * ============================================================
   */
  const {
    data: departments = [],
  } = useQuery({
    queryKey: [
      'departments',
    ],

    queryFn: () =>
      api<Department[]>(
        '/api/departments',
      ),
  })

  /**
   * ============================================================
   * Members
   * ============================================================
   */
  const {
    data: members = [],
  } = useQuery({
    queryKey: [
      'members',
    ],

    queryFn: () =>
      api<Member[]>(
        '/api/members',
      ),
  })

  /**
   * Department TreeSelect。
   */
  const departmentTreeData =
    useMemo(() => {
      const convert = (
        list: Department[],
      ): any[] =>
        list.map(
          (department) => ({
            title:
              department.name,

            value:
              department.id,

            key:
              department.id,

            children:
              convert(
                department.children ??
                  [],
              ),
          }),
        )

      return convert(
        departments,
      )
    }, [departments])

  /**
   * Member Select。
   *
   * 注意：
   * 后端 ACL 保存的是：
   *
   * organization_member.id
   *
   * 所以前端必须发送 membershipId，
   * 不是 userId。
   */
  const memberOptions =
    useMemo(
      () =>
        members.map(
          (member) => ({
            value:
              member.membershipId,

            label: `${member.displayName} · ${
              member.departmentName ??
              '未分配部门'
            } · ${member.email}`,
          }),
        ),
      [members],
    )

  /**
   * ============================================================
   * Create
   * ============================================================
   */
  const createMutation =
    useMutation({
      mutationFn: (
        values:
          KnowledgeBaseFormValues,
      ) =>
        api<KnowledgeBase>(
          '/api/knowledge-bases',
          {
            method: 'POST',

            body: JSON.stringify(
              normalizePayload(
                values,
              ),
            ),
          },
        ),

      onSuccess: async () => {
        message.success(
          '知识库已创建',
        )

        closeModal()

        await queryClient.invalidateQueries(
          {
            queryKey: [
              'knowledge-bases',
            ],
          },
        )
      },

      onError: (
        error: Error,
      ) => {
        message.error(
          error.message,
        )
      },
    })

  /**
   * ============================================================
   * Update
   * ============================================================
   */
  const updateMutation =
    useMutation({
      mutationFn: (
        values:
          KnowledgeBaseFormValues,
      ) => {
        if (!editing) {
          throw new Error(
            '缺少知识库信息',
          )
        }

        return api<KnowledgeBase>(
          `/api/knowledge-bases/${editing.id}`,
          {
            method: 'PUT',

            body: JSON.stringify(
              normalizePayload(
                values,
              ),
            ),
          },
        )
      },

      onSuccess: async () => {
        message.success(
          '知识库已更新',
        )

        closeModal()

        await queryClient.invalidateQueries(
          {
            queryKey: [
              'knowledge-bases',
            ],
          },
        )
      },

      onError: (
        error: Error,
      ) => {
        message.error(
          error.message,
        )
      },
    })

  /**
   * ============================================================
   * Delete
   * ============================================================
   */
  const removeMutation =
    useMutation({
      mutationFn: (
        id: number,
      ) =>
        api<void>(
          `/api/knowledge-bases/${id}`,
          {
            method: 'DELETE',
          },
        ),

      onSuccess: async () => {
        message.success(
          '知识库已删除',
        )

        await queryClient.invalidateQueries(
          {
            queryKey: [
              'knowledge-bases',
            ],
          },
        )
      },

      onError: (
        error: Error,
      ) => {
        message.error(
          error.message,
        )
      },
    })

  /**
   * 新建知识库。
   */
  const openCreate = () => {
    setEditing(null)

    form.resetFields()

    form.setFieldsValue({
      visibility: 'TENANT',
      departmentIds: [],
      memberIds: [],
    })

    setModalOpen(true)
  }

  /**
   * 编辑已有知识库。
   */
  const openEdit = (
    knowledgeBase:
      KnowledgeBase,
  ) => {
    setEditing(
      knowledgeBase,
    )

    form.resetFields()

    form.setFieldsValue({
      name:
        knowledgeBase.name,

      description:
        knowledgeBase.description,

      visibility:
        knowledgeBase.visibility,

      departmentIds:
        knowledgeBase.departmentIds ??
        [],

      memberIds:
        knowledgeBase.memberIds ??
        [],
    })

    setModalOpen(true)
  }

  const closeModal = () => {
    setModalOpen(false)

    setEditing(null)

    form.resetFields()
  }

  /**
   * 保存。
   */
  const handleSave =
    async () => {
      const values =
        await form.validateFields()

      if (editing) {
        updateMutation.mutate(
          values,
        )
      } else {
        createMutation.mutate(
          values,
        )
      }
    }

  const saving =
    createMutation.isPending ||
    updateMutation.isPending

  return (
    <div className="knowledge-bases-page">
      <div className="page-heading">
        <div>
          <Typography.Title
            level={2}
          >
            知识库
          </Typography.Title>

          <Typography.Text
            type="secondary"
          >
            集中管理企业制度、
            产品资料和技术文档，
            并配置部门及成员访问权限。
          </Typography.Text>
        </div>

        <Button
          type="primary"
          icon={
            <PlusOutlined />
          }
          onClick={
            openCreate
          }
        >
          新建知识库
        </Button>
      </div>

      {isLoading ? (
        <div className="knowledge-loading">
          <Spin />
        </div>
      ) : knowledgeBases.length ===
        0 ? (
        <Empty
          description="尚未创建知识库"
        >
          <Button
            type="primary"
            icon={
              <PlusOutlined />
            }
            onClick={
              openCreate
            }
          >
            创建第一个知识库
          </Button>
        </Empty>
      ) : (
        <Row
          gutter={[
            16,
            16,
          ]}
        >
          {knowledgeBases.map(
            (item) => (
              <Col
                xs={24}
                md={12}
                xl={8}
                key={item.id}
              >
                <Card
                  hoverable
                  className="knowledge-base-card"
                  title={
                    item.name
                  }
                  extra={
                    <Space size={0}>
                      <Button
                        type="text"
                        icon={
                          <EditOutlined />
                        }
                        onClick={(
                          event,
                        ) => {
                          event.stopPropagation()

                          openEdit(
                            item,
                          )
                        }}
                      />

                      <Popconfirm
                        title="确认删除知识库？"
                        description="删除后，该知识库中的文档也将被删除。"
                        okText="删除"
                        cancelText="取消"
                        okButtonProps={{
                          danger: true,
                        }}
                        onConfirm={() =>
                          removeMutation.mutate(
                            item.id,
                          )
                        }
                      >
                        <Button
                          type="text"
                          danger
                          icon={
                            <DeleteOutlined />
                          }
                          onClick={(
                            event,
                          ) =>
                            event.stopPropagation()
                          }
                        />
                      </Popconfirm>
                    </Space>
                  }
                  onClick={() =>
                    navigate(
                      `/app/knowledge-bases/${item.id}/documents`,
                    )
                  }
                >
                  <Typography.Paragraph
                    ellipsis={{
                      rows: 2,
                    }}
                    className="knowledge-description"
                  >
                    {item.description ||
                      '暂无说明'}
                  </Typography.Paragraph>

                  <div className="knowledge-card-footer">
                    {renderVisibility(
                      item,
                    )}
                  </div>
                </Card>
              </Col>
            ),
          )}
        </Row>
      )}

      {/* ======================================================
          Create / Edit Modal
          ====================================================== */}
      <Modal
        title={
          editing
            ? '编辑知识库'
            : '新建知识库'
        }
        open={modalOpen}
        onCancel={
          closeModal
        }
        onOk={
          handleSave
        }
        confirmLoading={
          saving
        }
        okText={
          editing
            ? '保存'
            : '创建'
        }
        cancelText="取消"
        width={620}
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
        >
          <Form.Item
            label="名称"
            name="name"
            rules={[
              {
                required: true,
                message:
                  '请输入知识库名称',
              },
              {
                max: 120,
                message:
                  '名称不能超过120个字符',
              },
            ]}
          >
            <Input
              placeholder="例如：财务制度"
              maxLength={120}
            />
          </Form.Item>

          <Form.Item
            label="说明"
            name="description"
            rules={[
              {
                max: 500,
                message:
                  '说明不能超过500个字符',
              },
            ]}
          >
            <Input.TextArea
              rows={3}
              maxLength={500}
              showCount
              placeholder="例如：财务部内部报销、预算及结算制度"
            />
          </Form.Item>

          <Form.Item
            label="访问范围"
            name="visibility"
            rules={[
              {
                required: true,
                message:
                  '请选择访问范围',
              },
            ]}
          >
            <Radio.Group className="knowledge-visibility-group">
              <Space
                direction="vertical"
                size={12}
              >
                <Radio value="TENANT">
                  <Space>
                    <TeamOutlined />

                    <span>
                      企业全员
                    </span>

                    <Typography.Text
                      type="secondary"
                    >
                      当前企业所有成员均可访问
                    </Typography.Text>
                  </Space>
                </Radio>

                <Radio value="DEPARTMENT">
                  <Space>
                    <ApartmentOutlined />

                    <span>
                      指定部门
                    </span>

                    <Typography.Text
                      type="secondary"
                    >
                      仅选择的部门成员可访问
                    </Typography.Text>
                  </Space>
                </Radio>

                <Radio value="MEMBER">
                  <Space>
                    <UserOutlined />

                    <span>
                      指定成员
                    </span>

                    <Typography.Text
                      type="secondary"
                    >
                      仅指定企业成员可访问
                    </Typography.Text>
                  </Space>
                </Radio>

                <Radio value="PRIVATE">
                  <Space>
                    <LockOutlined />

                    <span>
                      仅自己
                    </span>

                    <Typography.Text
                      type="secondary"
                    >
                      仅知识库创建者可访问
                    </Typography.Text>
                  </Space>
                </Radio>
              </Space>
            </Radio.Group>
          </Form.Item>

          {/* DEPARTMENT ACL */}
          {visibility ===
            'DEPARTMENT' && (
            <Form.Item
              label="允许访问的部门"
              name="departmentIds"
              rules={[
                {
                  validator: (
                    _,
                    value,
                  ) => {
                    if (
                      !value ||
                      value.length ===
                        0
                    ) {
                      return Promise.reject(
                        new Error(
                          '请至少选择一个部门',
                        ),
                      )
                    }

                    return Promise.resolve()
                  },
                },
              ]}
              extra="只有所选部门的成员可以看到和检索该知识库。"
            >
              <TreeSelect
                treeData={
                  departmentTreeData
                }
                treeCheckable
                showCheckedStrategy={
                  TreeSelect.SHOW_ALL
                }
                treeDefaultExpandAll
                allowClear
                maxTagCount="responsive"
                placeholder="请选择部门"
              />
            </Form.Item>
          )}

          {/* MEMBER ACL */}
          {visibility ===
            'MEMBER' && (
            <Form.Item
              label="允许访问的成员"
              name="memberIds"
              rules={[
                {
                  validator: (
                    _,
                    value,
                  ) => {
                    if (
                      !value ||
                      value.length ===
                        0
                    ) {
                      return Promise.reject(
                        new Error(
                          '请至少选择一个成员',
                        ),
                      )
                    }

                    return Promise.resolve()
                  },
                },
              ]}
              extra="这里保存的是企业成员关系，而不是单纯的用户账号。"
            >
              <Select
                mode="multiple"
                allowClear
                showSearch
                maxTagCount="responsive"
                placeholder="请选择成员"
                options={
                  memberOptions
                }
                optionFilterProp="label"
              />
            </Form.Item>
          )}

          {visibility ===
            'PRIVATE' && (
            <div className="knowledge-private-tip">
              <LockOutlined />

              <Typography.Text>
                该知识库仅创建者本人可以查看、上传文档和进行
                RAG 问答。
              </Typography.Text>
            </div>
          )}
        </Form>
      </Modal>
    </div>
  )
}

/**
 * 清理提交数据。
 *
 * 避免：
 *
 * DEPARTMENT → TENANT
 *
 * 之后还携带旧 departmentIds。
 */
function normalizePayload(
  values:
    KnowledgeBaseFormValues,
) {
  return {
    name:
      values.name.trim(),

    description:
      values.description?.trim() ||
      '',

    visibility:
      values.visibility,

    departmentIds:
      values.visibility ===
      'DEPARTMENT'
        ? values.departmentIds ??
          []
        : [],

    memberIds:
      values.visibility ===
      'MEMBER'
        ? values.memberIds ??
          []
        : [],
  }
}

/**
 * 卡片显示权限。
 */
function renderVisibility(
  knowledgeBase:
    KnowledgeBase,
) {
  switch (
    knowledgeBase.visibility
  ) {
    case 'DEPARTMENT':
      return (
        <Tag
          icon={
            <ApartmentOutlined />
          }
          color="blue"
        >
          指定部门 ·{' '}
          {
            knowledgeBase
              .departmentIds
              ?.length
          }{' '}
          个
        </Tag>
      )

    case 'MEMBER':
      return (
        <Tag
          icon={
            <UserOutlined />
          }
          color="purple"
        >
          指定成员 ·{' '}
          {
            knowledgeBase
              .memberIds
              ?.length
          }{' '}
          人
        </Tag>
      )

    case 'PRIVATE':
      return (
        <Tag
          icon={
            <LockOutlined />
          }
          color="orange"
        >
          仅自己
        </Tag>
      )

    case 'TENANT':
    default:
      return (
        <Tag
          icon={
            <TeamOutlined />
          }
          color="green"
        >
          企业全员
        </Tag>
      )
  }
}