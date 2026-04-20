import React from 'react'
import { Routes, Route, NavLink, Navigate } from 'react-router-dom'
import { Layout, Menu, Typography } from 'antd'
import {
  AlertOutlined,
  BarChartOutlined,
  SearchOutlined,
  ThunderboltOutlined,
} from '@ant-design/icons'
import ErrorsPage from './pages/ErrorsPage'
import StatsPage from './pages/StatsPage'
import TracePage from './pages/TracePage'
import AnalyzePage from './pages/AnalyzePage'

const { Sider, Content, Header } = Layout

const NAV_ITEMS = [
  { key: '/errors',  icon: <AlertOutlined />,       label: 'Ошибки' },
  { key: '/stats',   icon: <BarChartOutlined />,     label: 'Статистика' },
  { key: '/trace',   icon: <SearchOutlined />,       label: 'Трассировка' },
  { key: '/analyze', icon: <ThunderboltOutlined />,  label: 'Анализ' },
]

export default function App() {
  return (
    <Layout style={{ minHeight: '100vh' }}>
      <Sider width={200} theme="dark">
        <div style={{ padding: '16px 24px', borderBottom: '1px solid #303030' }}>
          <Typography.Text strong style={{ color: '#fff', fontSize: 16 }}>
            Log Analyzer
          </Typography.Text>
        </div>
        <Menu
          theme="dark"
          mode="inline"
          style={{ borderRight: 0 }}
          items={NAV_ITEMS.map(item => ({
            key: item.key,
            icon: item.icon,
            label: <NavLink to={item.key}>{item.label}</NavLink>,
          }))}
        />
      </Sider>
      <Layout>
        <Content style={{ padding: 24, background: '#f5f5f5', minHeight: '100vh' }}>
          <Routes>
            <Route path="/" element={<Navigate to="/errors" replace />} />
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
