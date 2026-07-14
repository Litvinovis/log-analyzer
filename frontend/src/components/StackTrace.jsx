import React from 'react'
import { Button, message } from 'antd'
import { CopyOutlined } from '@ant-design/icons'
import { useUi } from '../lib/ui'

/**
 * Развёрнутая запись: полное сообщение + stack trace (если есть),
 * theme-aware фон и кнопка копирования.
 */
export default function StackTrace({ record }) {
  const { isDark } = useUi()
  const full = [record.message, record.stackTrace].filter(Boolean).join('\n')

  const copy = async () => {
    try {
      await navigator.clipboard.writeText(full)
      message.success('Скопировано')
    } catch {
      message.error('Не удалось скопировать')
    }
  }

  return (
    <div style={{ position: 'relative' }}>
      <Button
        size="small"
        icon={<CopyOutlined />}
        onClick={copy}
        style={{ position: 'absolute', top: 6, right: 6, zIndex: 1 }}
      >
        Копировать
      </Button>
      <pre className="log-mono" style={{
        margin: 0,
        padding: '12px 110px 12px 12px',
        borderRadius: 6,
        overflowX: 'auto',
        maxHeight: 320,
        whiteSpace: 'pre-wrap',
        wordBreak: 'break-word',
        background: isDark ? '#16161f' : '#f6f6fa',
        color: isDark ? '#d8d8e8' : '#333',
        border: `1px solid ${isDark ? '#2d2d44' : '#e0e0ea'}`,
      }}>
        {record.stackTrace
          ? <><span>{record.message}</span>{'\n'}<span style={{ color: isDark ? '#ff8a8a' : '#c0392b' }}>{record.stackTrace}</span></>
          : record.message}
      </pre>
    </div>
  )
}
