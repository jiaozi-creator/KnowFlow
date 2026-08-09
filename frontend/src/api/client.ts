import { useAuthStore } from '../store/auth'
import type { ApiResponse, AuthResponse } from '../types'

const API_BASE = import.meta.env.VITE_API_BASE_URL ?? ''

/**
 * 将 Response 安全解析为 ApiResponse。
 *
 * 不能直接 response.json()：
 * Spring Security 返回 401 / 403 时，有些情况下 body 可能为空，
 * 直接 response.json() 会产生：
 *
 * Unexpected end of JSON input
 */
async function parseApiResponse<T>(
  response: Response,
): Promise<ApiResponse<T> | null> {
  const text = await response.text()

  if (!text.trim()) {
    return null
  }

  try {
    return JSON.parse(text) as ApiResponse<T>
  } catch {
    return null
  }
}

/**
 * 根据 HTTP 状态码生成更明确的错误信息。
 */
function getHttpErrorMessage(status: number): string {
  switch (status) {
    case 400:
      return '请求参数错误'
    case 401:
      return '登录状态已失效，请重新登录'
    case 403:
      return '无权执行该操作'
    case 404:
      return '请求的资源不存在'
    case 413:
      return '上传文件过大'
    case 500:
      return '服务器内部错误'
    case 502:
      return '后端服务暂时不可用'
    case 503:
      return '服务暂时不可用'
    default:
      return `请求失败：HTTP ${status}`
  }
}

/**
 * 使用 Refresh Token 获取新的 Access Token。
 */
async function refreshAccessToken(): Promise<string | null> {
  const state = useAuthStore.getState()

  if (!state.refreshToken) {
    state.logout()
    return null
  }

  let response: Response

  try {
    response = await fetch(
      `${API_BASE}/api/auth/refresh`,
      {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
        },
        body: JSON.stringify({
          refreshToken: state.refreshToken,
        }),
      },
    )
  } catch {
    return null
  }

  const result =
    await parseApiResponse<AuthResponse>(response)

  if (
    !response.ok ||
    !result ||
    result.code !== 0 ||
    !result.data
  ) {
    state.logout()
    return null
  }

  state.setAuth(result.data)

  return result.data.accessToken
}

/**
 * 普通 REST API 请求封装。
 */
export async function api<T>(
  path: string,
  init: RequestInit = {},
  retry = true,
): Promise<T> {
  const token =
    useAuthStore.getState().accessToken

  const headers =
    new Headers(init.headers)

  /*
   * FormData 上传文件时不能手动设置 Content-Type。
   * 浏览器会自动附带 multipart boundary。
   */
  if (
    init.body &&
    !(init.body instanceof FormData)
  ) {
    headers.set(
      'Content-Type',
      'application/json',
    )
  }

  if (token) {
    headers.set(
      'Authorization',
      `Bearer ${token}`,
    )
  }

  let response: Response

  try {
    response = await fetch(
      `${API_BASE}${path}`,
      {
        ...init,
        headers,
      },
    )
  } catch {
    throw new Error(
      '网络连接失败，请检查后端服务是否正常运行',
    )
  }

  /*
   * Access Token 过期。
   * 尝试使用 Refresh Token 刷新一次。
   *
   * 403 不在这里刷新，因为 403 表示：
   * 用户已经登录，但没有访问权限。
   */
  if (
    response.status === 401 &&
    retry &&
    !path.startsWith('/api/auth/')
  ) {
    const newToken =
      await refreshAccessToken()

    if (newToken) {
      return api<T>(
        path,
        init,
        false,
      )
    }

    throw new Error(
      '登录状态已失效，请重新登录',
    )
  }

  /*
   * 204 No Content
   */
  if (response.status === 204) {
    return undefined as T
  }

  const result =
    await parseApiResponse<T>(response)

  /*
   * Spring Security 或 Nginx 返回空 body 时，
   * 不再执行 response.json()。
   */
  if (!result) {
    if (!response.ok) {
      throw new Error(
        getHttpErrorMessage(
          response.status,
        ),
      )
    }

    return undefined as T
  }

  if (
    !response.ok ||
    result.code !== 0
  ) {
    throw new Error(
      result.message ||
        getHttpErrorMessage(
          response.status,
        ),
    )
  }

  return result.data
}

/**
 * SSE 事件回调。
 */
export interface StreamHandlers {
  onMeta?: (
    data: {
      conversationId: number
      messageId: number
    },
  ) => void

  onToken?: (
    content: string,
  ) => void

  onCitation?: (
    citation: Record<
      string,
      unknown
    >,
  ) => void

  onDone?: () => void

  onError?: (
    message: string,
  ) => void
}

/**
 * 从错误响应中提取 message。
 */
