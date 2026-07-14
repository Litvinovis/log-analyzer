import React, { useState } from 'react'
import {
  Card, Form, Select, Button, Row, Col, Statistic,
  Table, Space, Alert, DatePicker, Spin, Typography, Empty, Tooltip as AntTooltip,
} from 'antd'
import {
  BarChart, Bar, XAxis, YAxis, CartesianGrid, Tooltip, ResponsiveContainer, Cell,
} from 'recharts'
import { SearchOutlined, LinkOutlined } from '@ant-design/icons'
import { useNavigate } from 'react-router-dom'
import dayjs from 'dayjs'
import { logsApi } from '../api/logsApi'
import { useApps } from '../hooks/useApps'
import { useUi, CHART, RANGE_PRESETS, LEVELS } from '../lib/ui'

const { RangePicker } = DatePicker
const { Text } = Typography

// Порядок уровней фиксированный (ordinal severity), не зависит от данных
const LEVEL_ORDER = [...LEVELS].reverse() // FATAL … TRACE

export default function StatsPage() {
  const appOptions = useApps()
  const navigate = useNavigate()
  const { isDark } = useUi()
  const C = CHART[isDark ? 'dark' : 'light']
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
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([h, cnt]) => ({ iso: h, label: dayjs(h).format('DD.MM HH:00'), count: cnt }))
    : []

  const byLevelData = stats
    ? LEVEL_ORDER.filter(l => stats.byLevel[l]).map(l => ({ name: l, value: stats.byLevel[l] }))
    : []

  const byAppData = stats
    ? Object.entries(stats.byApp).map(([name, value]) => ({ name, value }))
        .sort((a, b) => b.value - a.value)
    : []

  const errorRate = stats && stats.totalScanned > 0
    ? (stats.totalErrors / stats.totalScanned * 100)
    : 0

  const maxMsgCount = stats?.topMessages?.[0]?.count ?? 1

  const topMsgColumns = [
    { title: '#', render: (_, __, i) => <Text type="secondary">{i + 1}</Text>, width: 42 },
    {
      title: 'Сообщение', dataIndex: 'message', ellipsis: true,
      render: (v) => <span className="log-mono">{v}</span>,
    },
    {
      title: 'Доля', dataIndex: 'count', width: 180,
      render: (v) => (
        <Space size={8}>
          <div style={{
            width: 90, height: 8, borderRadius: 4,
            background: isDark ? '#2d2d44' : '#eee', overflow: 'hidden',
          }}>
            <div style={{
              width: `${Math.max(4, v / maxMsgCount * 100)}%`, height: '100%',
              background: C.error, borderRadius: 4,
            }} />
          </div>
          <Text className="log-mono" style={{ fontSize: 12 }}>{v}</Text>
        </Space>
      ),
    },
    {
      title: '', width: 46,
      render: (_, rec) => (
        <AntTooltip title="Найти эти записи в «Ошибках»">
          <Button
            type="text" size="small" icon={<LinkOutlined />}
            onClick={() => navigate(`/errors?contains=${encodeURIComponent(rec.message.slice(0, 80))}`)}
          />
        </AntTooltip>
      ),
    },
  ]

  const axisProps = {
    tick: { fontSize: 11, fill: C.tick },
    axisLine: { stroke: C.grid },
    tickLine: { stroke: C.grid },
  }
  const tooltipStyle = {
    contentStyle: {
      background: isDark ? '#2a2a3e' : '#fff',
      border: `1px solid ${C.grid}`,
      borderRadius: 6,
      fontSize: 12,
    },
  }

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card size="small">
        <Form form={form} layout="inline" onFinish={search}
              initialValues={{ range: [dayjs().subtract(24, 'hour'), dayjs()] }}>
          <Form.Item name="app" label="Приложение">
            <Select mode="multiple" placeholder="Все приложения" allowClear style={{ width: 220 }} options={appOptions} maxTagCount="responsive" />
          </Form.Item>
          <Form.Item name="range" label="Период">
            <RangePicker showTime presets={RANGE_PRESETS} />
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
          <Row gutter={12}>
            <Col span={6}>
              <Card size="small"><Statistic title="Просканировано строк" value={stats.totalScanned} /></Card>
            </Col>
            <Col span={6}>
              <Card size="small">
                <Statistic title="Ошибок (ERROR + FATAL)" value={stats.totalErrors}
                  valueStyle={{ color: stats.totalErrors > 0 ? C.error : undefined }} />
              </Card>
            </Col>
            <Col span={6}>
              <Card size="small">
                <Statistic title="Доля ошибок" value={errorRate} precision={errorRate < 1 ? 3 : 1} suffix="%"
                  valueStyle={{ color: errorRate > 1 ? C.error : undefined }} />
              </Card>
            </Col>
            <Col span={6}>
              <Card size="small"><Statistic title="Приложений с ошибками" value={Object.keys(stats.byApp).length} /></Card>
            </Col>
          </Row>

          <Card size="small" title="Ошибки по часам">
            {byHourData.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Ошибок нет" /> : (
              <ResponsiveContainer width="100%" height={240}>
                <BarChart data={byHourData} margin={{ top: 4, right: 8, left: -16, bottom: 0 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke={C.grid} vertical={false} />
                  <XAxis dataKey="label" {...axisProps} minTickGap={24} />
                  <YAxis {...axisProps} allowDecimals={false} />
                  <Tooltip {...tooltipStyle} cursor={{ fill: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)' }}
                    formatter={(v) => [v, 'ошибок']} labelFormatter={(l) => l} />
                  <Bar dataKey="count" fill={C.error} radius={[4, 4, 0, 0]} maxBarSize={28} />
                </BarChart>
              </ResponsiveContainer>
            )}
          </Card>

          <Row gutter={12}>
            <Col span={9}>
              <Card size="small" title="По уровням">
                {byLevelData.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} /> : (
                  <ResponsiveContainer width="100%" height={Math.max(120, byLevelData.length * 42)}>
                    <BarChart data={byLevelData} layout="vertical" margin={{ top: 4, right: 40, left: 0, bottom: 0 }}>
                      <XAxis type="number" hide />
                      <YAxis dataKey="name" type="category" width={64} {...axisProps} />
                      <Tooltip {...tooltipStyle} cursor={{ fill: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)' }}
                        formatter={(v) => [v, 'записей']} />
                      <Bar dataKey="value" fill={C.accent} radius={[0, 4, 4, 0]} maxBarSize={22}
                           label={{ position: 'right', fontSize: 11, fill: C.tick }}>
                        {byLevelData.map((e) => (
                          <Cell key={e.name} fill={['ERROR', 'FATAL'].includes(e.name) ? C.error : C.accent} />
                        ))}
                      </Bar>
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </Card>
            </Col>
            <Col span={15}>
              <Card size="small" title="По приложениям">
                {byAppData.length === 0 ? <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} /> : (
                  <ResponsiveContainer width="100%" height={Math.max(120, byAppData.length * 42)}>
                    <BarChart data={byAppData} layout="vertical" margin={{ top: 4, right: 40, left: 0, bottom: 0 }}>
                      <XAxis type="number" hide />
                      <YAxis dataKey="name" type="category" width={150} {...axisProps} />
                      <Tooltip {...tooltipStyle} cursor={{ fill: isDark ? 'rgba(255,255,255,0.04)' : 'rgba(0,0,0,0.04)' }}
                        formatter={(v) => [v, 'ошибок']} />
                      <Bar dataKey="value" fill={C.accent} radius={[0, 4, 4, 0]} maxBarSize={22}
                           label={{ position: 'right', fontSize: 11, fill: C.tick }} />
                    </BarChart>
                  </ResponsiveContainer>
                )}
              </Card>
            </Col>
          </Row>

          <Card size="small" title="Топ повторяющихся сообщений">
            <Table
              columns={topMsgColumns}
              dataSource={stats.topMessages.map((m, i) => ({ key: i, ...m }))}
              pagination={false}
              size="small"
            />
          </Card>
        </>
      )}
    </Space>
  )
}
