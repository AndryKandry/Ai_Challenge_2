# Настройка Yandex GPT

## Описание

Теперь приложение поддерживает работу с двумя AI провайдерами:
- **Claude AI** (Anthropic)
- **Yandex GPT**

## Необходимые переменные окружения

### Для Claude (обязательно)
```bash
export CLAUDE_API_KEY="your-claude-api-key"
```

### Для Yandex GPT (опционально)
```bash
export YANDEX_API_KEY="your-yandex-api-key"
export YANDEX_FOLDER_ID="your-yandex-folder-id"
```

## Получение API ключей Yandex GPT

1. **Зарегистрируйтесь в Yandex Cloud**: https://cloud.yandex.ru/
2. **Создайте каталог** (Folder) в облаке
3. **Получите Folder ID**:
   - Перейдите в консоль Yandex Cloud
   - Выберите нужный каталог
   - Скопируйте ID каталога из URL или настроек
4. **Создайте API ключ**:
   - Перейдите в IAM (Identity and Access Management)
   - Создайте сервисный аккаунт
   - Назначьте ему роль `ai.languageModels.user`
   - Создайте API ключ для сервисного аккаунта

## Как запустить приложение

### С Claude (по умолчанию)
```bash
export CLAUDE_API_KEY="your-claude-api-key"
./gradlew run
```

### С Claude и Yandex GPT
```bash
export CLAUDE_API_KEY="your-claude-api-key"
export YANDEX_API_KEY="your-yandex-api-key"
export YANDEX_FOLDER_ID="your-yandex-folder-id"
./gradlew run
```

## Использование в UI

После запуска приложения вы увидите панель **"Выбор AI модели"** с кнопками:
- 🔵 **Claude** - использовать Claude AI
- 🟡 **Yandex GPT** - использовать Yandex GPT (доступно только если настроены ключи)

Переключение между моделями происходит мгновенно, история диалога сохраняется.

## Доступные модели Yandex GPT

По умолчанию используется `yandexgpt-lite` - быстрая и экономичная модель.

Вы можете изменить модель в коде `YandexGPTClientBuilder`

Доступные модели:
- `yandexgpt-lite` - облегченная версия (по умолчанию)
- `yandexgpt` - полная версия
- `yandexgpt-32k` - версия с увеличенным контекстом

## Примечания

- Если Yandex GPT ключи не настроены, приложение будет работать только с Claude
- Кнопка Yandex GPT будет неактивна, если ключи не заданы
- Внизу панели выбора модели отображается статус доступности Yandex GPT
- Температура и режим чата применяются к обеим моделям

## Стоимость

- **Claude**: платная модель, оплата по токенам
- **Yandex GPT**: зависит от тарифа Yandex Cloud, есть бесплатный уровень

## Поддержка

При возникновении проблем проверьте:
1. Правильность API ключей
2. Наличие прав у сервисного аккаунта Yandex
3. Корректность Folder ID
4. Логи приложения для диагностики ошибок
