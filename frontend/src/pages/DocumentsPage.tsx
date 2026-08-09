import {
  ArrowLeftOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  DeleteOutlined,
  EyeOutlined,
  InboxOutlined,
  ReloadOutlined,
  SyncOutlined,
  WarningOutlined,
} from '@ant-design/icons'
import {
  useMutation,
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  Alert,
  App,
  Button,
  Card,
  Col,
  Popconfirm,
  Progress,
  Row,
  Space,
  Statistic,
  Table,
  Tag,
  Typography,
  Upload,
} from 'antd'
import type {
  UploadProps,
} from 'antd'
import {
  useMemo,
  useState,
} from 'react'
import {
  useNavigate,
  useParams,
} from 'react-router-dom'

import { api } from '../api/client'
import { PdfPreviewDrawer } from '../components/PdfPreviewDrawer'
import { useAuthStore } from '../store/auth'
import type {
  BatchReindexResponse,
  DocumentItem,
  IndexStatus,
  IngestionTask,
  KnowledgeBase,
} from '../types'

export function DocumentsPage() {
  const knowledgeBaseId =
    Number(
      useParams().knowledgeBaseId,
    )

  const navigate =
    useNavigate()

  const queryClient =
    useQueryClient()

  const { message } =
    App.useApp()

  const user =
    useAuthStore(
      (state) => state.user,
    )

  const [
    preview,
    setPreview,
  ] = useState<{
    open: boolean
    url?: string
    title?: string
  }>({
    open: false,
  })

  const {
    data: knowledgeBase,
  } = useQuery({
    queryKey: [
      'knowledge-base',
      knowledgeBaseId,
    ],

    queryFn: () =>
      api<KnowledgeBase>(
        `/api/knowledge-bases/${knowledgeBaseId}`,
      ),

    enabled:
      Number.isFinite(
        knowledgeBaseId,
      ),
  })

  const canManage =
    useMemo(
      () =>
        Boolean(
          user &&
            knowledgeBase &&
            (
              user.organizationRole ===
                'OWNER' ||
              user.organizationRole ===
                'ADMIN' ||
              knowledgeBase.createdBy ===
                user.id
            ),
        ),
      [
        user,
        knowledgeBase,
      ],
    )

  const {
    data: documents = [],
    refetch: refetchDocuments,
  } = useQuery({
    queryKey: [
      'documents',
      knowledgeBaseId,
    ],

    queryFn: () =>
      api<DocumentItem[]>(
        `/api/knowledge-bases/${knowledgeBaseId}/documents`,
      ),

    enabled:
      Number.isFinite(
        knowledgeBaseId,
      ),

    refetchInterval: (
      query,
    ) => {
      const current =
        query.state.data as
          | DocumentItem[]
          | undefined

      return current?.some(
        (document) =>
          document.status ===
          'PROCESSING',
      )
        ? 2000
        : false
    },
  })

  const {
    data: indexStatus,
    isLoading:
      indexStatusLoading,
  } = useQuery({
    queryKey: [
      'document-index-status',
      knowledgeBaseId,
    ],

    queryFn: () =>
      api<IndexStatus>(
        `/api/knowledge-bases/${knowledgeBaseId}/documents/index-status`,
      ),

    enabled:
      canManage,

    refetchInterval: (
      query,
    ) => {
      const current =
        query.state.data as
          | IndexStatus
          | undefined

      return current &&
        current.processing > 0
        ? 2000
        : false
    },
  })

  const upload:
    UploadProps['customRequest'] =
    async (options) => {
      const form =
        new FormData()

      form.append(
        'file',
        options.file as File,
      )

      try {
        await api(
          `/api/knowledge-bases/${knowledgeBaseId}/documents`,
          {
            method: 'POST',
            body: form,
          },
        )

        options.onSuccess?.(
          {},
        )

        message.success(
          '文件已进入处理队列',
        )

        await Promise.all([
          refetchDocuments(),

          queryClient.invalidateQueries({
            queryKey: [
              'document-index-status',
              knowledgeBaseId,
            ],
          }),
        ])
      } catch (
        error
      ) {
        options.onError?.(
          error as Error,
        )

        message.error(
          error instanceof Error
            ? error.message
            : '上传失败',
        )
      }
    }

  const remove =
    useMutation({
      mutationFn: (
        documentId:
          number,
      ) =>
        api<void>(
          `/api/documents/${documentId}`,
          {
            method:
              'DELETE',
          },
        ),

      onSuccess:
        async () => {
          message.success(
            '文档已删除',
          )

          await invalidateAll()
        },

      onError:
        (
          error: Error,
        ) =>
          message.error(
            error.message,
          ),
    })

  const reindex =
    useMutation({
      mutationFn: (
        documentId:
          number,
      ) =>
        api<IngestionTask>(
          `/api/documents/${documentId}/reindex`,
          {
            method: 'POST',
          },
        ),

      onSuccess:
        async () => {
          message.success(
            '已提交重新索引任务',
          )

          await invalidateAll()
        },

      onError:
        (
          error: Error,
        ) =>
          message.error(
            error.message,
          ),
    })

  const repairIndexes =
    useMutation({
      mutationFn: () =>
        api<BatchReindexResponse>(
          `/api/knowledge-bases/${knowledgeBaseId}/documents/repair-indexes`,
          {
            method: 'POST',
          },
        ),

      onSuccess:
        async (
          result,
        ) => {
          if (
            result.queuedCount ===
            0
          ) {
            message.info(
              '当前没有需要修复的索引',
            )
          } else {
            message.success(
              `已提交 ${result.queuedCount} 个索引任务`,
            )
          }

          await invalidateAll()
        },

      onError:
        (
          error: Error,
        ) =>
          message.error(
            error.message,
          ),
    })

  async function invalidateAll() {
    await Promise.all([
      queryClient.invalidateQueries({
        queryKey: [
          'documents',
          knowledgeBaseId,
        ],
      }),

      queryClient.invalidateQueries({
        queryKey: [
          'document-index-status',
          knowledgeBaseId,
        ],
      }),
    ])
  }

  const openPreview =
    (
      document:
        DocumentItem,
    ) => {
      if (
        document.fileType !==
        'PDF'
      ) {
        message.info(
          '当前页面预览仅支持 PDF',
        )
        return
      }

      setPreview({
        open: true,
        url:
          `/api/documents/${document.id}/content`,
        title:
          document.name,
      })
    }

  return (
    <div>
      <Button
        type="text"
        icon={
          <ArrowLeftOutlined />
        }
        onClick={() =>
          navigate(
            '/app/knowledge-bases',
          )
        }
      >
        返回知识库
      </Button>

      <div className="page-heading">
        <div>
          <Typography.Title
            level={2}
          >
            {knowledgeBase
              ?.name ??
              '文档管理'}
          </Typography.Title>

          <Typography.Text
            type="secondary"
          >
            文档上传后自动完成解析、切片和向量化；
            索引规则升级时系统自动标记过期索引。
          </Typography.Text>
        </div>
      </div>

      {canManage && (
        <Card
          className="section-card"
          title={
            <Space>
              <SyncOutlined />
              索引状态
            </Space>
          }
          extra={
            <Button
              type="primary"
              icon={
                <ReloadOutlined />
              }
              loading={
                repairIndexes.isPending
              }
              disabled={
                !indexStatus ||
                indexStatus.repairable ===
                  0
              }
              onClick={() =>
                repairIndexes.mutate()
              }
            >
              一键修复索引
            </Button>
          }
        >
          <Row
            gutter={[
              16,
              16,
            ]}
          >
            <Col
              xs={12}
              md={6}
              xl={4}
            >
              <Statistic
                title="文档总数"
                value={
                  indexStatus?.total ??
                  0
                }
                loading={
                  indexStatusLoading
                }
              />
            </Col>

            <Col
              xs={12}
              md={6}
              xl={5}
            >
              <Statistic
                title="已就绪"
                value={
                  indexStatus?.ready ??
                  0
                }
                prefix={
                  <CheckCircleOutlined />
                }
                loading={
                  indexStatusLoading
                }
              />
            </Col>

            <Col
              xs={12}
              md={6}
              xl={5}
            >
              <Statistic
                title="待更新"
                value={
                  indexStatus
                    ?.needsReindex ??
                  0
                }
                prefix={
                  <WarningOutlined />
                }
                loading={
                  indexStatusLoading
                }
              />
            </Col>

            <Col
              xs={12}
              md={6}
              xl={5}
            >
              <Statistic
                title="处理中"
                value={
                  indexStatus
                    ?.processing ??
                  0
                }
                prefix={
                  <ClockCircleOutlined />
                }
                loading={
                  indexStatusLoading
                }
              />
            </Col>

            <Col
              xs={12}
              md={6}
              xl={5}
            >
              <Statistic
                title="失败"
                value={
                  indexStatus?.failed ??
                  0
                }
                loading={
                  indexStatusLoading
                }
              />
            </Col>
          </Row>

          {indexStatus &&
            indexStatus.repairable >
              0 && (
              <Alert
                style={{
                  marginTop: 20,
                }}
                type="warning"
                showIcon
                message={`${indexStatus.repairable} 个文档需要维护索引`}
                description="待更新和失败文档都可以通过“一键修复索引”重新进入 RabbitMQ 处理队列。已就绪文档不会重复消耗 Embedding API。"
              />
            )}

          {indexStatus && (
            <Typography.Text
              type="secondary"
              style={{
                display:
                  'block',
                marginTop: 16,
                wordBreak:
                  'break-all',
              }}
            >
              当前索引签名：
              {' '}
              {
                indexStatus.currentSignature
              }
            </Typography.Text>
          )}
        </Card>
      )}

      {canManage ? (
        <Upload.Dragger
          customRequest={
            upload
          }
          showUploadList={
            false
          }
          accept=".pdf,.docx,.md,.markdown,.txt"
          multiple
          beforeUpload={(
            file,
          ) => {
            const max =
              30 *
              1024 *
              1024

            if (
              file.size >
              max
            ) {
              message.error(
                '单文件不能超过 30 MB',
              )
              return Upload.LIST_IGNORE
            }

            return true
          }}
        >
          <p className="ant-upload-drag-icon">
            <InboxOutlined />
          </p>

          <p className="ant-upload-text">
            点击或拖拽文档到此区域
          </p>

          <p className="ant-upload-hint">
            支持
            PDF、DOCX、Markdown、TXT，
            单文件不超过 30 MB
          </p>
        </Upload.Dragger>
      ) : (
        <Alert
          type="info"
          showIcon
          message="当前为只读访问"
          description="你可以查看有权限的文档并用于智能问答，但不能上传、删除或维护索引。"
          style={{
            marginBottom: 16,
          }}
        />
      )}

      <Card
        className="section-card"
        title="文档列表"
      >
        <Table<DocumentItem>
          rowKey="id"
          dataSource={
            documents
          }
          pagination={
            false
          }
          columns={[
            {
              title:
                '文件名',
              dataIndex:
                'name',
            },

            {
              title:
                '类型',
              dataIndex:
                'fileType',
              width:
                100,
              render:
                (
                  value,
                ) => (
                  <Tag>
                    {value}
                  </Tag>
                ),
            },

            {
              title:
                '状态',
              dataIndex:
                'status',
              width:
                140,
              render:
                (
                  value:
                    DocumentItem['status'],
                ) =>
                  renderStatus(
                    value,
                  ),
            },

            {
              title:
                '处理进度',
              width:
                180,
              render:
                (
                  _,
                  document,
                ) => (
                  <TaskProgress
                    document={
                      document
                    }
                  />
                ),
            },

            {
              title:
                '更新时间',
              dataIndex:
                'updatedAt',
              width:
                190,
              render:
                (
                  value,
                ) =>
                  new Date(
                    value,
                  ).toLocaleString(),
            },

            {
              title:
                '操作',
              width:
                canManage
                  ? 290
                  : 120,

              render:
                (
                  _,
                  document,
                ) => (
                  <Space wrap>
                    <Button
                      type="text"
                      icon={
                        <EyeOutlined />
                      }
                      disabled={
                        document.status !==
                        'READY' ||
                        document.fileType !==
                          'PDF'
                      }
                      onClick={() =>
                        openPreview(
                          document,
                        )
                      }
                    >
                      预览
                    </Button>

                    {canManage &&
                      (
                        document.status ===
                          'NEEDS_REINDEX' ||
                        document.status ===
                          'FAILED'
                      ) && (
                        <Button
                          type="link"
                          icon={
                            <ReloadOutlined />
                          }
                          loading={
                            reindex.isPending
                          }
                          onClick={() =>
                            reindex.mutate(
                              document.id,
                            )
                          }
                        >
                          重新索引
                        </Button>
                      )}

                    {canManage && (
                      <Popconfirm
                        title="确认删除文档？"
                        description="删除后原文件和索引将不可继续使用。"
                        okText="删除"
                        cancelText="取消"
                        onConfirm={() =>
                          remove.mutate(
                            document.id,
                          )
                        }
                      >
                        <Button
                          type="text"
                          danger
                          icon={
                            <DeleteOutlined />
                          }
                        />
                      </Popconfirm>
                    )}
                  </Space>
                ),
            },
          ]}
        />
      </Card>

      <PdfPreviewDrawer
        {...preview}
        onClose={() =>
          setPreview({
            open: false,
          })
        }
      />
    </div>
  )
}

