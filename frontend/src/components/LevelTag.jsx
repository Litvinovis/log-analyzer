import React from 'react'
import { Tag } from 'antd'

// Пресетные цвета antd — корректно адаптируются к светлой и тёмной теме
const COLOR = {
  FATAL: 'magenta',
  ERROR: 'red',
  WARN:  'orange',
  INFO:  'blue',
  DEBUG: 'default',
  TRACE: 'default',
}

export default function LevelTag({ level }) {
  return (
    <Tag
      color={COLOR[level] ?? 'default'}
      style={{ marginInlineEnd: 0, opacity: level === 'TRACE' ? 0.65 : 1 }}
    >
      {level}
    </Tag>
  )
}
