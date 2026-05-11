# 1. Загрузка репозитория

```bash
git clone https://github.com/MamaevSergey/Burnout-prediction-system.git
cd burnout-prediction-system
```

# 2. Настройка переменных окружения

Перед запуском, необходимо настроить доступы к базе данных и для самого приложения.

## 2.1 Настройка Docker Compose

```bash
nano docker-compose.yml
```

**В `db` задайте `POSTGRES_USER` и `POSTGRES_PASSWORD`.**
`POSTGRES_DB` можно оставить по умолчанию.

<img width="376" height="284" alt="image" src="https://github.com/user-attachments/assets/82f06235-fe85-4b36-b7b8-e90013ce4823" />

--------

**Перейдем к настройке самого приложения:**
1. Впишите в `SPRING_DATASOURCE_USERNAME` - `POSTGRES_USER`
2. Впишите в `SPRING_DATASOURCE_PASSWORD` - `POSTGRES_PASSWORD`
3. Придумайте логины и пароли для каждой роли (ADMIN, HR, VIEWER)
4. Нужно сгенерировать `JWT SECRET` (можно командой `openssl rand -base64 32`).
   Мой пример: `9a4f2c8d3b7a1e6f45c8a0b3f267d8b1d4e6f3c8a9d2b5f8e3a9c8b5f6v8a3d9`
5. Сгенерируйте `MASTER_KEY` (можно командой `openssl rand -base64 48 | cut -c1-32`).
   Мой пример: `T9x#m2PqL8z!vW4bY7k@nC5rA1fJ6sH3`
6. Придумайте и напишите `HASH_SALT`, она будет использоваться для хеширования emails.

<img width="559" height="481" alt="image" src="https://github.com/user-attachments/assets/46c996a1-9e7e-4804-ae0d-8049ee94bba2" />

# 3. Запуск системы

Убедитесь, что у вас установлен Docker и Docker Compose.

## 3.1 **Установка Docker и Docker Compose**
```bash
sudo curl -fsSL https://get.docker.com | sh
```

## 3.2 **Запуск системы**
```bash
docker compose up -d --build
```

После успешного запуска, приложение будет доступно в браузере по адресу http://ip-addres:8080/login.html
# 4. Первичная настройка

1. Перейдите по адресу приложения и авторизуйтесь под учетной записью Администратора.
2. Откройте раздел "Настройки системы"
3. Первый блок настроек можно пропустить.

## Интеграция с Jira
Для корректного сбора задач и оценки заполните следующие поля:
- `URL`: ссылка на рабочее пространство организации (например, https://your-domain.atlassian.net).
- `Jira Username`: Email или логин администратора/пользователя, на которого выпущен токен.
- `API Token`: Необходимо выпустить токен с гранулярным доступом.
	- При создании токена в интерфейсе Atlassian (на этапе Select the app) выберите пункт **Jira** (API token can only access Jira APIs).
	- В разделе **Scopes** (Области действия) выберите права только на чтение, чтобы система могла собирать метрики без риска изменения данных:
		- `read:jira-work` (чтение задач, спринтов, комментариев)
	    - `read:jira-user` (чтение профилей пользователей для привязки метрик)

<img width="601" height="699" alt="image" src="https://github.com/user-attachments/assets/1ed181fe-1b85-4407-a447-e33d8bd86864" />

<img width="837" height="572" alt="image" src="https://github.com/user-attachments/assets/db32f004-f707-4208-bc3c-3d2a3dbbb2dd" />

## Интеграция с GitHub

- **GitHub Username** Название вашей организации в GitHub.
- **API Token (Personal Access Token):** Создайте Fine-grained Token в настройках GitHub.
	- Необходимые доступы (Scopes) для сбора метрик:

<img width="755" height="446" alt="image" src="https://github.com/user-attachments/assets/aa475efa-6015-42b0-85af-45d877077b21" />

## Настройка кастомных статусов (Jira Task)

Перейдите к пункту **Done status** (Статусы завершения).

Укажите статусы из вашей Jira, которые система должна считать успешно закрытыми задачами (например: `Done`, `Closed`, `Resolved`, `Готово`, `Выполнено` и т.п.).
