import React from 'react'
import { Tag } from 'antd'

const COLORS = {
  FATAL: 'magenta',
  ERROR: 'red',
  WARN:  'orange',
  INFO:  'blue',
  DEBUG: 'default',
  TRACE: 'default',
}

export default function LevelTag({ level }) {
  return <Tag color={COLORS[level] ?? 'default'}>{level}</Tag>
}
