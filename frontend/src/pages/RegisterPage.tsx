import { App, Button, Card, Form, Input, Typography } from 'antd'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useAuthStore } from '../store/auth'
import type { AuthResponse } from '../types'

export function RegisterPage() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const setAuth = useAuthStore((state) => state.setAuth)

  const submit = async (values: Record<string, string>) => {
    try {
      const auth = await api<AuthResponse>('/api/auth/register', { method: 'POST', body: JSON.stringify(values) })
      setAuth(auth)
      navigate('/app')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '注册失败')
    }
  }

  return (
    <div className="auth-page">
      <Card className="auth-card">
        <Typography.Title level={2}>创建 KnowFlow 企业</Typography.Title>
        <Form layout="vertical" onFinish={submit} requiredMark={false}>
          <Form.Item label="企业名称" name="organizationName" rules={[{ required: true }]}>
            <Input size="large" placeholder="示例科技有限公司" />
          </Form.Item>
          <Form.Item label="姓名" name="displayName" rules={[{ required: true }]}>
            <Input size="large" />
          </Form.Item>
          <Form.Item label="邮箱" name="email" rules={[{ required: true }, { type: 'email' }]}>
            <Input size="large" />
          </Form.Item>
          <Form.Item label="密码" name="password" rules={[{ required: true }, { min: 8 }]}>
            <Input.Password size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" size="large" block>创建并进入</Button>
        </Form>
        <Typography.Paragraph className="auth-footer">已有账号？<Link to="/login">返回登录</Link></Typography.Paragraph>
      </Card>
    </div>
  )
}
