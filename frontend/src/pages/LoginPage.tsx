import { App, Button, Card, Form, Input, Typography } from 'antd'
import { Link, useNavigate } from 'react-router-dom'
import { api } from '../api/client'
import { useAuthStore } from '../store/auth'
import type { AuthResponse } from '../types'

export function LoginPage() {
  const { message } = App.useApp()
  const navigate = useNavigate()
  const setAuth = useAuthStore((state) => state.setAuth)

  const submit = async (values: { email: string; password: string }) => {
    try {
      const auth = await api<AuthResponse>('/api/auth/login', { method: 'POST', body: JSON.stringify(values) })
      setAuth(auth)
      navigate('/app')
    } catch (error) {
      message.error(error instanceof Error ? error.message : '登录失败')
    }
  }

  return (
    <div className="auth-page">
      <Card className="auth-card">
        <Typography.Title level={2}>登录 KnowFlow</Typography.Title>
        <Typography.Paragraph type="secondary">进入企业内部知识库</Typography.Paragraph>
        <Form layout="vertical" onFinish={submit} requiredMark={false}>
          <Form.Item label="邮箱" name="email" rules={[{ required: true }, { type: 'email' }]}>
            <Input size="large" placeholder="name@company.com" />
          </Form.Item>
          <Form.Item label="密码" name="password" rules={[{ required: true }]}>
            <Input.Password size="large" />
          </Form.Item>
          <Button type="primary" htmlType="submit" size="large" block>登录</Button>
        </Form>
        <Typography.Paragraph className="auth-footer">没有账号？<Link to="/register">创建企业账号</Link></Typography.Paragraph>
      </Card>
    </div>
  )
}
