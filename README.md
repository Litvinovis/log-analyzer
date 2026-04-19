# Log Analyzer

REST-сервис на Spring Boot для анализа логов приложений. Читает `.log` и `.log.gz` файлы из заданных директорий, фильтрует записи по уровню, приложению и временному диапазону.

## Возможности

- Парсинг текстовых и gzip-сжатых лог-файлов
- Фильтрация по приложению, временному диапазону, уровням ERROR / FATAL
- Автоматическое определение имени приложения по структуре директорий
- REST API с JSON-ответами
- Поддержка нескольких корневых директорий с логами

## Требования

- Java 21+
- Maven 3.8+

## Быстрый старт

```bash
git clone https://github.com/Litvinovis/log-analyzer.git
cd log-analyzer
mvn spring-boot:run
```

По умолчанию сервис запускается на порту `8080`.

## Конфигурация

`src/main/resources/application.yml`:

```yaml
server:
  port: 8080

log-analyzer:
  log-paths:
    - /var/log/app        # можно указать несколько путей
    - /opt/services/logs
```

Свойство `log-analyzer.log-paths` принимает список корневых директорий. Сервис рекурсивно обходит каждую и подбирает файлы с расширениями `.log`, `.log.gz`, `.gz`.

## Формат лог-файлов

Поддерживается формат:

```
TIMESTAMP LEVEL [APP] сообщение
```

Примеры валидных строк:

```
2025-01-15T10:30:00Z      ERROR [auth-service] Connection timeout
2025-01-15T10:30:00.123Z  FATAL [payment-api]  Database unreachable
2025-01-15 10:30:00       ERROR [worker]        Task failed
2025-01-15 10:30:00.456   ERROR [scheduler]     Job aborted
```

## API

### `GET /api/logs/errors`

Возвращает ошибки (уровни `ERROR` и `FATAL`) из лог-файлов.

**Параметры запроса** (все необязательные):

| Параметр | Тип | Описание |
|----------|-----|----------|
| `app` | `string` | Имя приложения или несколько через запятую: `app=auth-service,payment-api` |
| `from` | `string` | Начало диапазона в формате ISO-8601: `2025-01-15T10:00:00Z` |
| `to` | `string` | Конец диапазона в формате ISO-8601: `2025-01-15T11:00:00Z` |

**Пример запроса:**

```bash
curl "http://localhost:8080/api/logs/errors?app=auth-service&from=2025-01-15T10:00:00Z&to=2025-01-15T11:00:00Z"
```

**Пример ответа:**

```json
[
  {
    "app": "auth-service",
    "analyzedAt": "2025-01-15T10:35:00Z",
    "errors": [
      {
        "timestamp": "2025-01-15T10:30:00Z",
        "level": "ERROR",
        "app": "auth-service",
        "message": "Connection timeout",
        "sourceFile": "/var/log/app/auth-service/app.log"
      }
    ],
    "totalLinesScanned": 1500,
    "errorCount": 1
  }
]
```

**Коды ответа:**

| Код | Описание |
|-----|----------|
| `200` | Успех, возвращает список результатов (может быть пустым) |
| `400` | Неверный формат даты в параметрах `from` или `to` |

---

### `GET /api/logs/health`

Проверка работоспособности сервиса.

```bash
curl http://localhost:8080/api/logs/health
# OK
```

## Структура директорий с логами

Имя приложения определяется автоматически по первому уровню вложенности относительно `log-paths`:

```
/var/log/app/
├── auth-service/
│   └── app.log          →  app = "auth-service"
├── payment-api/
│   ├── app.log          →  app = "payment-api"
│   └── app.2025-01.log.gz
└── worker.log           →  app = "worker"
```

## Запуск тестов

```bash
mvn test
```

23 теста покрывают парсер, сервис анализа, REST-контроллер и хранилище.

## Структура проекта

```
src/main/java/com/loganalyzer/
├── LogAnalyzerApplication.java   — точка входа
├── api/
│   └── LogController.java        — REST-эндпоинты
├── analyzer/
│   └── LogAnalyzerService.java   — бизнес-логика анализа
├── config/
│   └── LogAnalyzerConfig.java    — конфигурационные свойства
├── model/
│   ├── LogEntry.java             — запись из лога
│   ├── LogQuery.java             — параметры запроса
│   └── LogAnalysisResult.java    — результат анализа
├── parser/
│   └── LogFileParser.java        — парсинг .log и .log.gz файлов
└── storage/
    └── LogStore.java             — in-memory хранилище результатов
```
