# *Starlight*

English · [中文](./README.md)

[![Release](https://img.shields.io/github/v/release/FuturePrayer/starlight?sort=semver)](https://github.com/FuturePrayer/starlight/releases)
[![Java 25](https://img.shields.io/badge/Java-25-437291?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white)](https://vuejs.org/)
[![License: Anti 996](https://img.shields.io/badge/License-Anti%20996-black)](./LICENSE)
[![Ask DeepWiki](https://deepwiki.com/badge.svg)](https://deepwiki.com/FuturePrayer/starlight)

![Starlight Logo](./logo.png)

A lightweight **Spring Boot 4 + Vue 3** note-taking app for writing Markdown notes, organizing them in a category tree, sharing them publicly, and publishing selected categories as a read-only public site.

## Why Starlight

- 📝 Markdown editing, preview, and 30-second autosave
- 🌲 Infinite category tree, pinned notes, outline, and trash
- 🔎 Search across note titles and content
- 📦 ZIP import / export with Markdown + folder structure
- 🔗 Public share links, password-protected shares, expiration, and QR codes
- 🌌 **Starlight Site**: publish a category and its descendants as a read-only public site
- 🎨 Built-in and external themes
- 🔐 TOTP two-factor authentication and Passkey / WebAuthn

## Tech Stack

- **Backend:** Java 25, Spring Boot 4, Spring MVC, Spring Data JPA, Flyway, Sa-Token
- **Frontend:** Vue 3, Vite, Pinia, Vue Router
- **Database:** H2 by default, also supports PostgreSQL and MySQL
- **Packaging:** standalone frontend, Spring Boot Jar, GraalVM native image, Docker / GHCR

## Quick Start

### Run the full web app (recommended)

```bash
cd frontend
npm ci
npm run build:combined

cd ..
mvn clean package
java -jar target/starlight-<version>.jar
```

Default entry points:

- `http://localhost:8080/login`
- `http://localhost:8080/register`
- `http://localhost:8080/app`

On first startup:

- the first registered user becomes the administrator automatically
- public registration is disabled by default and can be enabled by the admin later

### Run backend only

```bash
mvn spring-boot:run
```

Default database: local H2 file database at `./data/starlight`

> If frontend assets have not been built into the backend package, the server still provides the API, but not the full bundled web UI.

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

## Configuration at a Glance

- Default database: file-based H2
- Switch database with `STARLIGHT_DATASOURCE_URL`, `STARLIGHT_DATASOURCE_USERNAME`, and `STARLIGHT_DATASOURCE_PASSWORD`
- External theme directory: `STARLIGHT_THEME_DIR` (default: `themes`)
- Trash retention days: `STARLIGHT_NOTE_TRASH_RETENTION_DAYS` (default: `30`)
- Trash cleanup cron: `STARLIGHT_NOTE_TRASH_CLEANUP_CRON`

Passkey notes:

- must be enabled by an administrator
- requires the configured site URL to use `https://`
- changing the site domain clears existing registered passkeys

## Deployment

### Docker / GHCR

The repository currently ships these image variants:

- combined image: `ghcr.io/futureprayer/starlight:latest`
- frontend image: `ghcr.io/futureprayer/starlight:latest-frontend`
- backend image: `ghcr.io/futureprayer/starlight:latest-backend`
- native images: `latest-native` / `latest-backend-native`

The frontend image uses `BACKEND_UPSTREAM` to reverse proxy requests to the backend.

### Release Workflows

The repository includes:

- `.github/workflows/release.yml`
- `.github/workflows/release-native.yml`

Pushing a `v<version>` tag triggers release packaging and GHCR image publishing.

## Project Structure

```text
startlight/
├─ src/main/java/          # Spring Boot backend
├─ src/main/resources/     # application.yaml, Flyway, static assets
├─ frontend/               # Vue 3 + Vite frontend
├─ deploy/nginx/           # Nginx template for standalone frontend
├─ sql/                    # schema reference SQL
└─ .github/workflows/      # release workflows
```

## Notes

- The local workspace directory may be named `startlight`, while the application name, package name, artifacts, and container images use `starlight`
- Database schema changes are managed by **Flyway**, not by JPA auto-DDL at runtime
- Search behavior may vary slightly depending on the selected database backend

## License

[**Anti 996**](./LICENSE)


