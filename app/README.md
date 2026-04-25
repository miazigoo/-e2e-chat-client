## Конфигурация окружений

Основное описание проекта и инструкции для GitHub находятся в корневом [README.md](../README.md).

Приложение читает адрес API и флаги debug/release из Gradle properties или переменных окружения.

### Debug

По умолчанию debug-сборка ходит в:

```text
http://10.0.2.2:8000/api/v1/
```

На Android-эмуляторе `localhost` указывает на сам эмулятор, а не на твой ПК.

Переопределить можно так:

```text
SECURE_CHAT_DEBUG_API_BASE_URL=http://10.0.2.2:8000/api/v1/
SECURE_CHAT_DEBUG_HTTP_LOGGING=true
SECURE_CHAT_DEBUG_SIGNAL_PROTOCOL=false
SECURE_CHAT_SHOW_DEBUG_AUTH_INFO=true
```

### Release

Для release-сборки обязательно нужно задать production API URL, иначе Gradle остановит сборку:

```text
SECURE_CHAT_RELEASE_API_BASE_URL=https://api.example.com/api/v1/
SECURE_CHAT_RELEASE_HTTP_LOGGING=false
SECURE_CHAT_RELEASE_SIGNAL_PROTOCOL=false
```
