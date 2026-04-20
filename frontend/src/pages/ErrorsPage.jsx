import React, { useState, useCallback } from 'react'
import {
  Card, Form, Input, Select, Button, Table, Space,
  DatePicker, Typography, Row, Col, Alert,
} from 'antd'
import { SearchOutlined, ReloadOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { logsApi } from '../api/logsApi'
import LevelTag from '../components/LevelTag'
import { useApps } from '../hooks/useApps'

const { RangePicker } = DatePicker
const { Text, Paragraph } = Typography

const LEVELS = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL']

const PAGE_SIZE = 20

export default function ErrorsPage() {
  const appOptions = useApps()
  const [form] = Form.useForm()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [page, setPage] = useState(0)
  const [lastParams, setLastParams] = useState({})

  const search = useCallback(async (extraParams = {}) => {
    const values = form.getFieldsValue()
    const [from, to] = values.range || []
    const params = {
      app: values.app?.join(',') || undefined,
      from: from ? from.toISOString() : undefined,
      to:   to   ? to.toISOString()   : undefined,
      levels: values.levels?.join(',') || undefined,
      contains: values.contains || undefined,
      page: extraParams.page ?? page,
      size: PAGE_SIZE,
      ...extraParams,
    }
    setLastParams(params)
    setLoading(true)
    setError(null)
    try {
      const result = await logsApi.getErrors(params)
      setData(result)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [form, page])

  const handleSearch = () => {
    setPage(0)
    search({ page: 0 })
  }

  const handlePageChange = (newPage) => {
    const p = newPage - 1
    setPage(p)
    search({ page: p })
  }

  const columns = [
    {
      title: 'Время',
      dataIndex: 'timestamp',
      width: 180,
      render: (v) => dayjs(v).format('YYYY-MM-DD HH:mm:ss.SSS'),
    },
    {
      title: 'Уровень',
      dataIndex: 'level',
      width: 90,
      render: (v) => <LevelTag level={v} />,
    },
    {
      title: 'Приложение',
      dataIndex: 'app',
      width: 150,
      ellipsis: true,
    },
    {
      title: 'Поток',
      dataIndex: 'threadName',
      width: 180,
      ellipsis: true,
      render: (v) => v ? <Text code style={{ fontSize: 11 }}>{v}</Text> : '—',
    },
    {
      title: 'Логгер',
      dataIndex: 'loggerName',
      width: 200,
      ellipsis: true,
      render: (v) => v ? <Text type="secondary" style={{ fontSize: 12 }}>{v}</Text> : '—',
    },
    {
      title: 'Сообщение',
      dataIndex: 'message',
      ellipsis: true,
    },
  ]

  // Rows that have a stackTrace are expandable
  const expandable = {
    expandedRowRender: (record) =>
      record.stackTrace ? (
        <Paragraph>
          <pre style={{
            margin: 0, fontSize: 12, background: '#1a1a1a',
            color: '#ff6b6b', padding: 12, borderRadius: 4,
            overflowX: 'auto', maxHeight: 300,
          }}>
            {record.stackTrace}
          </pre>
        </Paragraph>
      ) : null,
    rowExpandable: (record) => !!record.stackTrace,
  }

  // Flatten: data.content is List<LogAnalysisResult>, each has errors[]
  const flatRows = data?.content?.flatMap((r, ri) =>
    r.errors.map((e, ei) => ({ key: `${ri}-${ei}`, ...e }))
  ) ?? []

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card title="Фильтры">
        <Form form={form} layout="vertical" onFinish={handleSearch}>
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item name="app" label="Приложение">
                <Select mode="multiple" placeholder="Все приложения" allowClear options={appOptions} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="levels" label="Уровни">
                <Select mode="multiple" placeholder="По умолчанию: ERROR, FATAL" allowClear options={LEVELS.map(l => ({ value: l, label: l }))} />
              </Form.Item>
            </Col>
            <Col span={7}>
              <Form.Item name="range" label="Период">
                <RangePicker showTime style={{ width: '100%' }} />
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item name="contains" label="Поиск в тексте">
                <Input placeholder="UUID, ключевое слово" allowClear />
              </Form.Item>
            </Col>
          </Row>
          <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={loading}>
            Найти
          </Button>
        </Form>
      </Card>

      {error && <Alert type="error" message={error} showIcon />}

      {data && (
        <Card
          title={
            <Space>
              <span>Результаты</span>
              <Text type="secondary" style={{ fontSize: 13 }}>
                {data.total} совпадений (стр. {data.page + 1} из {data.totalPages || 1})
              </Text>
            </Space>
          }
          extra={
            <Button icon={<ReloadOutlined />} onClick={() => search()}>Обновить</Button>
          }
        >
          <Table
            columns={columns}
            dataSource={flatRows}
            expandable={expandable}
            pagination={{
              current: (data.page ?? 0) + 1,
              pageSize: PAGE_SIZE,
              total: data.total,
              onChange: handlePageChange,
              showSizeChanger: false,
            }}
            scroll={{ x: 'max-content' }}
            size="small"
          />
        </Card>
      )}
    </Space>
  )
}
