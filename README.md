# Log Analyzer

REST-сервис на Spring Boot для анализа логов. Читает `.log` и `.log.gz` файлы локально или с удалённых серверов по SSH, парсит два формата (Apache Ignite и Spring-микросервисы), предоставляет REST API и React-интерфейс.

## Возможности

- **Два формата логов** — Apache Ignite (`[LEVEL][thread][logger] msg`) и микросервисы Spring Boot (`[thread] LEVEL logger - msg`) с авто-определением
- **Многострочные записи** — stack traces и JSON-тела привязываются к родительской записи
- **SSH-доступ** — чтение логов с удалённых серверов по SFTP/ключу
- **Фильтрация** — по приложению, уровню, периоду, тексту
- **Трассировка UUID** — поиск transaction ID во всех приложениях одновременно, с выбором временного окна (10м–24ч) и переключением вида (по времени / по приложениям)
- **Единый поток логов** — хронологический вид со всех источников одновременно с цветными тегами приложений
- **Статистика** — ошибки по времени, уровням, приложениям, топ сообщений
- **Async-анализ** — фоновое задание с опросом статуса и выгрузкой результатов в CSV
- **React UI** — встроенный веб-интерфейс с тёмной/светлой темой (Ant Design + Recharts)
- **Параллельный опрос источников** — все источники читаются одновременно через `CompletableFuture`
- **Оптимизация памяти** — пропуск файлов по дате модификации, стриминг больших файлов, ограниченный кэш с вытеснением
- **Настраиваемая таймзона** — корректная интерпретация меток времени в логах
- **Кэш файлов** — повторные запросы не перечитывают диск
- **WatchService** — автоматическая инвалидация кэша при изменении файлов
- **Actuator** — `/actuator/health`, `/actuator/metrics`

## Требования

- Java 21+
- Maven 3.8+
- Node.js 18+ и npm (только для сборки UI)

---

## Быстрый старт (локально)

```bash
git clone https://github.com/Litvinovis/log-analyzer.git
cd log-analyzer

cd frontend && npm install && npm run build && cd ..
mvn spring-boot:run
```

Открыть в браузере: **http://localhost:18765**

> Без сборки UI бэкенд тоже работает — только REST API на `http://localhost:18765/api/logs`.

---

## Конфигурация

### `config/application.yml`

```yaml
log-analyzer:
  ssh-key-path: ~/.ssh/id_rsa        # путь к SSH-ключу для удалённых источников
  cache-ttl-seconds: 300             # TTL кэша файлов (секунды)
  max-cache-file-size-mb: 50         # файлы больше этого размера не кэшируются (стриминг)
  max-cached-files: 20               # максимум файлов одновременно в кэше (старые вытесняются)
  log-timezone: Europe/Moscow        # таймзона, в которой записаны метки времени в логах

  sources:
    # Локальный кластер Ignite
    - name: ignite-node-1
      log-path: /opt/ignite/work/log
      connection: LOCAL
      log-format: IGNITE             # IGNITE | MICROSERVICE | AUTO
      watch-levels: [ERROR, WARN]    # уровни по умолчанию (без явного ?levels=)

    # Локальный микросервис
    - name: order-service
      log-path: /var/log/order-service/logs
      connection: LOCAL
      log-format: MICROSERVICE
      watch-levels: [ERROR]

    # Удалённый микросервис по SSH
    - name: payment-service
      log-path: /opt/payment/logs
      connection: SSH
      ssh-host: 10.0.0.5
      ssh-port: 22
      ssh-user: ubuntu
      log-format: MICROSERVICE
      watch-levels: [ERROR, WARN]
```

### Форматы логов

| Формат | Пример строки |
|--------|--------------|
| `IGNITE` | `2026-04-20 00:00:08.205 [TRACE][Thread-Loader][backupEventsLog] Сообщение` |
| `MICROSERVICE` | `2026-04-19 17:39:34.954 [masking-exec-1] TRACE messages.writer.in - Сообщение` |
| `AUTO` | Авто-определение по каждой строке |

### Структура папок с логами

