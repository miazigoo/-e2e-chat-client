## Конфигурация окружений

Основное описание проекта и инструкции для GitHub находятся в корневом [README.md](../README.md).

Приложение читает адрес API и флаги debug/release из Gradle properties или переменных окружения.

### Debug

По умолчанию debug-сборка ходит в:

```text
https://170.168.10.207/api/v1/
```

Переопределить можно так:

```text
SECURE_CHAT_DEBUG_API_BASE_URL=https://your-domain.example/api/v1/
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
