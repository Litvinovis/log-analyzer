import React, { useState } from 'react'
import {
  Card, Form, Select, Button, Row, Col, Statistic,
  Table, Space, Alert, DatePicker, Spin,
} from 'antd'
import {
  BarChart, Bar, PieChart, Pie, Cell, XAxis, YAxis,
  CartesianGrid, Tooltip, Legend, ResponsiveContainer,
} from 'recharts'
import { SearchOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { logsApi } from '../api/logsApi'
import { useApps } from '../hooks/useApps'

const { RangePicker } = DatePicker

const LEVEL_COLORS = {
  ERROR: '#ff4d4f',
  FATAL: '#a61d24',
  WARN:  '#fa8c16',
  INFO:  '#1677ff',
  DEBUG: '#8c8c8c',
  TRACE: '#d9d9d9',
}
const PIE_COLORS = Object.values(LEVEL_COLORS)

export default function StatsPage() {
  const appOptions = useApps()
  const [form] = Form.useForm()
  const [stats, setStats] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)

  const search = async () => {
    const values = form.getFieldsValue()
    const [from, to] = values.range || []
    setLoading(true)
    setError(null)
    try {
      const result = await logsApi.getStats({
        app: values.app?.join(',') || undefined,
        from: from ? from.toISOString() : undefined,
        to:   to   ? to.toISOString()   : undefined,
      })
      setStats(result)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  const byHourData = stats
    ? Object.entries(stats.byHour)
        .map(([h, cnt]) => ({ hour: dayjs(h).format('HH:mm DD.MM'), count: cnt }))
        .sort((a, b) => a.hour.localeCompare(b.hour))
    : []

  const byLevelData = stats
    ? Object.entries(stats.byLevel).map(([name, value]) => ({ name, value }))
    : []

  const byAppData = stats
    ? Object.entries(stats.byApp).map(([name, value]) => ({ name, value }))
    : []

  const topMsgColumns = [
    { title: '#', render: (_, __, i) => i + 1, width: 50 },
    { title: 'Сообщение', dataIndex: 'message', ellipsis: true },
    { title: 'Кол-во', dataIndex: 'count', width: 90, sorter: (a, b) => a.count - b.count },
  ]

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card title="Параметры">
        <Form form={form} layout="inline" onFinish={search}
              initialValues={{ range: [dayjs().subtract(24, 'hour'), dayjs()] }}>
          <Form.Item name="app" label="Приложение">
            <Select mode="multiple" placeholder="Все приложения" allowClear style={{ width: 220 }} options={appOptions} />
          </Form.Item>
          <Form.Item name="range" label="Период">
            <RangePicker showTime />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={loading}>
              Загрузить
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {error && <Alert type="error" message={error} showIcon />}

      {loading && <Spin size="large" style={{ display: 'block', margin: '40px auto' }} />}

      {stats && !loading && (
        <>
          <Row gutter={16}>
            <Col span={6}>
              <Card><Statistic title="Всего строк" value={stats.totalScanned} /></Card>
            </Col>
            <Col span={6}>
              <Card><Statistic title="Всего ошибок" value={stats.totalErrors} valueStyle={{ color: '#cf1322' }} /></Card>
            </Col>
            <Col span={6}>
              <Card><Statistic title="Уникальных уровней" value={Object.keys(stats.byLevel).length} /></Card>
            </Col>
            <Col span={6}>
              <Card><Statistic title="Приложений" value={Object.keys(stats.byApp).length} /></Card>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={14}>
              <Card title="Ошибки по времени">
                <ResponsiveContainer width="100%" height={250}>
                  <BarChart data={byHourData}>
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis dataKey="hour" tick={{ fontSize: 11 }} />
                    <YAxis />
                    <Tooltip />
                    <Bar dataKey="count" fill="#ff4d4f" name="Ошибок" />
                  </BarChart>
                </ResponsiveContainer>
              </Card>
            </Col>
            <Col span={10}>
              <Card title="По уровням">
                <ResponsiveContainer width="100%" height={250}>
                  <PieChart>
                    <Pie data={byLevelData} dataKey="value" nameKey="name" outerRadius={90} label={({ name, percent }) => `${name} ${(percent * 100).toFixed(0)}%`}>
                      {byLevelData.map((entry, i) => (
                        <Cell key={entry.name} fill={LEVEL_COLORS[entry.name] ?? PIE_COLORS[i % PIE_COLORS.length]} />
                      ))}
                    </Pie>
                    <Tooltip />
                  </PieChart>
                </ResponsiveContainer>
              </Card>
            </Col>
          </Row>

          <Row gutter={16}>
            <Col span={10}>
              <Card title="По приложениям">
                <ResponsiveContainer width="100%" height={250}>
                  <BarChart data={byAppData} layout="vertical">
                    <CartesianGrid strokeDasharray="3 3" />
                    <XAxis type="number" />
                    <YAxis dataKey="name" type="category" width={140} tick={{ fontSize: 11 }} />
                    <Tooltip />
                    <Bar dataKey="value" fill="#1677ff" name="Ошибок" />
                  </BarChart>
                </ResponsiveContainer>
              </Card>
            </Col>
            <Col span={14}>
              <Card title="Топ сообщений">
                <Table
                  columns={topMsgColumns}
                  dataSource={stats.topMessages.map((m, i) => ({ key: i, ...m }))}
                  pagination={false}
                  size="small"
                />
              </Card>
            </Col>
          </Row>
        </>
      )}
    </Space>
  )
}
