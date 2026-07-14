import React from 'react'
import { Tooltip, Typography } from 'antd'
import dayjs from 'dayjs'

const { Text } = Typography

/** Компактное время: дата мелко, время с миллисекундами — моноширинно. */
export default function TimeCell({ value }) {
  const d = dayjs(value)
  return (
    <Tooltip title={d.format('YYYY-MM-DD HH:mm:ss.SSS')}>
      <span className="log-mono" style={{ whiteSpace: 'nowrap' }}>
        <Text type="secondary" style={{ fontSize: 11 }}>{d.format('DD.MM')} </Text>
        {d.format('HH:mm:ss')}
        <Text type="secondary" style={{ fontSize: 11 }}>.{d.format('SSS')}</Text>
      </span>
    </Tooltip>
  )
}
