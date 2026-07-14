import React, { useState, useCallback, useEffect, useRef } from 'react'
import {
  Card, Form, Input, Select, Button, Table, Space,
  DatePicker, Typography, Row, Col, Alert, Tag, Segmented, Tooltip,
} from 'antd'
import { SearchOutlined, ReloadOutlined, ClearOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { logsApi } from '../api/logsApi'
import LevelTag from '../components/LevelTag'
import TimeCell from '../components/TimeCell'
import MessageCell from '../components/MessageCell'
import StackTrace from '../components/StackTrace'
import { useApps } from '../hooks/useApps'
import { LEVELS, RANGE_PRESETS, rowLevelClass, getAppColor } from '../lib/ui'

const { RangePicker } = DatePicker
const { Text } = Typography

const PAGE_SIZE = 100
const REFRESH_OPTIONS = [
  { value: 0,  label: 'Выкл' },
  { value: 5,  label: '5 с' },
  { value: 15, label: '15 с' },
]

export default function StreamPage() {
  const appOptions = useApps()
  const [form] = Form.useForm()
  const [data, setData] = useState(null)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState(null)
  const [page, setPage] = useState(0)
  const [contains, setContains] = useState('')
  const [refreshSec, setRefreshSec] = useState(0)
  const timerRef = useRef(null)

  const search = useCallback(async (extraParams = {}) => {
    const values = form.getFieldsValue()
    const [from, to] = values.range || []
    setContains(values.contains || '')
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
    setLoading(true)
    setError(null)
    try {
      const result = await logsApi.getAllEntries(params)
      setData(result)
    } catch (e) {
      setError(e.message)
    } finally {
      setLoading(false)
    }
  }, [form, page])

  // Автообновление: сдвигаем окно «до текущего момента» и перечитываем
  useEffect(() => {
    if (timerRef.current) clearInterval(timerRef.current)
    if (refreshSec > 0) {
      timerRef.current = setInterval(() => {
        const [from] = form.getFieldValue('range') || []
        form.setFieldValue('range', [from, dayjs()])
        search({ page: 0 })
        setPage(0)
      }, refreshSec * 1000)
    }
    return () => timerRef.current && clearInterval(timerRef.current)
  }, [refreshSec, form, search])

  const handleSearch = () => { setPage(0); search({ page: 0 }) }
  const handlePageChange = (newPage) => { const p = newPage - 1; setPage(p); search({ page: p }) }
  const handleReset = () => {
    form.resetFields()
    form.setFieldValue('range', [dayjs().subtract(24, 'hour'), dayjs()])
  }

  const columns = [
    { title: 'Время', dataIndex: 'timestamp', width: 165, render: (v) => <TimeCell value={v} /> },
    { title: 'Приложение', dataIndex: 'app', width: 150, render: (v) => <Tag color={getAppColor(v)}>{v}</Tag> },
    { title: 'Уровень', dataIndex: 'level', width: 82, render: (v) => <LevelTag level={v} /> },
    {
      title: 'Поток', dataIndex: 'threadName', width: 150, ellipsis: true, responsive: ['xl'],
      render: (v) => v ? <Text type="secondary" className="log-mono" style={{ fontSize: 11 }}>{v}</Text> : '—',
    },
    {
      title: 'Логгер', dataIndex: 'loggerName', width: 170, ellipsis: true, responsive: ['xl'],
      render: (v) => v
        ? <Tooltip title={v}><Text type="secondary" style={{ fontSize: 12 }}>{v.split('.').pop()}</Text></Tooltip>
        : '—',
    },
    {
      title: 'Сообщение', dataIndex: 'message', ellipsis: true,
      render: (v) => <MessageCell text={v} highlight={contains} />,
    },
  ]

  const expandable = {
    expandedRowRender: (record) => <StackTrace record={record} />,
    rowExpandable: (record) => !!record.stackTrace || (record.message?.length ?? 0) > 140,
  }

  const rows = data?.content?.map((e, i) => ({ key: i, ...e })) ?? []

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card size="small">
        <Form form={form} layout="vertical" onFinish={handleSearch}
              initialValues={{ range: [dayjs().subtract(24, 'hour'), dayjs()] }}>
          <Row gutter={12}>
            <Col span={5}>
              <Form.Item name="app" label="Приложение" style={{ marginBottom: 8 }}>
                <Select mode="multiple" placeholder="Все приложения" allowClear options={appOptions} maxTagCount="responsive" />
              </Form.Item>
            </Col>
            <Col span={5}>
              <Form.Item name="levels" label="Уровни" style={{ marginBottom: 8 }}>
                <Select mode="multiple" placeholder="Все уровни" allowClear maxTagCount="responsive"
                  options={LEVELS.map(l => ({ value: l, label: l }))} />
              </Form.Item>
            </Col>
            <Col span={8}>
              <Form.Item name="range" label="Период" style={{ marginBottom: 8 }}>
                <RangePicker showTime style={{ width: '100%' }} presets={RANGE_PRESETS} />
              </Form.Item>
            </Col>
            <Col span={6}>
              <Form.Item name="contains" label="Поиск в тексте" style={{ marginBottom: 8 }}>
                <Input placeholder="UUID, ключевое слово" allowClear onPressEnter={handleSearch} />
              </Form.Item>
            </Col>
          </Row>
          <Space>
            <Button type="primary" htmlType="submit" icon={<SearchOutlined />} loading={loading}>
              Загрузить
            </Button>
            <Button icon={<ClearOutlined />} onClick={handleReset}>Сбросить</Button>
            <Text type="secondary" style={{ marginLeft: 12 }}>Автообновление:</Text>
            <Segmented size="small" value={refreshSec} onChange={setRefreshSec} options={REFRESH_OPTIONS} />
          </Space>
        </Form>
      </Card>

      {error && <Alert type="error" message={error} showIcon />}

      {data && (
        <Card
          size="small"
          title={
            <Space>
              <span>Все логи</span>
              <Text type="secondary" style={{ fontSize: 13, fontWeight: 'normal' }}>
                {data.total} записей · стр. {data.page + 1} из {data.totalPages || 1}
              </Text>
            </Space>
          }
          extra={<Button size="small" icon={<ReloadOutlined />} onClick={() => search()}>Обновить</Button>}
        >
          <Table
            columns={columns}
            dataSource={rows}
            expandable={expandable}
            rowClassName={rowLevelClass}
            pagination={{
              current: (data.page ?? 0) + 1,
              pageSize: PAGE_SIZE,
              total: data.total,
              onChange: handlePageChange,
              showSizeChanger: false,
              size: 'small',
            }}
            scroll={{ x: 'max-content' }}
            size="small"
          />
        </Card>
      )}
    </Space>
  )
}
