import { useState, useEffect } from 'react'
import { logsApi } from '../api/logsApi'

const DEFAULT_PATTERNS = [
  '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}',
]

/** Метаданные с бэка: приложения и паттерны идентификаторов трассировки. */
export function useMeta() {
  const [meta, setMeta] = useState({ apps: [], idPatterns: DEFAULT_PATTERNS })

  useEffect(() => {
    logsApi.getMeta()
      .then(m => setMeta({
        apps: [...(m.apps ?? [])].sort(),
        idPatterns: m.idPatterns?.length ? m.idPatterns : DEFAULT_PATTERNS,
      }))
      .catch(() => {})
  }, [])

  return meta
}
