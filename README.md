# CONTACT — Render Ready

Минимальный backend CONTACT для деплоя на Render.

## Что уже настроено

- Node.js backend без npm-зависимостей
- Telegram admin Chat ID: `902102344`
- Render Blueprint: `render.yaml`
- `/health` endpoint
- DEVELOPER / HIGHER -> Telegram
- PERSONA API
- rate limit
- delete endpoint

## Самый короткий запуск

### 1. Создай пустой GitHub-репозиторий

Например:

`contact-backend`

### 2. Загрузи в корень репозитория ВСЕ файлы из этого архива

Должно получиться:

- `server.js`
- `package.json`
- `render.yaml`
- `.gitignore`
- `.env.example`
- `README.md`

### 3. Render

На Render:

**New → Blueprint**

Подключи GitHub и выбери `contact-backend`.

Render увидит `render.yaml`.

### 4. Секрет Telegram

В Environment у сервиса добавь:

`TELEGRAM_BOT_TOKEN`

Значение — НОВЫЙ token из BotFather.

Его:
- не присылай в чат;
- не клади в GitHub;
- не вставляй в APK.

`TELEGRAM_ADMIN_CHAT_ID=902102344` уже задан в blueprint.

### 5. Deploy

После успешного деплоя Render даст адрес вроде:

`https://contact-backend.onrender.com`

Проверка:

`https://contact-backend.onrender.com/health`

Ожидается JSON с:

`"product":"CONTACT"`

### 6. Следующий шаг

Пришли ChatGPT только публичный `https://...onrender.com` URL.

Тогда его можно поставить в Android:

`AppConfig.BASE_URL = "https://...onrender.com/"`

## Telegram команды

Ответ в Developer:

`/reply <conversation-id> developer <текст>`

Ответ в Higher:

`/reply <conversation-id> higher <текст>`

Завершить:

`/close <conversation-id>`

Статистика:

`/stats`

## Важно

На бесплатном Render сервис может засыпать при бездействии. Для теста это нормально.
Для публичного запуска позже лучше перейти на постоянный план/сервер и production DB.
