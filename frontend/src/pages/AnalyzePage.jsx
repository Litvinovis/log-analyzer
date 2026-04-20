import React, { useState, useEffect, useRef } from 'react'
import {
  Card, Form, Input, Select, Button, Alert, Space,
  Table, Typography, Tag, Steps, DatePicker, Row, Col,
} from 'antd'
import { useApps } from '../hooks/useApps'
import { ThunderboltOutlined, LoadingOutlined, CheckCircleOutlined, CloseCircleOutlined } from '@ant-design/icons'
import dayjs from 'dayjs'
import { logsApi } from '../api/logsApi'
import LevelTag from '../components/LevelTag'

const { RangePicker } = DatePicker
const { Text } = Typography
const LEVELS = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL']

const STATUS_ICON = {
  PENDING:   <LoadingOutlined style={{ color: '#1677ff' }} />,
  RUNNING:   <LoadingOutlined style={{ color: '#fa8c16' }} />,
  COMPLETED: <CheckCircleOutlined style={{ color: '#52c41a' }} />,
  FAILED:    <CloseCircleOutlined style={{ color: '#ff4d4f' }} />,
}

const resultColumns = [
  { title: 'Приложение', dataIndex: 'app', width: 160 },
  { title: 'Ошибок', dataIndex: 'errorCount', width: 90, render: (v) => <Tag color="red">{v}</Tag> },
  { title: 'Строк всего', dataIndex: 'totalLinesScanned', width: 120 },
  {
    title: 'Топ ошибки',
    dataIndex: 'errors',
    render: (errors) => (
      <Space direction="vertical" size={2} style={{ width: '100%' }}>
        {errors?.slice(0, 3).map((e, i) => (
          <Space key={i} size={4}>
            <LevelTag level={e.level} />
            <Text ellipsis style={{ maxWidth: 400, fontSize: 12 }}>{e.message}</Text>
          </Space>
        ))}
        {errors?.length > 3 && <Text type="secondary" style={{ fontSize: 11 }}>...ещё {errors.length - 3}</Text>}
      </Space>
    ),
  },
]

export default function AnalyzePage() {
  const appOptions = useApps()
  const [form] = Form.useForm()
  const [job, setJob] = useState(null)
  const [submitting, setSubmitting] = useState(false)
  const [error, setError] = useState(null)
  const pollRef = useRef(null)

  const stopPoll = () => {
    if (pollRef.current) {
      clearInterval(pollRef.current)
      pollRef.current = null
    }
  }

  useEffect(() => () => stopPoll(), [])

  const startPoll = (jobId) => {
    stopPoll()
    pollRef.current = setInterval(async () => {
      try {
        const updated = await logsApi.getJob(jobId)
        setJob(updated)
        if (updated.status === 'COMPLETED' || updated.status === 'FAILED') {
          stopPoll()
        }
      } catch { stopPoll() }
    }, 1500)
  }

  const submit = async () => {
    const values = form.getFieldsValue()
    const [from, to] = values.range || []
    setSubmitting(true)
    setError(null)
    setJob(null)
    try {
      const started = await logsApi.startAnalysis({
        apps: values.apps?.length ? values.apps : null,
        from: from ? from.toISOString() : null,
        to:   to   ? to.toISOString()   : null,
        levels: values.levels?.length ? values.levels : null,
        contains: values.contains || null,
      })
      setJob(started)
      startPoll(started.jobId)
    } catch (e) {
      setError(e.message)
    } finally {
      setSubmitting(false)
    }
  }

  const statusStep = { PENDING: 0, RUNNING: 1, COMPLETED: 2, FAILED: 2 }[job?.status] ?? 0

  return (
    <Space direction="vertical" style={{ width: '100%' }} size="middle">
      <Card title="Параметры анализа">
        <Form form={form} layout="vertical" onFinish={submit}>
          <Row gutter={16}>
            <Col span={6}>
              <Form.Item name="apps" label="Приложения">
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
              <Form.Item name="contains" label="Текст / UUID">
                <Input allowClear />
              </Form.Item>
            </Col>
          </Row>
          <Button
            type="primary"
            htmlType="submit"
            icon={<ThunderboltOutlined />}
            loading={submitting}
            disabled={!!job && (job.status === 'PENDING' || job.status === 'RUNNING')}
          >
            Запустить
          </Button>
        </Form>
      </Card>

      {error && <Alert type="error" message={error} showIcon />}

      {job && (
        <Card
          title={
            <Space>
              {STATUS_ICON[job.status]}
              <span>Задание</span>
              <Text code style={{ fontSize: 12 }}>{job.jobId}</Text>
              <Tag color={job.status === 'COMPLETED' ? 'green' : job.status === 'FAILED' ? 'red' : 'blue'}>
                {job.status}
              </Tag>
            </Space>
          }
        >
          <Steps
            size="small"
            current={statusStep}
            status={job.status === 'FAILED' ? 'error' : undefined}
            style={{ marginBottom: 24 }}
            items={[
              { title: 'Принято' },
              { title: 'Выполняется' },
              { title: job.status === 'FAILED' ? 'Ошибка' : 'Готово' },
            ]}
          />

          {job.status === 'COMPLETED' && job.results !== null && (
            job.results?.length === 0
              ? <Alert type="info" message="Совпадений не найдено" showIcon />
              : <Table
                  columns={resultColumns}
                  dataSource={job.results.map((r, i) => ({ key: i, ...r }))}
                  pagination={false}
                  size="small"
                />
          )}
        </Card>
      )}
    </Space>
  )
}
