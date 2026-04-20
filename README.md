# Log Analyzer

REST-сервис на Spring Boot для анализа логов. Читает `.log` и `.log.gz` файлы локально или с удалённых серверов по SSH, парсит два формата (Apache Ignite и Spring-микросервисы), предоставляет REST API и React-интерфейс.

## Возможности

- **Два формата логов** — Apache Ignite (`[LEVEL][thread][logger] msg`) и микросервисы Spring Boot (`[thread] LEVEL logger - msg`) с авто-определением
- **Многострочные записи** — stack traces и JSON-тела привязываются к родительской записи
- **SSH-доступ** — чтение логов с удалённых серверов по SFTP/ключу
- **Фильтрация** — по приложению, уровню, периоду, тексту
- **Трассировка UUID** — поиск transaction ID во всех приложениях одновременно
- **Статистика** — ошибки по времени, уровням, приложениям, топ сообщений
- **Async-анализ** — фоновое задание с опросом статуса
- **React UI** — встроенный веб-интерфейс (Ant Design + Recharts)
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

# Собрать UI и запустить всё вместе
cd frontend && npm install && npm run build && cd ..
mvn spring-boot:run
```

Открыть в браузере: **http://localhost:8080**

> Без сборки UI бэкенд тоже работает — только REST API на `http://localhost:8080/api/logs`.

---

## Конфигурация

Файл `src/main/resources/application.yml`:

```yaml
log-analyzer:
  ssh-key-path: ~/.ssh/id_rsa        # путь к SSH-ключу для удалённых источников
  cache-ttl-seconds: 300             # TTL кэша файлов (секунды)
  max-cache-file-size-mb: 50         # файлы больше этого размера не кэшируются

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

## Деплой на сервер (JAR + systemd)

### 1. Собери на своей машине

```bash
cd frontend && npm install && npm run build && cd ..
mvn package -DskipTests
# Готовый файл: target/log-analyzer-1.0.0.jar
```

### 2. Загрузи на сервер

```bash
scp target/log-analyzer-1.0.0.jar user@server:/opt/log-analyzer/app.jar
```

### 3. Создай конфиг на сервере

```bash
mkdir -p /opt/log-analyzer
nano /opt/log-analyzer/application.yml
```

Пропиши реальные пути к логам (см. раздел «Конфигурация» выше).

### 4. Создай systemd-сервис

```bash
sudo nano /etc/systemd/system/log-analyzer.service
```

```ini
[Unit]
Description=Log Analyzer
After=network.target

[Service]
User=user
WorkingDirectory=/opt/log-analyzer
ExecStart=java -jar /opt/log-analyzer/app.jar \
  --spring.config.location=file:/opt/log-analyzer/application.yml
Restart=on-failure
RestartSec=10

[Install]
WantedBy=multi-user.target
```

```bash
sudo systemctl daemon-reload
sudo systemctl enable log-analyzer
sudo systemctl start log-analyzer

# Проверить статус и логи:
sudo systemctl status log-analyzer
journalctl -u log-analyzer -f
```

Открыть в браузере: **http://your-server:8080**

### Опционально: nginx на порту 80

```nginx
server {
    listen 80;
    server_name your-server.example.com;

    location / {
        proxy_pass http://localhost:8080;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
    }
}
```

---

## REST API

Базовый путь: `/api/logs`

### Ошибки и записи

```
GET /api/logs/errors
```

| Параметр | Тип | Описание |
|----------|-----|----------|
| `app` | string | Имя приложения (через запятую для нескольких) |
| `from` | ISO-8601 | Начало периода, напр. `2026-04-18T00:00:00Z` |
| `to` | ISO-8601 | Конец периода |
| `levels` | string[] | Уровни: `ERROR`, `WARN`, `INFO`, `DEBUG`, `TRACE` |
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
GET /api/logs/trace/{traceId}?app=order-service,ignite-node-1
```

Возвращает все записи (любого уровня) из указанных приложений, где встречается данный UUID.

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

Опрос результата:
```
GET /api/logs/jobs/{jobId}
```

Статусы: `PENDING` → `RUNNING` → `COMPLETED` / `FAILED`

---

## UI

| Страница | Описание |
|----------|----------|
| **Ошибки** | Таблица с фильтрами, пагинация, раскрывающийся stack trace |
| **Статистика** | Графики по времени, уровням и приложениям, топ сообщений |
| **Трассировка** | Поиск UUID — полный путь транзакции по приложениям |
| **Анализ** | Асинхронный запуск с live-обновлением статуса |

---

## Разработка

```bash
# Тесты бэкенда
mvn test

# UI в dev-режиме (hot reload, прокси на localhost:8080)
cd frontend && npm run dev    # http://localhost:5173

# Встроить UI в Spring (build → static)
cd frontend && npm run build
```
