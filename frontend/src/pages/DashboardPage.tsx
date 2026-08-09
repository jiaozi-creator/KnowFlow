import { BookOutlined, FileTextOutlined, MessageOutlined, SafetyCertificateOutlined } from '@ant-design/icons'
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
      <Typography.Title level={2}>工作台</Typography.Title>
      <Typography.Paragraph type="secondary">当前版本已覆盖多租户认证、知识库管理、异步文档处理和 RAG 问答核心闭环。</Typography.Paragraph>
      <Row gutter={[16, 16]}>
        <Col xs={24} md={12} xl={6}><Card><Statistic title="知识库" value={data.length} prefix={<BookOutlined />} /></Card></Col>
        <Col xs={24} md={12} xl={6}><Card><Statistic title="支持格式" value={4} suffix="种" prefix={<FileTextOutlined />} /></Card></Col>
        <Col xs={24} md={12} xl={6}><Card><Statistic title="检索模式" value="向量" prefix={<MessageOutlined />} /></Card></Col>
        <Col xs={24} md={12} xl={6}><Card><Statistic title="租户隔离" value="已启用" prefix={<SafetyCertificateOutlined />} /></Card></Col>
      </Row>
      <Card className="section-card" title="建议演示流程">
        <ol className="steps-list">
          <li>创建“企业制度库”或“技术文档库”。</li>
          <li>上传 PDF、DOCX、Markdown 或 TXT 文件。</li>
          <li>等待 RabbitMQ Worker 完成解析、切片和向量化。</li>
          <li>进入智能问答，选择知识库并提问。</li>
          <li>查看回答、引用片段和 PDF 原文。</li>
        </ol>
      </Card>
    </div>
  )
}
