import {
  DeleteOutlined,
  MessageOutlined,
  PlusOutlined,
  SendOutlined,
  StopOutlined,
} from '@ant-design/icons'
import {
  useQuery,
  useQueryClient,
} from '@tanstack/react-query'
import {
  App,
  Button,
  Card,
  Checkbox,
  Empty,
  Input,
  List,
  Space,
  Spin,
  Tag,
  Typography,
} from 'antd'
import {
  useEffect,
  useRef,
  useState,
} from 'react'
import ReactMarkdown from 'react-markdown'

import {
  api,
  streamChat,
} from '../api/client'

import type {
  ChatMessage,
  Citation,
  Conversation,
  KnowledgeBase,
} from '../types'

export function ChatPage() {
  const { message } = App.useApp()
  const queryClient = useQueryClient()

  const [question, setQuestion] =
    useState('')

  const [selected, setSelected] =
    useState<number[]>([])

  const [
    conversationId,
    setConversationId,
  ] = useState<number>()

  const [
    messages,
    setMessages,
  ] = useState<ChatMessage[]>([])

  const [
    streaming,
    setStreaming,
  ] = useState(false)

  const [
    loadingHistory,
    setLoadingHistory,
  ] = useState(false)

  const abortRef =
    useRef<AbortController | null>(
      null,
    )

  /**
   * 知识库列表
   */
  const {
    data: knowledgeBases = [],
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
   * 历史会话列表
   */
  const {
    data: conversations = [],
    isLoading:
      conversationsLoading,
  } = useQuery({
    queryKey: [
      'chat-conversations',
    ],

    queryFn: () =>
      api<Conversation[]>(
        '/api/chat/conversations',
      ),
  })

  /**
   * 第一次进入页面时，
   * 如果没有选择知识库，
   * 默认勾选第一个。
   */
  useEffect(() => {
    if (
      selected.length === 0 &&
      knowledgeBases.length > 0
    ) {
      setSelected([
        knowledgeBases[0].id,
      ])
    }
  }, [
    knowledgeBases,
    selected.length,
  ])

  /**
   * 打开一个历史会话
   */
  const openConversation =
    async (id: number) => {
      if (streaming) {
        message.warning(
          '请先停止当前回答',
        )
        return
      }

      setLoadingHistory(true)

      try {
        const history =
          await api<
            ChatMessage[]
          >(
            `/api/chat/conversations/${id}/messages`,
          )

        setConversationId(id)

        setMessages(
          history.map(
            (item) => ({
              ...item,

              citations:
                item.citations ??
                [],
            }),
          ),
        )
      } catch (error) {
        message.error(
          error instanceof Error
            ? error.message
            : '加载历史会话失败',
        )
      } finally {
        setLoadingHistory(false)
      }
    }

  /**
   * 新对话
   *
   * 不立即调用后端创建，
   * 用户发送第一条消息时
   * ChatService 自动创建。
   */
  const newConversation =
    () => {
      if (streaming) {
        abortRef.current?.abort()
      }

      setConversationId(
        undefined,
      )

      setMessages([])

      setQuestion('')
    }

  /**
   * 发送消息
   */
  const send = async () => {
    const prompt =
      question.trim()

    if (!prompt) {
      return
    }

    if (
      selected.length === 0
    ) {
      message.warning(
        '请至少选择一个知识库',
      )
      return
    }

    setQuestion('')

    /**
     * 先乐观插入：
     *
     * 用户消息
     * +
     * 一个空 AI 消息
     */
    setMessages((old) => [
      ...old,

      {
        role: 'USER',
        content: prompt,
      },

      {
        role: 'ASSISTANT',
        content: '',
        citations: [],
      },
    ])

    setStreaming(true)

    const controller =
      new AbortController()

    abortRef.current =
      controller

    try {
      await streamChat(
        {
          conversationId,
          question: prompt,
          knowledgeBaseIds:
            selected,
        },

        {
          /**
           * 后端返回 conversationId
           */
          onMeta: (meta) => {
            setConversationId(
              meta.conversationId,
            )

            /**
             * 新对话创建后，
             * 立即刷新左侧历史列表。
             */
            queryClient.invalidateQueries(
              {
                queryKey: [
                  'chat-conversations',
                ],
              },
            )
          },

          /**
           * SSE Token
           */
          onToken: (
            content,
          ) => {
            setMessages(
              (old) =>
                old.map(
                  (
                    item,
                    index,
                  ) =>
                    index ===
                    old.length -
                      1
                      ? {
                          ...item,

                          content:
                            item.content +
                            content,
                        }
                      : item,
                ),
            )
          },

          /**
           * SSE Citation
           */
          onCitation: (
            raw,
          ) => {
            setMessages(
              (old) =>
                old.map(
                  (
                    item,
                    index,
                  ) =>
                    index ===
                    old.length -
                      1
                      ? {
                          ...item,

                          citations: [
                            ...(
                              item.citations ??
                              []
                            ),

                            raw as unknown as Citation,
                          ],
                        }
                      : item,
                ),
            )
          },

          onDone: () => {
            queryClient.invalidateQueries(
              {
                queryKey: [
                  'chat-conversations',
                ],
              },
            )
          },

          onError: (
            text,
          ) => {
            message.error(text)
          },
        },

        controller.signal,
      )
    } catch (error) {
      if (
        !controller.signal
          .aborted
      ) {
        message.error(
          error instanceof Error
            ? error.message
            : '生成失败',
        )
      }
    } finally {
      setStreaming(false)

      abortRef.current = null
    }
  }

  /**
   * 将时间转成简单显示格式
   */
  const formatTime = (
    value?: string,
  ) => {
    if (!value) {
      return ''
    }

    const date =
      new Date(value)

    return date.toLocaleString(
      'zh-CN',
      {
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
      },
    )
  }

  return (
    <div className="chat-page">
      <div className="page-heading">
        <div>
          <Typography.Title
            level={2}
          >
            智能问答
          </Typography.Title>

          <Typography.Text
            type="secondary"
          >
            回答仅基于已完成向量化且当前租户有权访问的文档。
          </Typography.Text>
        </div>
      </div>

      <div className="chat-workspace">
        {/* 左侧历史会话 */}
        <aside className="chat-history">
          <div className="chat-history-header">
            <Typography.Text strong>
              历史对话
            </Typography.Text>

            <Button
              type="primary"
              size="small"
              icon={
                <PlusOutlined />
              }
              onClick={
                newConversation
              }
            >
              新对话
            </Button>
          </div>

          <div className="chat-history-list">
            {conversationsLoading ? (
              <div className="chat-history-loading">
                <Spin />
              </div>
            ) : conversations.length ===
              0 ? (
              <Empty
                image={
                  Empty.PRESENTED_IMAGE_SIMPLE
                }
                description="暂无历史对话"
              />
            ) : (
              <List
                split={false}
                dataSource={
                  conversations
                }
                renderItem={(
                  conversation,
                ) => {
                  const active =
                    conversation.id ===
                    conversationId

                  return (
                    <List.Item
                      className={
                        active
                          ? 'chat-history-item active'
                          : 'chat-history-item'
                      }
                      onClick={() =>
                        openConversation(
                          conversation.id,
                        )
                      }
                    >
                      <div className="chat-history-item-content">
                        <Space
                          size={8}
                          align="start"
                        >
                          <MessageOutlined />

                          <div>
                            <Typography.Text
                              strong={
                                active
                              }
                              ellipsis
                            >
                              {
                                conversation.title
                              }
                            </Typography.Text>

                            <div className="chat-history-time">
                              {formatTime(
                                conversation.updatedAt,
                              )}
                            </div>
                          </div>
                        </Space>
                      </div>
                    </List.Item>
                  )
                }}
              />
            )}
          </div>
        </aside>

        {/* 右侧聊天区域 */}
        <main className="chat-main">
          <Card
            className="kb-selector"
            size="small"
          >
            <Space
              direction="vertical"
              size={8}
            >
              <Typography.Text strong>
                检索知识库：
              </Typography.Text>

              <Checkbox.Group
                value={selected}
                onChange={(
                  values,
                ) =>
                  setSelected(
                    values as number[],
                  )
                }
                options={knowledgeBases.map(
                  (item) => ({
                    label:
                      item.name,
                    value:
                      item.id,
                  }),
                )}
              />
            </Space>
          </Card>

          <div className="chat-messages">
            {loadingHistory ? (
              <div className="chat-loading">
                <Spin tip="加载历史消息..." />
              </div>
            ) : messages.length ===
              0 ? (
              <Empty description="选择知识库后开始提问" />
            ) : (
              <List
                dataSource={
                  messages
                }
                renderItem={(
                  item,
                ) => (
                  <List.Item
                    className={`chat-row ${item.role.toLowerCase()}`}
                  >
                    <div className="chat-bubble">
                      <Typography.Text strong>
                        {item.role ===
                        'USER'
                          ? '你'
                          : 'KnowFlow'}
                      </Typography.Text>

                      <div className="markdown-body">
                        <ReactMarkdown>
                          {item.content ||
                            '正在生成…'}
                        </ReactMarkdown>
                      </div>

                      {(item.citations
                        ?.length ??
                        0) >
                        0 && (
                        <div className="citations">
                          <Typography.Text
                            type="secondary"
                          >
                            引用来源
                          </Typography.Text>

                          {item.citations!.map(
                            (
                              citation,
                            ) => (
                              <Card
                                size="small"
                                key={`${citation.documentId}-${citation.citationIndex}`}
                              >
                                <Space wrap>
                                  <Tag color="blue">
                                    [
                                    {
                                      citation.citationIndex
                                    }
                                    ]
                                  </Tag>

                                  <Typography.Text>
                                    {citation.documentName ??
                                      `文档 #${citation.documentId}`}
                                  </Typography.Text>

                                  {citation.pageNumber && (
                                    <Typography.Text
                                      type="secondary"
                                    >
                                      第{' '}
                                      {
                                        citation.pageNumber
                                      }{' '}
                                      页
                                    </Typography.Text>
                                  )}

                                  <Typography.Text
                                    type="secondary"
                                  >
                                    相似度{' '}
                                    {Number(
                                      citation.similarity,
                                    ).toFixed(
                                      3,
                                    )}
                                  </Typography.Text>
                                </Space>

                                <Typography.Paragraph className="citation-excerpt">
                                  {
                                    citation.excerpt
                                  }
                                </Typography.Paragraph>
                              </Card>
                            ),
                          )}
                        </div>
                      )}
                    </div>
                  </List.Item>
                )}
              />
            )}
          </div>

          <div className="chat-composer">
            <Input.TextArea
              value={question}
              onChange={(
                event,
              ) =>
                setQuestion(
                  event.target
                    .value,
                )
              }
              autoSize={{
                minRows: 2,
                maxRows: 5,
              }}
              placeholder="例如：报销超过 5000 元需要哪些审批？"
              onPressEnter={(
                event,
              ) => {
                if (
                  !event.shiftKey
                ) {
                  event.preventDefault()

                  if (
                    !streaming
                  ) {
                    send()
                  }
                }
              }}
            />

            {streaming ? (
              <Button
                danger
                icon={
                  <StopOutlined />
                }
                onClick={() =>
                  abortRef.current?.abort()
                }
              >
                停止
              </Button>
            ) : (
              <Button
                type="primary"
                icon={
                  <SendOutlined />
                }
                onClick={send}
              >
                发送
              </Button>
            )}
          </div>
        </main>
      </div>
    </div>
  )
}