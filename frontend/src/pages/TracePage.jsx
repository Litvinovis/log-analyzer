import React, { useState, useEffect } from 'react'
import {
  Card, Form, Input, Button, Alert, Space, Table,
  Typography, Tag, Collapse, Empty, Badge, Select, Segmented,
} from 'antd'
import { SearchOutlined, UnorderedListOutlined, AppstoreOutlined } from '@ant-design/icons'
import { useSearchParams } from 'react-router-dom'
import dayjs from 'dayjs'
import { logsApi } from '../api/logsApi'
import LevelTag from '../components/LevelTag'
import TimeCell from '../components/TimeCell'
import StackTrace from '../components/StackTrace'
import { useApps } from '../hooks/useApps'
import { rowLevelClass, getAppColor } from '../lib/ui'

const { Text } = Typography

const MIN_ID_LENGTH = 3

const TIME_WINDOWS = [
  { value: 10,    label: '10 минут' },
  { value: 30,    label: '30 минут' },
  { value: 60,    label: '1 час' },
  { value: 120,   label: '2 часа' },
  { value: 480,   label: '8 часов' },
  { value: 1440,  label: '24 часа' },
]

/** Человекочитаемая длительность: мс → с → мин → ч. */
function fmtDuration(ms) {
  if (ms < 1000) return `${ms}мс`
  if (ms < 60_000) return `${(ms / 1000).toFixed(2)}с`
  if (ms < 3_600_000) return `${Math.floor(ms / 60_000)}м ${Math.floor(ms % 60_000 / 1000)}с`
  return `${Math.floor(ms / 3_600_000)}ч ${Math.floor(ms % 3_600_000 / 60_000)}м`
}

/** Δ к предыдущей записи: подсвечивает паузы в пути транзакции. */
function DeltaCell({ ms }) {
  if (ms == null) return <Text type="secondary">—</Text>
  const label = `+${fmtDuration(ms)}`
  const slow = ms >= 1000
  return (
    <Text className="log-mono" style={{ fontSize: 11 }} type={slow ? 'warning' : 'secondary'} strong={slow}>
      {label}
    </Text>
  )
}

const expandable = {
  expandedRowRender: (r) => <StackTrace record={r} />,
  rowExpandable: (r) => !!r.stackTrace || (r.message?.length ?? 0) > 140,
}