- **Ignite**: `log-path/` — текущие логи, `log-path/archive/` — архив (сканируются обе)
- **Микросервис**: `log-path/` — всё в одной папке (`.log` и `.log.gz`)

---

## Деплой на сервер

### 1. Скачать готовый JAR

После каждого мержа в `main` GitHub Actions автоматически собирает проект и публикует артефакт в **Releases**:

```bash
curl -L https://github.com/Litvinovis/log-analyzer/releases/latest/download/log-analyzer-1.0.0.jar \
     -o /opt/log-analyzer/log-analyzer-1.0.0.jar
```

Или скачать вручную: **GitHub → Releases → latest → log-analyzer-1.0.0.jar**

### 2. Структура на сервере

```
/opt/log-analyzer/
  log-analyzer.sh           ← скрипт управления (из репозитория)
  log-analyzer-1.0.0.jar    ← скачать из Releases
  config/
    application.yml         ← настроить пути к логам
    logback.xml             ← шаблон из репозитория
  logs/                     ← создаётся автоматически
    la.log                  ← основной лог
    la-ssh.log              ← SSH-подключения
    la-watcher.log          ← файловый вотчер
    la-api.log              ← API-запросы
    archive/                ← ротированные архивы
```

Скопировать конфиги из репозитория:
```bash
cp config/logback.xml /opt/log-analyzer/config/
cp log-analyzer.sh /opt/log-analyzer/
chmod +x /opt/log-analyzer/log-analyzer.sh
```

Затем заполнить `/opt/log-analyzer/config/application.yml` реальными путями к логам.

### 3. Управление сервисом

```bash
./log-analyzer.sh start         # запуск в фоне (3 попытки)
./log-analyzer.sh start debug   # запуск с выводом в log-analyzer_debug.log
./log-analyzer.sh status        # показать PID
./log-analyzer.sh stop          # graceful stop с таймаутами
./log-analyzer.sh stop force    # kill -9
./log-analyzer.sh restart       # stop + start
```

### 4. Обновление до новой версии

```bash
./log-analyzer.sh stop
curl -L https://github.com/Litvinovis/log-analyzer/releases/latest/download/log-analyzer-1.0.0.jar \
     -o /opt/log-analyzer/log-analyzer-1.0.0.jar
./log-analyzer.sh start
```

### Порты

| Назначение | Порт |
|-----------|------|
| HTTP (UI + API) | **18765** |
| JMX | 19485 |
| Remote debug | 15485 |

### Опционально: nginx на порту 80

