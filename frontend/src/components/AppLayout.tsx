import {
  ApartmentOutlined,
  BookOutlined,
  DashboardOutlined,
  LogoutOutlined,
  MessageOutlined,
  TeamOutlined,
} from '@ant-design/icons'
import {
  Avatar,
  Button,
  Layout,
  Menu,
  Space,
  Typography,
} from 'antd'
import type {
  MenuProps,
} from 'antd'
import {
  Outlet,
  useLocation,
  useNavigate,
} from 'react-router-dom'

import { useAuthStore } from '../store/auth'

const {
  Header,
  Sider,
  Content,
} = Layout

export function AppLayout() {
  const navigate =
    useNavigate()

  const location =
    useLocation()

  const user =
    useAuthStore(
      (state) =>
        state.user,
    )

  const logout =
    useAuthStore(
      (state) =>
        state.logout,
    )

  /**
   * ============================================================
   * 当前用户是否为企业管理员
   * ============================================================
   *
   * OWNER：
   * 企业所有者
   *
   * ADMIN：
   * 企业管理员
   *
   * MEMBER：
   * 普通企业成员
   */
  const isTenantAdmin =
    user?.organizationRole ===
      'OWNER' ||
    user?.organizationRole ===
      'ADMIN'

  /**
   * ============================================================
   * 当前菜单选中状态
   * ============================================================
   */
  const selected =
    location.pathname.includes(
      '/knowledge-bases',
    )
      ? '/app/knowledge-bases'
      : location.pathname.includes(
            '/chat',
          )
        ? '/app/chat'
        : location.pathname.includes(
              '/departments',
            )
          ? '/app/departments'
          : location.pathname.includes(
                '/members',
              )
            ? '/app/members'
            : '/app/dashboard'

  /**
   * ============================================================
   * 左侧菜单
   * ============================================================
   *
   * 普通 MEMBER 不显示：
   *
   * 部门管理
   * 成员管理
   */
  const menuItems:
    MenuProps['items'] = [
    {
      key: '/app/dashboard',

      icon: (
        <DashboardOutlined />
      ),

      label: '工作台',
    },

    {
      key: '/app/knowledge-bases',

      icon: (
        <BookOutlined />
      ),

      label: '知识库',
    },

    {
      key: '/app/chat',

      icon: (
        <MessageOutlined />
      ),

      label: '智能问答',
    },

    /*
     * OWNER / ADMIN
     * 才显示组织管理菜单。
     */
    ...(isTenantAdmin
      ? [
          {
            key: '/app/departments',

            icon: (
              <ApartmentOutlined />
            ),

            label: '部门管理',
          },

          {
            key: '/app/members',

            icon: (
              <TeamOutlined />
            ),

            label: '成员管理',
          },
        ]
      : []),
  ]

  /**
   * 用户头像显示名称首字。
   */
  const avatarText =
    user?.displayName
      ?.trim()
      ?.slice(
        0,
        1,
      ) || 'U'

  return (
    <Layout className="app-shell">
      {/* ======================================================
          左侧导航
          ====================================================== */}
      <Sider
        width={232}
        theme="light"
        className="app-sider"
      >
        <div className="brand">
          KnowFlow
        </div>

        <Menu
          mode="inline"
          selectedKeys={[
            selected,
          ]}
          items={
            menuItems
          }
          onClick={({
            key,
          }) => {
            navigate(key)
          }}
        />
      </Sider>

      {/* ======================================================
          右侧主体
          ====================================================== */}
      <Layout>
        {/* ====================================================
            顶部
            ==================================================== */}
        <Header className="app-header">
          <Typography.Text
            type="secondary"
          >
            企业内部智能知识库与 RAG 问答平台
          </Typography.Text>

          <Space
            size={12}
            align="center"
          >
            <Avatar>
              {avatarText}
            </Avatar>

            <div className="user-meta">
              <Typography.Text
                strong
              >
                {user?.displayName ||
                  '用户'}
              </Typography.Text>

              <Typography.Text
                type="secondary"
                className="small-text"
              >
                {user?.organizationRole ||
                  ''}
              </Typography.Text>
            </div>

            <Button
              type="text"
              icon={
                <LogoutOutlined />
              }
              onClick={() => {
                logout()

                navigate(
                  '/login',
                )
              }}
            >
              退出
            </Button>
          </Space>
        </Header>

        {/* ====================================================
            页面主体
            ==================================================== */}
        <Content className="app-content">
          <Outlet />
        </Content>
      </Layout>
    </Layout>
  )
}