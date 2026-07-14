import React from 'react'
import { Tooltip } from 'antd'
import { useNavigate } from 'react-router-dom'

const UUID_G = /[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}/gi

function escapeRe(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * Сообщение лога: моноширинно, найденная подстрока подсвечена,
 * а каждый UUID — ссылка на страницу трассировки.
 */
export default function MessageCell({ text, highlight }) {
  const navigate = useNavigate()
  if (!text) return '—'

  // Разбивка по UUID (сохранением разделителей), затем подсветка внутри кусков
  const parts = text.split(UUID_G)
  const uuids = text.match(UUID_G) ?? []

  const renderPlain = (chunk, keyBase) => {
    if (!highlight) return chunk
    const re = new RegExp(escapeRe(highlight), 'gi')
    const segs = chunk.split(re)
    const hits = chunk.match(re) ?? []
    return segs.flatMap((s, i) =>
      i < hits.length
        ? [s, <mark className="log-hit" key={`${keyBase}-h${i}`}>{hits[i]}</mark>]
        : [s]
    )
  }

  const nodes = []
  parts.forEach((p, i) => {
    nodes.push(<React.Fragment key={`p${i}`}>{renderPlain(p, `p${i}`)}</React.Fragment>)
    if (i < uuids.length) {
      const id = uuids[i]
      nodes.push(
        <Tooltip title="Трассировать этот ID" key={`u${i}`}>
          <a
            className="uuid-link"
            onClick={(e) => { e.stopPropagation(); navigate(`/trace?id=${id}`) }}
          >
            {id}
          </a>
        </Tooltip>
      )
    }
  })

  return <span className="log-mono">{nodes}</span>
}