function TaskProgress({
  document,
}: {
  document: DocumentItem
}) {
  const {
    data: task,
  } = useQuery({
    queryKey: [
      'document-task',
      document.id,
    ],

    queryFn: () =>
      api<IngestionTask>(
        `/api/documents/${document.id}/task`,
      ),

    enabled:
      document.status ===
      'PROCESSING',

    refetchInterval:
      document.status ===
      'PROCESSING'
        ? 1200
        : false,
  })

  if (
    document.status !==
    'PROCESSING'
  ) {
    return '-'
  }

  return (
    <div
      style={{
        minWidth: 130,
      }}
    >
      <Progress
        percent={
          task?.progress ?? 0
        }
        size="small"
        status="active"
      />

      <Typography.Text
        type="secondary"
        style={{
          fontSize: 12,
        }}
      >
        {task?.status ??
          'PENDING'}
      </Typography.Text>
    </div>
  )
}

function renderStatus(
  status:
    DocumentItem['status'],
) {
  switch (
    status
  ) {
    case 'READY':
      return (
        <Tag color="success">
          已就绪
        </Tag>
      )

    case 'NEEDS_REINDEX':
      return (
        <Tag color="warning">
          待更新索引
        </Tag>
      )

    case 'FAILED':
      return (
        <Tag color="error">
          处理失败
        </Tag>
      )

    case 'PROCESSING':
    default:
      return (
        <Tag
          color="processing"
          icon={
            <SyncOutlined spin />
          }
        >
          处理中
        </Tag>
      )
  }
}