export default function TracePage() {
  const appOptions = useApps()
  const [form] = Form.useForm()
  const [results, setResults] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [searchedId, setSearchedId] = useState('')
  const [viewMode, setViewMode] = useState('timeline')
  const [searchParams] = useSearchParams()

  const search = async () => {
    const { traceId, app, windowMinutes } = form.getFieldsValue()
    if (!traceId?.trim()) return
    setLoading(true)
    setError(null)
    setSearchedId(traceId.trim())
    try {
      const to = new Date()
      const from = new Date(to.getTime() - windowMinutes * 60 * 1000)
      const data = await logsApi.trace(
        traceId.trim(),
        app?.join(',') || undefined,
        from.toISOString(),
        to.toISOString(),
      )
      setResults(data)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  // Переход по клику на UUID из других страниц: /trace?id=<uuid>
  useEffect(() => {
    const id = searchParams.get('id')
    if (id && id.trim().length >= MIN_ID_LENGTH) {
      form.setFieldsValue({ traceId: id, windowMinutes: 1440 })
      search()
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams])

  const totalEntries = results?.reduce((s, r) => s + r.entries.length, 0) ?? 0

  const flatRows = (results?.flatMap((r) =>
    r.entries.map((e, i) => ({ key: `${r.app}-${i}`, app: r.app, ...e }))
  ) ?? []).sort((a, b) => a.timestamp.localeCompare(b.timestamp))
    .map((row, i, arr) => ({
      ...row,
      deltaMs: i === 0 ? null : dayjs(row.timestamp).diff(dayjs(arr[i - 1].timestamp)),
    }))

  const timelineColumns = [
    { title: 'Время', dataIndex: 'timestamp', width: 165, render: (v) => <TimeCell value={v} /> },
    { title: 'Δ', dataIndex: 'deltaMs', width: 80, render: (v) => <DeltaCell ms={v} /> },
    { title: 'Уровень', dataIndex: 'level', width: 82, render: (v) => <LevelTag level={v} /> },
    { title: 'Приложение', dataIndex: 'app', width: 150, render: (v) => <Tag color={getAppColor(v)}>{v}</Tag> },
    {
      title: 'Поток', dataIndex: 'threadName', width: 150, ellipsis: true, responsive: ['xl'],
      render: (v) => v ? <Text type="secondary" className="log-mono" style={{ fontSize: 11 }}>{v}</Text> : '—',
    },
    { title: 'Сообщение', dataIndex: 'message', ellipsis: true, render: (v) => <span className="log-mono">{v}</span> },
  ]

  const entryColumns = timelineColumns.filter(c => c.dataIndex !== 'app' && c.dataIndex !== 'deltaMs')

  const collapseItems = results?.map((r) => ({
    key: r.app,
    label: (
      <Space>
        <Text strong>{r.app}</Text>
        <Badge count={r.entries.length} color={r.entries.some(e => e.level === 'ERROR' || e.level === 'FATAL') ? 'red' : 'blue'} />
      </Space>
    ),
    children: (
      <Table
        columns={entryColumns}
        dataSource={r.entries.map((e, i) => ({ key: i, ...e }))}
        expandable={expandable}
        rowClassName={rowLevelClass}
        pagination={false}
        size="small"
        scroll={{ x: 'max-content' }}
      />
    ),
  })) ?? []

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card size="small" title="Трассировка по идентификатору">
        <Form form={form} layout="inline" onFinish={search} initialValues={{ windowMinutes: 10 }}>
          <Form.Item
            name="traceId"
            label="Идентификатор"
            rules={[
              { required: true, message: 'Введите идентификатор' },
              { min: MIN_ID_LENGTH, message: `Минимум ${MIN_ID_LENGTH} символа` },
            ]}
          >
            <Input placeholder="UUID, hh_id, Discord ID, ключевое слово" style={{ width: 340 }} allowClear className="log-mono" />
          </Form.Item>
          <Form.Item name="windowMinutes" label="Глубина поиска">
            <Select style={{ width: 130 }} options={TIME_WINDOWS} />
          </Form.Item>
          <Form.Item name="app" label="Приложения">
            <Select mode="multiple" placeholder="Все" allowClear options={appOptions} style={{ width: 220 }} maxTagCount="responsive" />
          </Form.Item>
          <Form.Item>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={loading}>
              Найти
            </Button>
          </Form.Item>
        </Form>
      </Card>

      {error && <Alert type="error" message={error} showIcon />}

      {results !== null && !loading && (
        <Card
          size="small"
          title={
            <Space>
              <span>Путь транзакции</span>
              <Text code className="log-mono" style={{ fontSize: 12 }} copyable>{searchedId}</Text>
              {totalEntries > 0 && (
                <Text type="secondary" style={{ fontWeight: 'normal' }}>
                  — {totalEntries} записей в {results.length} прил.{
                    flatRows.length > 1
                      ? ` за ${fmtDuration(dayjs(flatRows[flatRows.length - 1].timestamp).diff(dayjs(flatRows[0].timestamp)))}`
                      : ''
                  }
                </Text>
              )}
            </Space>
          }
          extra={
            results.length > 0 && (
              <Segmented
                value={viewMode}
                onChange={setViewMode}
                options={[
                  { value: 'timeline', icon: <UnorderedListOutlined />, label: 'По времени' },
                  { value: 'grouped',  icon: <AppstoreOutlined />,      label: 'По приложениям' },
                ]}
              />
            )
          }
        >
          {results.length === 0 ? (
            <Empty description="Идентификатор не найден ни в одном приложении. Попробуйте увеличить глубину поиска." />
          ) : viewMode === 'timeline' ? (
            <Table
              columns={timelineColumns}
              dataSource={flatRows}
              expandable={expandable}
              rowClassName={rowLevelClass}
              pagination={false}
              size="small"
              scroll={{ x: 'max-content' }}
            />
          ) : (
            <Collapse defaultActiveKey={results.map(r => r.app)} items={collapseItems} />
          )}
        </Card>
      )}
    </Space>
  )
}
