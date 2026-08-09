import {
  Navigate,
  createBrowserRouter,
} from 'react-router-dom'
import { MembersPage } from './pages/MembersPage'
import { AppLayout } from './components/AppLayout'
import { ProtectedRoute } from './components/ProtectedRoute'

import { LoginPage } from './pages/LoginPage'
import { RegisterPage } from './pages/RegisterPage'

import { DashboardPage } from './pages/DashboardPage'
import { KnowledgeBasesPage } from './pages/KnowledgeBasesPage'
import { DocumentsPage } from './pages/DocumentsPage'
import { ChatPage } from './pages/ChatPage'
import { DepartmentsPage } from './pages/DepartmentsPage'

export const router = createBrowserRouter([
  /**
   * 登录
   */
  {
    path: '/login',
    element: <LoginPage />,
  },

  /**
   * 注册
   */
  {
    path: '/register',
    element: <RegisterPage />,
  },

  /**
   * 登录后的系统
   */
  {
    path: '/app',

    element: (
      <ProtectedRoute>
        <AppLayout />
      </ProtectedRoute>
    ),

    children: [
      /**
       * /app
       * 自动跳转工作台
       */
      {
        index: true,

        element: (
          <Navigate
            to="dashboard"
            replace
          />
        ),
      },

      /**
       * 工作台
       *
       * /app/dashboard
       */
      {
        path: 'dashboard',
        element: <DashboardPage />,
      },

      /**
       * 知识库
       *
       * /app/knowledge-bases
       */
      {
        path: 'knowledge-bases',
        element: <KnowledgeBasesPage />,
      },

      /**
       * 知识库文档
       *
       * /app/knowledge-bases/:knowledgeBaseId/documents
       */
      {
        path: 'knowledge-bases/:knowledgeBaseId/documents',
        element: <DocumentsPage />,
      },

      /**
       * 智能问答
       *
       * /app/chat
       */
      {
        path: 'chat',
        element: <ChatPage />,
      },

      /**
       * 部门管理
       *
       * /app/departments
       */
      {
        path: 'departments',
        element: <DepartmentsPage />,
      },
      {
      path: 'members',
      element: <MembersPage />,
      },
    ],
  },

  /**
   * 未知地址统一回到系统首页
   */
  {
    path: '*',

    element: (
      <Navigate
        to="/app"
        replace
      />
    ),
  },
])