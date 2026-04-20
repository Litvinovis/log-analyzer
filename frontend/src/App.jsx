import React, { useState } from 'react'
import { Routes, Route, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Layout, Menu, Typography, ConfigProvider, theme, Tooltip } from 'antd'
import {
  AlertOutlined,
  BarChartOutlined,
  SearchOutlined,
  ThunderboltOutlined,
  UnorderedListOutlined,
  SunOutlined,
  MoonOutlined,
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

const DARK_TOKENS = {
  colorPrimary: '#4096ff',
  borderRadius: 6,
  colorBgContainer: '#1e1e2e',
  colorBgElevated: '#2a2a3e',
  colorBgLayout: '#13131f',
}

const LIGHT_TOKENS = {
  colorPrimary: '#1677ff',
  borderRadius: 6,
}

function AppShell({ isDark, onToggle }) {
  const location = useLocation()
  const navigate = useNavigate()

  const siderBg    = isDark ? '#1a1a2e' : '#001529'
  const siderBorder = isDark ? '#2d2d44' : '#002140'
  const layoutBg   = isDark ? '#13131f' : '#f0f2f5'
  const titleColor = '#e0e0ff'

  return (
    <Layout style={{ minHeight: '100vh', background: layoutBg }}>
      <Sider width={210} style={{ background: siderBg, borderRight: `1px solid ${siderBorder}` }}>
        <div style={{
          padding: '18px 24px',
          borderBottom: `1px solid ${siderBorder}`,
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
        }}>
          <Typography.Text strong style={{ color: titleColor, fontSize: 16, letterSpacing: 0.5 }}>
            Log Analyzer
          </Typography.Text>
          <Tooltip title={isDark ? 'Светлая тема' : 'Тёмная тема'}>
            <span
              onClick={onToggle}
              style={{ color: titleColor, cursor: 'pointer', fontSize: 16, lineHeight: 1 }}
            >
              {isDark ? <SunOutlined /> : <MoonOutlined />}
            </span>
          </Tooltip>
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
      <Layout style={{ background: layoutBg }}>
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

export default function App() {
  const [isDark, setIsDark] = useState(
    () => localStorage.getItem('theme') !== 'light'
  )

  const toggle = () => {
    const next = !isDark
    setIsDark(next)
    localStorage.setItem('theme', next ? 'dark' : 'light')
  }

  return (
    <ConfigProvider
      theme={{
        algorithm: isDark ? theme.darkAlgorithm : theme.defaultAlgorithm,
        token: isDark ? DARK_TOKENS : LIGHT_TOKENS,
      }}
    >
      <AppShell isDark={isDark} onToggle={toggle} />
    </ConfigProvider>
  )
}
