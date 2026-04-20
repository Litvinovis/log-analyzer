import React from 'react'
import { Routes, Route, NavLink, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Layout, Menu, Typography } from 'antd'
import {
  AlertOutlined,
  BarChartOutlined,
  SearchOutlined,
  ThunderboltOutlined,
  UnorderedListOutlined,
} from '@ant-design/icons'
import ErrorsPage from './pages/ErrorsPage'
import StatsPage from './pages/StatsPage'
import TracePage from './pages/TracePage'
import AnalyzePage from './pages/AnalyzePage'
import StreamPage from './pages/StreamPage'

const { Sider, Content } = Layout

const NAV_ITEMS = [
  { key: '/stream',  icon: <UnorderedListOutlined />, label: 'Все логи' },
  { key: '/errors',  icon: <AlertOutlined />,         label: 'Ошибки' },
  { key: '/stats',   icon: <BarChartOutlined />,       label: 'Статистика' },
  { key: '/trace',   icon: <SearchOutlined />,         label: 'Трассировка' },
  { key: '/analyze', icon: <ThunderboltOutlined />,    label: 'Анализ' },
]

export default function App() {
  const location = useLocation()
  const navigate = useNavigate()

  return (
    <Layout style={{ minHeight: '100vh', background: '#13131f' }}>
      <Sider width={210} style={{ background: '#1a1a2e', borderRight: '1px solid #2d2d44' }}>
        <div style={{ padding: '18px 24px', borderBottom: '1px solid #2d2d44' }}>
          <Typography.Text strong style={{ color: '#e0e0ff', fontSize: 16, letterSpacing: 0.5 }}>
            Log Analyzer
          </Typography.Text>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          selectedKeys={[location.pathname]}
          onClick={({ key }) => navigate(key)}
          style={{ background: 'transparent', borderRight: 0, marginTop: 8 }}
          items={NAV_ITEMS.map(({ key, icon, label }) => ({ key, icon, label }))}
        />
      </Sider>
      <Layout style={{ background: '#13131f' }}>
        <Content style={{ padding: 24, minHeight: '100vh' }}>
          <Routes>
            <Route path="/" element={<Navigate to="/stream" replace />} />
            <Route path="/stream"  element={<StreamPage />} />
            <Route path="/errors"  element={<ErrorsPage />} />
            <Route path="/stats"   element={<StatsPage />} />
            <Route path="/trace"   element={<TracePage />} />
            <Route path="/analyze" element={<AnalyzePage />} />
          </Routes>
        </Content>
      </Layout>
    </Layout>
  )
}
