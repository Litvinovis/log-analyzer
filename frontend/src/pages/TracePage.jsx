import React, { useState } from 'react'
import {
  Card, Form, Input, Button, Alert, Space, Table,
  Typography, Tag, Collapse, Empty, Badge, Select,
} from 'antd'
import { SearchOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { logsApi } from '../api/logsApi'
import LevelTag from '../components/LevelTag'
import { useApps } from '../hooks/useApps'

const { Text, Paragraph } = Typography

const UUID_RE = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i

const entryColumns = [
  {
    title: 'Время',
    dataIndex: 'timestamp',
    width: 190,
    render: (v) => <Text style={{ fontSize: 12 }}>{dayjs(v).format('YYYY-MM-DD HH:mm:ss.SSS')}</Text>,
  },
  {
    title: 'Уровень',
    dataIndex: 'level',
    width: 90,
    render: (v) => <LevelTag level={v} />,
  },
  {
    title: 'Поток',
    dataIndex: 'threadName',
    width: 170,
    ellipsis: true,
    render: (v) => v ? <Text code style={{ fontSize: 11 }}>{v}</Text> : '—',
  },
  {
    title: 'Сообщение',
    dataIndex: 'message',
    ellipsis: true,
  },
]

const expandable = {
  expandedRowRender: (r) =>
    r.stackTrace ? (
      <pre style={{
        margin: 0, fontSize: 11, background: '#1a1a1a',
        color: '#ff6b6b', padding: 10, borderRadius: 4,
        overflowX: 'auto', maxHeight: 250,
      }}>
        {r.stackTrace}
      </pre>
    ) : null,
  rowExpandable: (r) => !!r.stackTrace,
}

export default function TracePage() {
  const appOptions = useApps()
  const [form] = Form.useForm()
  const [results, setResults] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [searchedId, setSearchedId] = useState('')

  const search = async () => {
    const { traceId, app } = form.getFieldsValue()
    if (!traceId?.trim()) return
    setLoading(true)
    setError(null)
    setSearchedId(traceId.trim())
    try {
      const data = await logsApi.trace(traceId.trim(), app?.join(',') || undefined)
      setResults(data)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }

  const totalEntries = results?.reduce((s, r) => s + r.entries.length, 0) ?? 0

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
        pagination={false}
        size="small"
        scroll={{ x: 'max-content' }}
      />
    ),
  })) ?? []

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card title="Поиск транзакции по UUID">
        <Form form={form} layout="inline" onFinish={search}>
          <Form.Item
            name="traceId"
            label="Transaction ID"
            rules={[
              { required: true, message: 'Введите UUID' },
              { pattern: UUID_RE, message: 'Неверный формат UUID' },
            ]}
          >
            <Input
              placeholder="f47ac10b-58cc-4372-a567-0e02b2c3d479"
              style={{ width: 360 }}
              allowClear
            />
          </Form.Item>
          <Form.Item name="app" label="Приложения (необязательно)" style={{ minWidth: 260 }}>
            <Select mode="multiple" placeholder="Все приложения" allowClear options={appOptions} style={{ width: 260 }} />
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
          title={
            <Space>
              <span>Путь транзакции</span>
              <Text code style={{ fontSize: 12 }}>{searchedId}</Text>
              {totalEntries > 0 && (
                <Text type="secondary">— {totalEntries} записей в {results.length} приложениях</Text>
              )}
            </Space>
          }
        >
          {results.length === 0 ? (
            <Empty description="UUID не найден ни в одном приложении" />
          ) : (
            <Collapse
              defaultActiveKey={results.map(r => r.app)}
              items={collapseItems}
            />
          )}
        </Card>
      )}
    </Space>
  )
}
