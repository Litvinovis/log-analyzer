import { useState, useEffect } from 'react'
import { logsApi } from '../api/logsApi'

export function useApps() {
  const [apps, setApps] = useState([])

  useEffect(() => {
    logsApi.getApps().then(setApps).catch(() => {})
  }, [])

  return apps.map(a => ({ value: a, label: a }))
}
