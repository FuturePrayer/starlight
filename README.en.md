# Starlight

English · [中文](./README.md)

[![Release](https://img.shields.io/github/v/release/FuturePrayer/starlight?sort=semver)](https://github.com/FuturePrayer/starlight/releases)
[![Java 25](https://img.shields.io/badge/Java-25-437291?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white)](https://vuejs.org/)
[![License: Anti 996](https://img.shields.io/badge/License-Anti%20996-black)](./LICENSE)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/FuturePrayer/starlight)

![Starlight Logo](./logo.png)

Starlight is a **Spring Boot 4 + Vue 3** Markdown note-taking application with category trees, full-text search, public sharing, publishable read-only sites, theme extension, and a built-in **MCP Server** for AI clients.

## Highlights

- 📝 Markdown editing, autosave, and outline support
- 🌲 Unlimited category tree, root-level notes, pinning, and trash
- 🔎 Full-text search across titles and note content
- 📦 ZIP import / export with Markdown + folder structure
- 🔗 Public share links, password-protected shares, expiration, and QR codes
- 🌌 Starlight Site: publish a category as a read-only public website
- 🎨 Built-in and external themes
- 🔐 TOTP 2FA and Passkey / WebAuthn
- 🤖 Stateless Streamable HTTP MCP Server with API Key, scope, and read-only control

## Tech Stack

- **Backend:** Java 25, Spring Boot 4, Spring MVC, Spring Data JPA, Flyway, Sa-Token, Spring AI MCP
- **Frontend:** Vue 3, Vite, Pinia, Vue Router
- **Database:** H2 by default, plus PostgreSQL and MySQL
- **Packaging:** combined image, split frontend/backend images, Spring Boot JAR, GraalVM Native Image

## Deployment

### Option 1: Docker Compose (recommended)

The repository now includes a root-level `docker-compose.yml` configured with:

- combined image: `ghcr.io/futureprayer/starlight:latest`
- database: file-based H2
- persistent data directory: `./data`
- external theme directory: `./themes`

Start:

```bash
docker compose up -d
```

Stop:

```bash
docker compose down
```

Then open:

- `http://localhost:8080/login`
- `http://localhost:8080/register`
- `http://localhost:8080/app`

First-start notes:

- the first registered user automatically becomes the administrator
- public registration is disabled by default and can be enabled later by the admin
- the MCP Server is disabled by default and must be enabled by an administrator

### Option 2: Run a single container

```bash
docker run -d \
  --name starlight \
  -p 8080:8080 \
  -v ./data:/app/data \
  -v ./themes:/app/themes \
  ghcr.io/futureprayer/starlight:latest
```

If needed, you can also use the China mainland mirror:

- `swr.cn-east-3.myhuaweicloud.com/suhoan/starlight:latest`

### Option 3: Build from source

Build frontend assets into the backend package and then start the application:

```bash
cd frontend
npm ci
npm run build:combined

cd ..
mvn clean package
java -jar target/starlight-<version>.jar
```

If you only want the backend API during development:

```bash
mvn spring-boot:run
```

> Without building the frontend into the backend package, the server still exposes APIs but does not include the full bundled web UI.

## Configuration

Default runtime settings live in `src/main/resources/application.yaml`.

### Common environment variables

| Variable | Default | Description |
| --- | --- | --- |
| `STARLIGHT_DATASOURCE_URL` | `jdbc:h2:file:./data/starlight;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;AUTO_SERVER=TRUE` | JDBC datasource URL |
| `STARLIGHT_DATASOURCE_USERNAME` | `sa` | Database username |
| `STARLIGHT_DATASOURCE_PASSWORD` | empty | Database password |
| `STARLIGHT_THEME_DIR` | `themes` | External theme directory |
| `STARLIGHT_NOTE_TRASH_RETENTION_DAYS` | `30` | Trash retention period in days |
| `STARLIGHT_NOTE_TRASH_CLEANUP_CRON` | `0 20 3 * * *` | Trash cleanup cron |
| `JAVA_OPTS` | empty | JVM startup options, useful in containers |

### Database support

- **H2:** best for single-host deployment and quick evaluation
- **PostgreSQL:** recommended for production environments
- **MySQL:** suitable when MySQL is already part of your infrastructure

Flyway manages schema migrations automatically at startup. Runtime schema creation does **not** rely on JPA auto-DDL.

### HTTPS and Passkeys

To use Passkey / WebAuthn:

- the feature must be enabled by an administrator
- the configured site URL must use `https://`
- changing the site domain may invalidate existing passkeys and require re-registration

## MCP Server

Starlight includes a built-in **Stateless Streamable HTTP MCP Server**. The default endpoint is:

- `POST /api/mcp`

### How to enable MCP

1. The administrator enables **MCP Server** in the settings center
2. Each user creates one or more **API Keys**
3. Every key can be configured with:
   - enabled / disabled state
   - read-only mode (default)
   - access to all categories or only selected scopes
   - selected category roots that automatically include descendants

### MCP permission model

- MCP access does **not** use the normal web login session; it requires an API Key
- when the admin disables MCP globally, all API Keys lose access to the MCP endpoint
- each key can only access categories and notes inside its authorized scope
- read-only keys can call query tools only; write tools are blocked
- the backend records the last usage time for every key for audit and management

### Available tools

| Tool | Description | Available in read-only mode |
| --- | --- | --- |
| `starlight_list_tree` | Read the category/note tree, optionally scoped by category and depth | Yes |
| `starlight_get_note_content` | Read Markdown content and note metadata by note ID | Yes |
| `starlight_search_note_content` | Full-text search within the authorized scope | Yes |
| `starlight_create_category` | Create a category | No |
| `starlight_update_category` | Rename or move a category | No |
| `starlight_delete_category` | Delete an empty category | No |
| `starlight_create_note` | Create a note | No |
| `starlight_update_note` | Update note title, content, or category | No |
| `starlight_delete_note` | Move a note to trash | No |

### MCP client notes

- Use an MCP client that supports **Streamable HTTP**
- Authentication headers can be either:
  - `Authorization: Bearer <api-key>`
  - `X-API-Key: <api-key>`
- The server already treats `"null"` and `"undefined"` category values as the root directory for model compatibility

## Images and Releases

The project currently documents these image variants:

- `ghcr.io/futureprayer/starlight:latest`: combined Java image with frontend + backend
- `ghcr.io/futureprayer/starlight:latest-frontend`: standalone frontend image
- `ghcr.io/futureprayer/starlight:latest-backend`: standalone backend image
- `ghcr.io/futureprayer/starlight:latest-native`: combined native image
- `ghcr.io/futureprayer/starlight:latest-backend-native`: backend native image

Pushing a `v<version>` tag can be used with the repository release workflows to publish release assets and container images.

## Local Development

### Frontend

```bash
cd frontend
npm ci
npm run dev
```

The Vite dev server proxies `/api` and `/theme-files` to `http://localhost:8080`.

### Backend

```bash
mvn spring-boot:run
```

### Tests

```bash
mvn test
```

## Project Structure

```text
startlight/
├─ src/main/java/          # Spring Boot backend source
├─ src/main/resources/     # configuration, Flyway, static assets
├─ frontend/               # Vue 3 + Vite frontend
├─ deploy/nginx/           # Nginx template for standalone frontend deployment
├─ docker-compose.yml      # Compose deployment using default H2 storage
├─ sql/                    # init / reference SQL
└─ .github/workflows/      # release workflows (if present in the repository)
```

## Notes

- Your local folder may be named `startlight`, while the application name, artifacts, package name, and container images consistently use `starlight`
- Search is implemented differently per database backend, but the feature is supported across H2, PostgreSQL, and MySQL
- Backend implementation is kept friendly to GraalVM Native Image constraints where practical

## License

[**Anti 996**](./LICENSE)

