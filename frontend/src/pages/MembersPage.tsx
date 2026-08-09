import {
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Card,
  Form,
  Input,
  Modal,
  Select,
  Space,
  Table,
  Tag,
  TreeSelect,
  Typography,
} from 'antd'
import type { ColumnsType } from 'antd/es/table'
import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  useMemo,
  useState,
} from 'react'

import { api } from '../api/client'

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

interface Department {
  id: number
  parentId: number | null
  name: string
  sortOrder: number
  createdAt: string
  updatedAt: string
  children: Department[]
}

interface CreateValues {
  email: string
  displayName: string
  temporaryPassword: string
  departmentId?: number
  role: 'ADMIN' | 'MEMBER'
}

interface EditValues {
  departmentId?: number
  role?: 'ADMIN' | 'MEMBER'
}

export function MembersPage() {
  const { message, modal } = App.useApp()

  const queryClient =
    useQueryClient()

  const [createForm] =
    Form.useForm<CreateValues>()

  const [editForm] =
    Form.useForm<EditValues>()

  const [
    createOpen,
    setCreateOpen,
  ] = useState(false)

  const [
    editingMember,
    setEditingMember,
  ] = useState<Member | null>(
    null,
  )

  /**
   * 查询成员列表
   */
  const {
    data: members = [],
    isLoading,
  } = useQuery({
    queryKey: ['members'],

    queryFn: () =>
      api<Member[]>(
        '/api/members',
      ),
  })

  /**
   * 查询部门树
   */
  const {
    data: departments = [],
  } = useQuery({
    queryKey: ['departments'],

    queryFn: () =>
      api<Department[]>(
        '/api/departments',
      ),
  })

  /**
   * 转换成 TreeSelect 数据
   */
  const departmentTreeData =
    useMemo(() => {
      const convert = (
        list: Department[],
      ): any[] => {
        return list.map(
          (item) => ({
            title: item.name,
            value: item.id,
            key: item.id,

            children: convert(
              item.children ?? [],
            ),
          }),
        )
      }

      return convert(
        departments,
      )
    }, [departments])

  /**
   * 创建成员
   */
  const createMutation =
    useMutation({
      mutationFn: (
        values: CreateValues,
      ) =>
        api<Member>(
          '/api/members',
          {
            method: 'POST',

            body: JSON.stringify({
              email:
                values.email.trim(),

              displayName:
                values.displayName.trim(),

              temporaryPassword:
                values.temporaryPassword,

              departmentId:
                values.departmentId ??
                null,

              role:
                values.role ??
                'MEMBER',
            }),
          },
        ),

      onSuccess: async () => {
        message.success(
          '成员创建成功',
        )

        setCreateOpen(false)

        createForm.resetFields()

        await queryClient.invalidateQueries(
          {
            queryKey: [
              'members',
            ],
          },
        )
      },

      onError: (error) => {
        message.error(
          error instanceof Error
            ? error.message
            : '创建失败',
        )
      },
    })

  /**
   * 修改成员
   */
  const updateMutation =
    useMutation({
      mutationFn: (
        values: EditValues,
      ) => {
        if (!editingMember) {
          throw new Error(
            '缺少成员信息',
          )
        }

        const body: {
          departmentId:
            | number
            | null
          role?: string
        } = {
          departmentId:
            values.departmentId ??
            null,
        }

        /**
         * OWNER 不允许通过普通成员管理修改角色
         */
        if (
          editingMember.role !==
            'OWNER' &&
          values.role
        ) {
          body.role =
            values.role
        }

        return api<Member>(
          `/api/members/${editingMember.membershipId}`,
          {
            method: 'PUT',

            body:
              JSON.stringify(
                body,
              ),
          },
        )
      },

      onSuccess: async () => {
        message.success(
          '成员信息已更新',
        )

        setEditingMember(
          null,
        )

        editForm.resetFields()

        await queryClient.invalidateQueries(
          {
            queryKey: [
              'members',
            ],
          },
        )
      },

      onError: (error) => {
        message.error(
          error instanceof Error
            ? error.message
            : '修改失败',
        )
      },
    })

  /**
   * 删除成员
   */
  const deleteMutation =
    useMutation({
      mutationFn: (
        membershipId: number,
      ) =>
        api<void>(
          `/api/members/${membershipId}`,
          {
            method: 'DELETE',
          },
        ),

      onSuccess: async () => {
        message.success(
          '成员已移除',
        )

        await queryClient.invalidateQueries(
          {
            queryKey: [
              'members',
            ],
          },
        )
      },

      onError: (error) => {
        message.error(
          error instanceof Error
            ? error.message
            : '移除失败',
        )
      },
    })

  /**
   * 打开创建成员
   */
  const openCreate = () => {
    createForm.resetFields()

    createForm.setFieldsValue({
      role: 'MEMBER',
    })

    setCreateOpen(true)
  }

  /**
   * 打开编辑成员
   */
  const openEdit = (
    member: Member,
  ) => {
    setEditingMember(member)

    editForm.resetFields()

    editForm.setFieldsValue({
      departmentId:
        member.departmentId ??
        undefined,

      role:
        member.role ===
        'OWNER'
          ? undefined
          : (member.role as
              | 'ADMIN'
              | 'MEMBER'),
    })
  }

  /**
   * 删除确认
   */
  const confirmDelete = (
    member: Member,
  ) => {
    modal.confirm({
      title: '移除企业成员',

      content: (
        <span>
          确定将
          <strong>
            「
            {
              member.displayName
            }
            」
          </strong>
          从当前企业移除吗？
        </span>
      ),

      okText: '移除',
      cancelText: '取消',

      okButtonProps: {
        danger: true,
      },

      onOk: () =>
        deleteMutation.mutateAsync(
          member.membershipId,
        ),
    })
  }

  /**
   * 角色标签
   */
  const renderRole = (
    role: string,
  ) => {
    if (role === 'OWNER') {
      return (
        <Tag color="gold">
          OWNER
        </Tag>
      )
    }

    if (role === 'ADMIN') {
      return (
        <Tag color="blue">
          ADMIN
        </Tag>
      )
    }

    return (
      <Tag>
        MEMBER
      </Tag>
    )
  }

  /**
   * 表格列
   */
  const columns:
    ColumnsType<Member> = [
    {
      title: '成员',
      key: 'member',

      render: (_, record) => (
        <div>
          <Typography.Text
            strong
          >
            {
              record.displayName
            }
          </Typography.Text>

          <div>
            <Typography.Text
              type="secondary"
            >
              {record.email}
            </Typography.Text>
          </div>
        </div>
      ),
    },

    {
      title: '部门',
      dataIndex:
        'departmentName',

      render: (
        value:
          | string
          | null,
      ) =>
        value ? (
          <Space>
            <TeamOutlined />
            {value}
          </Space>
        ) : (
          <Typography.Text
            type="secondary"
          >
            未分配
          </Typography.Text>
        ),
    },

    {
      title: '企业角色',
      dataIndex: 'role',

      render: (
        role: string,
      ) =>
        renderRole(role),
    },

    {
      title: '状态',
      dataIndex: 'status',

      render: (
        status: string,
      ) =>
        status ===
        'ACTIVE' ? (
          <Tag color="green">
            正常
          </Tag>
        ) : (
          <Tag>
            {status}
          </Tag>
        ),
    },

    {
      title: '加入时间',
      dataIndex: 'createdAt',

      render: (
        value: string,
      ) =>
        value
          ? new Date(
              value,
            ).toLocaleString()
          : '-',
    },

    {
      title: '操作',
      key: 'actions',
      width: 180,

      render: (_, record) => (
        <Space>
          <Button
            type="link"
            icon={
              <EditOutlined />
            }
            onClick={() =>
              openEdit(record)
            }
          >
            编辑
          </Button>

          <Button
            danger
            type="link"
            disabled={
              record.role ===
              'OWNER'
            }
            icon={
              <DeleteOutlined />
            }
            onClick={() =>
              confirmDelete(
                record,
              )
            }
          >
            移除
          </Button>
        </Space>
      ),
    },
  ]

  /**
   * 统计信息
   */
  const adminCount =
    members.filter(
      (member) =>
        member.role ===
          'OWNER' ||
        member.role ===
          'ADMIN',
    ).length

  const assignedCount =
    members.filter(
      (member) =>
        member.departmentId !==
        null,
    ).length

  return (
    <div className="members-page">
      <div className="page-heading">
        <div>
          <Typography.Title
            level={2}
          >
            成员管理
          </Typography.Title>

          <Typography.Text
            type="secondary"
          >
            管理企业成员、
            所属部门及企业角色。
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
          新建成员
        </Button>
      </div>

      <div className="member-stat-grid">
        <Card>
          <Typography.Text
            type="secondary"
          >
            企业成员
          </Typography.Text>

          <Typography.Title
            level={2}
          >
            {members.length}
          </Typography.Title>
        </Card>

        <Card>
          <Typography.Text
            type="secondary"
          >
            管理员
          </Typography.Text>

          <Typography.Title
            level={2}
          >
            {adminCount}
          </Typography.Title>
        </Card>

        <Card>
          <Typography.Text
            type="secondary"
          >
            已分配部门
          </Typography.Text>

          <Typography.Title
            level={2}
          >
            {assignedCount}
          </Typography.Title>
        </Card>
      </div>

      <Card>
        <Table<Member>
          rowKey="membershipId"
          loading={isLoading}
          columns={columns}
          dataSource={members}
          pagination={{
            pageSize: 10,
            showSizeChanger:
              false,
          }}
        />
      </Card>

      {/* 新建成员 */}
      <Modal
        title="新建成员"
        open={createOpen}
        onCancel={() => {
          setCreateOpen(false)

          createForm.resetFields()
        }}
        onOk={async () => {
          const values =
            await createForm.validateFields()

          createMutation.mutate(
            values,
          )
        }}
        okText="创建账号"
        cancelText="取消"
        confirmLoading={
          createMutation.isPending
        }
        destroyOnHidden
      >
        <Form
          form={createForm}
          layout="vertical"
        >
          <Form.Item
            label="姓名"
            name="displayName"
            rules={[
              {
                required: true,
                message:
                  '请输入姓名',
              },
              {
                max: 80,
                message:
                  '姓名不能超过80个字符',
              },
            ]}
          >
            <Input
              maxLength={80}
              placeholder="例如：张三"
            />
          </Form.Item>

          <Form.Item
            label="邮箱"
            name="email"
            rules={[
              {
                required: true,
                message:
                  '请输入邮箱',
              },
              {
                type: 'email',
                message:
                  '邮箱格式不正确',
              },
            ]}
          >
            <Input
              placeholder="zhangsan@example.com"
            />
          </Form.Item>

          <Form.Item
            label="初始密码"
            name="temporaryPassword"
            extra="当前版本暂未接入邮件邀请，请将初始密码提供给成员。"
            rules={[
              {
                required: true,
                message:
                  '请输入初始密码',
              },
              {
                min: 8,
                message:
                  '密码至少8位',
              },
            ]}
          >
            <Input.Password
              placeholder="至少8位"
            />
          </Form.Item>

          <Form.Item
            label="所属部门"
            name="departmentId"
          >
            <TreeSelect
              allowClear
              treeDefaultExpandAll
              placeholder="可暂不分配部门"
              treeData={
                departmentTreeData
              }
            />
          </Form.Item>

          <Form.Item
            label="企业角色"
            name="role"
            initialValue="MEMBER"
            rules={[
              {
                required: true,
                message:
                  '请选择角色',
              },
            ]}
          >
            <Select
              options={[
                {
                  label:
                    '普通成员 MEMBER',
                  value:
                    'MEMBER',
                },

                {
                  label:
                    '管理员 ADMIN',
                  value:
                    'ADMIN',
                },
              ]}
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* 编辑成员 */}
      <Modal
        title={
          editingMember
            ? `编辑成员：${editingMember.displayName}`
            : '编辑成员'
        }
        open={
          editingMember !==
          null
        }
        onCancel={() => {
          setEditingMember(
            null,
          )

          editForm.resetFields()
        }}
        onOk={async () => {
          const values =
            await editForm.validateFields()

          updateMutation.mutate(
            values,
          )
        }}
        okText="保存"
        cancelText="取消"
        confirmLoading={
          updateMutation.isPending
        }
        destroyOnHidden
      >
        <Form
          form={editForm}
          layout="vertical"
        >
          <Form.Item
            label="所属部门"
            name="departmentId"
          >
            <TreeSelect
              allowClear
              treeDefaultExpandAll
              placeholder="未分配部门"
              treeData={
                departmentTreeData
              }
            />
          </Form.Item>

          {editingMember?.role ===
          'OWNER' ? (
            <Form.Item
              label="企业角色"
            >
              <Input
                value="OWNER"
                disabled
              />
            </Form.Item>
          ) : (
            <Form.Item
              label="企业角色"
              name="role"
              rules={[
                {
                  required: true,
                  message:
                    '请选择角色',
                },
              ]}
            >
              <Select
                options={[
                  {
                    label:
                      '普通成员 MEMBER',
                    value:
                      'MEMBER',
                  },

                  {
                    label:
                      '管理员 ADMIN',
                    value:
                      'ADMIN',
                  },
                ]}
              />
            </Form.Item>
          )}
        </Form>
      </Modal>
    </div>
  )
}