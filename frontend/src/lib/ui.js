import React, { createContext, useContext } from 'react'
import dayjs from 'dayjs'

/** Контекст темы: страницы и графики берут isDark отсюда. */
export const UiContext = createContext({ isDark: true })
export const useUi = () => useContext(UiContext)

/** Цвета графиков, проверены dataviz-валидатором на обеих поверхностях. */
export const CHART = {
  dark:  { accent: '#4c88e8', error: '#d4585c', grid: '#2d2d44', tick: '#8a8aa8' },
  light: { accent: '#1677ff', error: '#cf1322', grid: '#e5e5ee', tick: '#7a7a90' },
}

export const LEVELS = ['TRACE', 'DEBUG', 'INFO', 'WARN', 'ERROR', 'FATAL']

/** Быстрые пресеты периодов для RangePicker. */
export const RANGE_PRESETS = [
  { label: '15 минут', value: () => [dayjs().subtract(15, 'minute'), dayjs()] },
  { label: '1 час',    value: () => [dayjs().subtract(1, 'hour'),  dayjs()] },
  { label: '6 часов',  value: () => [dayjs().subtract(6, 'hour'),  dayjs()] },
  { label: '24 часа',  value: () => [dayjs().subtract(24, 'hour'), dayjs()] },
  { label: '3 дня',    value: () => [dayjs().subtract(3, 'day'),   dayjs()] },
  { label: '7 дней',   value: () => [dayjs().subtract(7, 'day'),   dayjs()] },
].map(p => ({ ...p, value: p.value() }))

/** Тонировка строк таблиц по уровню (классы в styles.css). */
export const rowLevelClass = (record) =>
  ['FATAL', 'ERROR', 'WARN'].includes(record.level) ? `row-${record.level}` : ''

/** Детерминированный цвет тега приложения. */
const APP_COLORS = ['cyan', 'geekblue', 'purple', 'volcano', 'gold', 'lime', 'green', 'magenta', 'blue', 'orange']
const appColorCache = {}
let appColorIdx = 0
export function getAppColor(app) {
  if (!appColorCache[app]) {
    appColorCache[app] = APP_COLORS[appColorIdx % APP_COLORS.length]
    appColorIdx++
  }
  return appColorCache[app]
}
