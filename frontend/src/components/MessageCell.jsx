import React from 'react'
import { Tooltip } from 'antd'
import { useNavigate } from 'react-router-dom'
import { useMeta } from '../hooks/useMeta'

function escapeRe(s) {
  return s.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')
}

/**
 * Сообщение лога: моноширинно, найденная подстрока подсвечена, а каждый
 * идентификатор (по паттернам из конфига бэка: UUID, числовые ID и т.д.) —
 * ссылка на страницу трассировки.
 */
export default function MessageCell({ text, highlight }) {
  const navigate = useNavigate()
  const { idPatterns } = useMeta()
  if (!text) return '—'

  let idRe = null
  try {
    idRe = new RegExp(idPatterns.map(p => `(?:${p})`).join('|'), 'gi')
  } catch { /* кривой паттерн в конфиге — работаем без линковки */ }

  const parts = idRe ? text.split(idRe) : [text]
  const ids = idRe ? (text.match(idRe) ?? []) : []

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
    if (i < ids.length) {
      const id = ids[i]
      nodes.push(
        <Tooltip title="Трассировать этот идентификатор" key={`u${i}`}>
          <a
            className="uuid-link"
            onClick={(e) => { e.stopPropagation(); navigate(`/trace?id=${encodeURIComponent(id)}`) }}
          >
            {id}
          </a>
        </Tooltip>
      )
    }
  })

  return <span className="log-mono">{nodes}</span>
}