```nginx
server {
    listen 80;
    server_name your-server.example.com;

    location / {
        proxy_pass http://localhost:18765;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

## REST API

Базовый путь: `/api/logs`

### Список приложений

```
GET /api/logs/apps
```

Возвращает список имён источников из конфигурации (используется UI для выпадающих списков):

```json
["ignite-node-1", "order-service", "payment-service"]
```

### Единый поток логов

```
GET /api/logs/all
```

| Параметр | Тип | Описание |
|----------|-----|----------|
| `app` | string | Имя приложения (через запятую для нескольких) |
| `from` | ISO-8601 | Начало периода |
| `to` | ISO-8601 | Конец периода |
| `levels` | string | Уровни через запятую |
| `contains` | string | Текст в сообщении |
| `page` | int | Страница (0-based) |
| `size` | int | Размер страницы (по умолчанию 100) |

### Ошибки

```
GET /api/logs/errors
```

| Параметр | Тип | Описание |
|----------|-----|----------|
| `app` | string | Имя приложения (через запятую для нескольких) |
| `from` | ISO-8601 | Начало периода, напр. `2026-04-18T00:00:00Z` |
| `to` | ISO-8601 | Конец периода |
| `levels` | string | Уровни через запятую: `ERROR,WARN` |
| `contains` | string | Текст в сообщении |
| `page` | int | Страница (0-based, по умолчанию 0) |
| `size` | int | Размер страницы (по умолчанию 20) |

```json
{
  "content": [
    {
      "app": "order-service",
      "analyzedAt": "2026-04-20T10:00:00Z",
      "errors": [
        {
          "timestamp": "2026-04-19T17:39:34.954Z",
          "level": "ERROR",
          "app": "order-service",
          "message": "Ошибка при обогащении транзакции",
          "threadName": "response-exec-1",
          "loggerName": "enricher",
          "stackTrace": "ru.sbrf.IgniteClusterUnavailableException\n\tat ...",
          "sourceFile": "/var/log/order-service/logs/app.log"
        }
      ],
      "totalLinesScanned": 1024,
      "errorCount": 1
    }
  ],
  "page": 0,
  "size": 20,
  "total": 1,
  "totalPages": 1
}
```

### Статистика

```
GET /api/logs/stats?app=order-service&from=2026-04-18T00:00:00Z
```

```json
{
  "totalScanned": 50000,
  "totalErrors": 42,
  "byLevel": { "ERROR": 38, "WARN": 4 },
  "byApp": { "order-service": 42 },
  "topMessages": [
    { "message": "Ошибка при обогащении транзакции", "count": 12 }
  ],
  "byHour": { "2026-04-19T17:00:00Z": 5, "2026-04-19T18:00:00Z": 37 }
}
```

### Трассировка по UUID

```
GET /api/logs/trace/{traceId}?app=order-service,ignite-node-1&from=2026-04-20T10:00:00Z&to=2026-04-20T10:30:00Z
```

| Параметр | Тип | Описание |
|----------|-----|----------|
| `app` | string | Приложения через запятую (опционально) |
| `from` | ISO-8601 | Начало временного окна поиска |
| `to` | ISO-8601 | Конец временного окна поиска |

Возвращает все записи (любого уровня) из указанных приложений, где встречается данный UUID:

```json
[
  {
    "app": "order-service",
    "entries": [
      { "timestamp": "...", "level": "INFO", "message": "Получено Id = f47ac10b..." }
    ]
  },
  {
    "app": "ignite-node-1",
    "entries": [
      { "timestamp": "...", "level": "ERROR", "message": "Ошибка Id = f47ac10b..." }
    ]
  }
]
```

### Асинхронный анализ

```
POST /api/logs/analyze
Content-Type: application/json

{
  "apps": ["order-service"],
  "from": "2026-04-18T00:00:00Z",
  "to": "2026-04-20T00:00:00Z",
  "levels": ["ERROR", "WARN"],
  "contains": null
}
```

Ответ `202 Accepted`:
```json
{ "jobId": "550e8400-e29b-41d4-a716-446655440000", "status": "PENDING", "results": null }
```

Опрос: `GET /api/logs/jobs/{jobId}` — статусы: `PENDING` → `RUNNING` → `COMPLETED` / `FAILED`

---

## UI

| Страница | Описание |
|----------|----------|
| **Все логи** | Единый хронологический поток со всех источников, цветные теги приложений, фильтры, пагинация |
| **Ошибки** | Таблица ERROR/FATAL с фильтрами, пагинация, раскрывающийся stack trace |
| **Статистика** | Графики по времени, уровням и приложениям, топ повторяющихся сообщений |
| **Трассировка** | Поиск UUID с выбором временного окна (10м–24ч), два вида результатов: по времени и по приложениям |
| **Анализ** | Асинхронный запуск с live-статусом, сводная таблица ошибок, выгрузка в CSV |

Интерфейс поддерживает **тёмную и светлую тему** — переключатель в левом верхнем углу, выбор сохраняется в `localStorage`.

---

## CI/CD

GitHub Actions запускается автоматически:

| Событие | Что происходит |
|---------|---------------|
| Открытие / обновление PR | Сборка фронта + тесты + сборка JAR → artifact во вкладке Actions |
| Merge в `main` | То же + публикация JAR в **GitHub Releases** (тег `latest`) |

---

## Разработка

```bash
# Тесты бэкенда
mvn test

# UI в dev-режиме (hot reload, прокси на localhost:18765)
cd frontend && npm run dev    # http://localhost:5173

# Встроить UI в Spring
cd frontend && npm run build
```