async function readErrorMessage(
  response: Response,
): Promise<string> {
  const text =
    await response.text()

  if (!text.trim()) {
    return getHttpErrorMessage(
      response.status,
    )
  }

  try {
    const result =
      JSON.parse(text) as Partial<
        ApiResponse<unknown>
      >

    return (
      result.message ||
      getHttpErrorMessage(
        response.status,
      )
    )
  } catch {
    return text.length <= 200
      ? text
      : getHttpErrorMessage(
          response.status,
        )
  }
}

/**
 * SSE 智能问答。
 */
export async function streamChat(
  body: {
    conversationId?: number
    question: string
    knowledgeBaseIds: number[]
  },
  handlers: StreamHandlers,
  signal?: AbortSignal,
): Promise<void> {
  return streamChatInternal(
    body,
    handlers,
    signal,
    true,
  )
}

/**
 * SSE 内部实现。
 */
async function streamChatInternal(
  body: {
    conversationId?: number
    question: string
    knowledgeBaseIds: number[]
  },
  handlers: StreamHandlers,
  signal: AbortSignal | undefined,
  retry: boolean,
): Promise<void> {
  const token =
    useAuthStore.getState()
      .accessToken

  const headers: HeadersInit = {
    'Content-Type':
      'application/json',
  }

  if (token) {
    headers.Authorization =
      `Bearer ${token}`
  }

  let response: Response

  try {
    response = await fetch(
      `${API_BASE}/api/chat/stream`,
      {
        method: 'POST',
        headers,
        body: JSON.stringify(body),
        signal,
      },
    )
  } catch (error) {
    /*
     * 用户主动点击“停止生成”时，
     * AbortError 不应该提示 network error。
     */
    if (
      error instanceof DOMException &&
      error.name === 'AbortError'
    ) {
      return
    }

    throw new Error(
      '网络连接失败，请检查后端服务是否正常运行',
    )
  }

  /*
   * SSE 请求同样支持 Token 自动刷新。
   */
  if (
    response.status === 401 &&
    retry
  ) {
    const newToken =
      await refreshAccessToken()

    if (newToken) {
      return streamChatInternal(
        body,
        handlers,
        signal,
        false,
      )
    }

    throw new Error(
      '登录状态已失效，请重新登录',
    )
  }

  if (!response.ok) {
    const message =
      await readErrorMessage(
        response,
      )

    throw new Error(message)
  }

  if (!response.body) {
    throw new Error(
      '服务器未返回流式响应',
    )
  }

  const reader =
    response.body.getReader()

  const decoder =
    new TextDecoder()

  let buffer = ''
  let doneReceived = false

  while (true) {
    let result: ReadableStreamReadResult<Uint8Array>

    try {
      result = await reader.read()
    } catch (error) {
      // 后端已经发送 done 事件后关闭 SSE，
      // 不应该再被前端视为 network error
      if (doneReceived) {
        break
      }

      throw error
    }

    const { done, value } = result
    if (done) {
      break
    }

    /*
     * SSE 标准使用空行分隔事件。
     * Windows / Linux 换行统一转换为 \n。
     */
    buffer += decoder
      .decode(
        value,
        {
          stream: true,
        },
      )
      .replace(
        /\r\n/g,
        '\n',
      )

    const events =
      buffer.split('\n\n')

    /*
     * 最后一段可能还没有接收完整，
     * 留到下一次读取。
     */
    buffer =
      events.pop() ?? ''

    for (
      const block of events
    ) {
      let event = 'message'
      const dataLines: string[] =
        []

      for (
        const line of block.split(
          '\n',
        )
      ) {
        if (
          line.startsWith(
            'event:',
          )
        ) {
          event = line
            .slice(6)
            .trim()
        }

        if (
          line.startsWith(
            'data:',
          )
        ) {
          dataLines.push(
            line
              .slice(5)
              .trim(),
          )
        }
      }

      const data =
        dataLines.join('\n')

      if (!data) {
        continue
      }

      let parsed:
        Record<
          string,
          unknown
        >

      try {
        parsed =
          JSON.parse(data) as Record<
            string,
            unknown
          >
      } catch {
        /*
         * 避免单个异常 SSE 数据包
         * 导致整个聊天流程崩溃。
         */
        continue
      }

      switch (event) {
        case 'meta':
          handlers.onMeta?.(
            parsed as {
              conversationId:
                number
              messageId: number
            },
          )
          break

        case 'token':
          handlers.onToken?.(
            String(
              parsed.content ??
                '',
            ),
          )
          break

        case 'citation':
          handlers.onCitation?.(
            parsed,
          )
          break

        case 'done':
          doneReceived = true
          handlers.onDone?.()
          break

        case 'error':
          handlers.onError?.(
            String(
              parsed.message ??
                '生成失败',
            ),
          )
          break

        default:
          break
      }
    }
  }
}