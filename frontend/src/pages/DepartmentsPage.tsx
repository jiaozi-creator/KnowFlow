import {
  ApartmentOutlined,
  DeleteOutlined,
  EditOutlined,
  PlusOutlined,
} from '@ant-design/icons'
import {
  App,
  Button,
  Card,
  Empty,
  Form,
  Input,
  InputNumber,
  Modal,
  Space,
  Spin,
  Tag,
  Tree,
  TreeSelect,
  Typography,
} from 'antd'
import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import { useMemo, useState } from 'react'

import { api } from '../api/client'

export interface Department {
  id: number
  parentId: number | null
  name: string
  sortOrder: number
  createdAt: string
  updatedAt: string
  children: Department[]
}

interface DepartmentFormValues {
  name: string
  parentId?: number
  sortOrder?: number
}

interface FlatDepartment {
  id: number
  parentId: number | null
  name: string
  sortOrder: number
  level: number
}

export function DepartmentsPage() {
  const { message, modal } = App.useApp()
  const queryClient = useQueryClient()

  const [form] =
    Form.useForm<DepartmentFormValues>()

  const [
    modalOpen,
    setModalOpen,
  ] = useState(false)

  const [
    editing,
    setEditing,
  ] = useState<Department | null>(
    null,
  )

  const [
    defaultParentId,
    setDefaultParentId,
  ] = useState<number | undefined>()

  /**
   * 查询部门树
   */
  const {
    data: departments = [],
    isLoading,
  } = useQuery({
    queryKey: ['departments'],

    queryFn: () =>
      api<Department[]>(
        '/api/departments',
      ),
  })

  /**
   * 将树拍平成数组。
   * 后面统计和 TreeSelect 会使用。
   */
  const flatDepartments =
    useMemo(() => {
      const result: FlatDepartment[] =
        []

      const walk = (
        list: Department[],
        level = 0,
      ) => {
        list.forEach((item) => {
          result.push({
            id: item.id,
            parentId:
              item.parentId,
            name: item.name,
            sortOrder:
              item.sortOrder,
            level,
          })

          if (
            item.children?.length
          ) {
            walk(
              item.children,
              level + 1,
            )
          }
        })
      }

      walk(departments)

      return result
    }, [departments])

  /**
   * 创建部门
   */
  const createMutation =
    useMutation({
      mutationFn: (
        values: DepartmentFormValues,
      ) =>
        api<Department>(
          '/api/departments',
          {
            method: 'POST',

            body: JSON.stringify({
              name:
                values.name.trim(),

              parentId:
                values.parentId ??
                null,

              sortOrder:
                values.sortOrder ??
                0,
            }),
          },
        ),

      onSuccess: async () => {
        message.success(
          '部门创建成功',
        )

        closeModal()

        await queryClient.invalidateQueries(
          {
            queryKey: [
              'departments',
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
   * 修改部门
   */
  const updateMutation =
    useMutation({
      mutationFn: (
        values: DepartmentFormValues,
      ) => {
        if (!editing) {
          throw new Error(
            '缺少部门信息',
          )
        }

        return api<Department>(
          `/api/departments/${editing.id}`,
          {
            method: 'PUT',

            body: JSON.stringify({
              name:
                values.name.trim(),

              parentId:
                values.parentId ??
                null,

              sortOrder:
                values.sortOrder ??
                0,
            }),
          },
        )
      },

      onSuccess: async () => {
        message.success(
          '部门修改成功',
        )

        closeModal()

        await queryClient.invalidateQueries(
          {
            queryKey: [
              'departments',
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
   * 删除部门
   */
  const deleteMutation =
    useMutation({
      mutationFn: (id: number) =>
        api<void>(
          `/api/departments/${id}`,
          {
            method: 'DELETE',
          },
        ),

      onSuccess: async () => {
        message.success(
          '部门已删除',
        )

        await queryClient.invalidateQueries(
          {
            queryKey: [
              'departments',
            ],
          },
        )
      },

      onError: (error) => {
        message.error(
          error instanceof Error
            ? error.message
            : '删除失败',
        )
      },
    })

  /**
   * 新建一级部门
   */
  const openCreateRoot =
    () => {
      setEditing(null)

      setDefaultParentId(
        undefined,
      )

      form.resetFields()

      form.setFieldsValue({
        sortOrder: 0,
      })

      setModalOpen(true)
    }

  /**
   * 新建子部门
   */
  const openCreateChild =
    (
      department: Department,
    ) => {
      setEditing(null)

      setDefaultParentId(
        department.id,
      )

      form.resetFields()

      form.setFieldsValue({
        parentId:
          department.id,

        sortOrder: 0,
      })

      setModalOpen(true)
    }

  /**
   * 编辑
   */
  const openEdit = (
    department: Department,
  ) => {
    setEditing(department)

    setDefaultParentId(
      department.parentId ??
        undefined,
    )

    form.setFieldsValue({
      name: department.name,

      parentId:
        department.parentId ??
        undefined,

      sortOrder:
        department.sortOrder,
    })

    setModalOpen(true)
  }

  const closeModal = () => {
    setModalOpen(false)
    setEditing(null)

    setDefaultParentId(
      undefined,
    )

    form.resetFields()
  }

  /**
   * 删除确认
   */
  const confirmDelete = (
    department: Department,
  ) => {
    modal.confirm({
      title: '删除部门',

      content: (
        <>
          确定删除部门
          <strong>
            「{department.name}」
          </strong>
          吗？
          <br />
          <br />
          有子部门的情况下，
          系统会禁止删除。
        </>
      ),

      okText: '删除',

      cancelText: '取消',

      okButtonProps: {
        danger: true,
      },

      onOk: () =>
        deleteMutation.mutateAsync(
          department.id,
        ),
    })
  }

  /**
   * 保存 Modal
   */
  const handleSubmit =
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

  /**
   * 转成 Ant Design Tree 数据。
   */
  const treeData =
    useMemo(() => {
      const convert = (
        list: Department[],
      ): any[] =>
        list.map((item) => ({
          key: item.id,

          title: (
            <div className="department-tree-row">
              <div className="department-tree-name">
                <ApartmentOutlined />

                <Typography.Text strong>
                  {item.name}
                </Typography.Text>

                {item.parentId ===
                  null && (
                  <Tag>
                    一级部门
                  </Tag>
                )}
              </div>

              <Space
                size={4}
                onClick={(event) =>
                  event.stopPropagation()
                }
              >
                <Button
                  type="text"
                  size="small"
                  icon={
                    <PlusOutlined />
                  }
                  onClick={() =>
                    openCreateChild(
                      item,
                    )
                  }
                >
                  子部门
                </Button>

                <Button
                  type="text"
                  size="small"
                  icon={
                    <EditOutlined />
                  }
                  onClick={() =>
                    openEdit(item)
                  }
                >
                  编辑
                </Button>

                <Button
                  danger
                  type="text"
                  size="small"
                  icon={
                    <DeleteOutlined />
                  }
                  onClick={() =>
                    confirmDelete(
                      item,
                    )
                  }
                >
                  删除
                </Button>
              </Space>
            </div>
          ),

          children: convert(
            item.children ?? [],
          ),
        }))

      return convert(departments)
    }, [departments])

  /**
   * TreeSelect 上级部门数据。
   */
  const parentTreeData =
    useMemo(() => {
      const convert = (
        list: Department[],
      ): any[] =>
        list
          /*
           * 编辑部门时，
           * 至少不允许把自己设为自己。
           * 后端还会继续检查循环层级。
           */
          .filter(
            (item) =>
              item.id !==
              editing?.id,
          )
          .map((item) => ({
            title:
              item.name,

            value:
              item.id,

            key:
              item.id,

            children:
              convert(
                item.children ?? [],
              ),
          }))

      return convert(departments)
    }, [
      departments,
      editing?.id,
    ])

  const saving =
    createMutation.isPending ||
    updateMutation.isPending

  return (
    <div className="departments-page">
      <div className="page-heading">
        <div>
          <Typography.Title
            level={2}
          >
            部门管理
          </Typography.Title>

          <Typography.Text
            type="secondary"
          >
            管理企业组织架构，
            后续成员权限和知识库访问范围将基于部门配置。
          </Typography.Text>
        </div>

        <Button
          type="primary"
          icon={<PlusOutlined />}
          onClick={
            openCreateRoot
          }
        >
          新建部门
        </Button>
      </div>

      <div className="department-stat-grid">
        <Card>
          <Typography.Text
            type="secondary"
          >
            部门总数
          </Typography.Text>

          <Typography.Title
            level={2}
          >
            {
              flatDepartments.length
            }
          </Typography.Title>
        </Card>

        <Card>
          <Typography.Text
            type="secondary"
          >
            一级部门
          </Typography.Text>

          <Typography.Title
            level={2}
          >
            {
              departments.length
            }
          </Typography.Title>
        </Card>

        <Card>
          <Typography.Text
            type="secondary"
          >
            最大层级
          </Typography.Text>

          <Typography.Title
            level={2}
          >
            {flatDepartments.length
              ? Math.max(
                  ...flatDepartments.map(
                    (item) =>
                      item.level +
                      1,
                  ),
                )
              : 0}
          </Typography.Title>
        </Card>
      </div>

      <Card
        title={
          <Space>
            <ApartmentOutlined />
            组织架构
          </Space>
        }
        className="department-tree-card"
      >
        {isLoading ? (
          <div className="department-loading">
            <Spin />
          </div>
        ) : departments.length ===
          0 ? (
          <Empty
            description="暂未创建部门"
          >
            <Button
              type="primary"
              icon={
                <PlusOutlined />
              }
              onClick={
                openCreateRoot
              }
            >
              创建第一个部门
            </Button>
          </Empty>
        ) : (
          <Tree
            blockNode
            defaultExpandAll
            selectable={false}
            treeData={treeData}
          />
        )}
      </Card>

      <Modal
        title={
          editing
            ? '编辑部门'
            : defaultParentId
              ? '新建子部门'
              : '新建部门'
        }
        open={modalOpen}
        onCancel={
          closeModal
        }
        onOk={
          handleSubmit
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
        destroyOnHidden
      >
        <Form
          form={form}
          layout="vertical"
        >
          <Form.Item
            label="部门名称"
            name="name"
            rules={[
              {
                required: true,
                message:
                  '请输入部门名称',
              },

              {
                max: 120,
                message:
                  '部门名称不能超过120个字符',
              },
            ]}
          >
            <Input
              placeholder="例如：技术中心"
              maxLength={120}
            />
          </Form.Item>

          <Form.Item
            label="上级部门"
            name="parentId"
          >
            <TreeSelect
              allowClear
              treeDefaultExpandAll
              placeholder="不选择则为一级部门"
              treeData={
                parentTreeData
              }
            />
          </Form.Item>

          <Form.Item
            label="排序"
            name="sortOrder"
            tooltip="数字越小越靠前"
          >
            <InputNumber
              min={0}
              max={9999}
              style={{
                width: '100%',
              }}
            />
          </Form.Item>
        </Form>
      </Modal>
    </div>
  )
}