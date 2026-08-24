import {
  BookOutlined,
  FileTextOutlined,
  MessageOutlined,
  SafetyCertificateOutlined,
} from '@ant-design/icons'
import { useQuery } from '@tanstack/react-query'
import { Card, Col, Row, Statistic, Typography } from 'antd'
import { api } from '../api/client'
import type { KnowledgeBase } from '../types'

export function DashboardPage() {
  const { data = [] } = useQuery({
    queryKey: ['knowledge-bases'],
    queryFn: () => api<KnowledgeBase[]>('/api/knowledge-bases'),
  })

  return (
    <div>
      <Typography.Title level={2}>
        工作台
      </Typography.Title>

      <Typography.Paragraph type="secondary">
        当前版本已覆盖多租户认证、知识库管理、异步文档处理和 RAG 智能问答核心能力。
      </Typography.Paragraph>

      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic
              title="知识库"
              value={data.length}
              prefix={<BookOutlined />}
            />
          </Card>
        </Col>

        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic
              title="支持格式"
              value={4}
              suffix="种"
              prefix={<FileTextOutlined />}
            />
          </Card>
        </Col>

        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic
              title="检索模式"
              value="向量"
              prefix={<MessageOutlined />}
            />
          </Card>
        </Col>

        <Col xs={24} md={12} xl={6}>
          <Card>
            <Statistic
              title="租户隔离"
              value="已启用"
              prefix={<SafetyCertificateOutlined />}
            />
          </Card>
        </Col>
      </Row>
    </div>
  )
}