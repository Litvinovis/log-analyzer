import React from 'react'
import { Tag } from 'antd'

const STYLE = {
  FATAL: { color: '#ff85c2', background: '#3d0020', border: '1px solid #c41d7f' },
  ERROR: { color: '#ff7875', background: '#2a0a0a', border: '1px solid #a61d24' },
  WARN:  { color: '#ffc069', background: '#2b1a00', border: '1px solid #d46b08' },
  INFO:  { color: '#69b1ff', background: '#001a3d', border: '1px solid #1677ff' },
  DEBUG: { color: '#b0b0c8', background: '#1e1e2e', border: '1px solid #3a3a55' },
  TRACE: { color: '#7a7a9a', background: '#1a1a2e', border: '1px solid #2d2d44' },
}

export default function LevelTag({ level }) {
  const s = STYLE[level]
  return (
    <Tag style={s ?? { color: '#b0b0c8' }}>
      {level}
    </Tag>
  )
}
