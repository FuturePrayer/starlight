# *Starlight*

[English](./README.en.md) · 中文

[![Release](https://img.shields.io/github/v/release/FuturePrayer/starlight?sort=semver)](https://github.com/FuturePrayer/starlight/releases)
[![Java 25](https://img.shields.io/badge/Java-25-437291?logo=openjdk&logoColor=white)](https://openjdk.org/)
[![Spring Boot 4](https://img.shields.io/badge/Spring%20Boot-4.0-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Vue 3](https://img.shields.io/badge/Vue-3-4FC08D?logo=vuedotjs&logoColor=white)](https://vuejs.org/)
[![License: Anti 996](https://img.shields.io/badge/License-Anti%20996-black)](./LICENSE)

![Starlight Logo](./logo.png)

一个基于 **Spring Boot 4 + Vue 3** 的轻量笔记应用，用来写 Markdown 笔记、管理分类树、生成分享链接，并把分类发布成公开站点。

## 为什么是 Starlight

- 📝 Markdown 笔记编辑、预览、30 秒自动保存
- 🌲 无限级分类树、置顶、大纲、回收站
- 🔎 搜索笔记标题与正文
- 📦 ZIP 导入 / 导出（Markdown + 文件夹结构）
- 🔗 公开分享、密码分享、过期时间、二维码
- 🌌 星迹书阁：把某个分类及其子分类发布为只读公开站点
- 🎨 内置与外部主题
- 🔐 TOTP 两步验证与 Passkey / WebAuthn

## 技术栈

- **Backend:** Java 25, Spring Boot 4, Spring MVC, Spring Data JPA, Flyway, Sa-Token
- **Frontend:** Vue 3, Vite, Pinia, Vue Router
- **Database:** H2 by default, also supports PostgreSQL and MySQL
- **Packaging:** standalone frontend, Spring Boot Jar, GraalVM native image, Docker / GHCR

## 快速开始

### 方式一：运行完整 Web 应用（推荐）

```bash
cd frontend
npm ci
npm run build:combined

cd ..
mvn clean package
java -jar target/starlight-<version>.jar
```

默认访问：

- `http://localhost:8080/login`
- `http://localhost:8080/register`
- `http://localhost:8080/app`

首次启动时：

- 首个注册用户会自动成为管理员
- 普通注册默认关闭，管理员可在应用内开启

### 方式二：仅启动后端

```bash
mvn spring-boot:run
```

默认数据库为本地 H2 文件库：`./data/starlight`

> 如果没有先构建前端静态资源，后端仍可提供 API，但不会自带完整 Web UI。

## 本地开发

### 前端

```bash
cd frontend
npm ci
npm run dev
```

Vite 会将 `/api` 与 `/theme-files` 代理到 `http://localhost:8080`。

### 后端

```bash
mvn spring-boot:run
```

### 测试

```bash
mvn test
```

## 配置摘要

- 默认数据库：H2 文件库
- 数据库切换：通过 `STARLIGHT_DATASOURCE_URL` 等环境变量切换到 PostgreSQL / MySQL
- 外部主题目录：`STARLIGHT_THEME_DIR`，默认 `themes`
- 回收站保留天数：`STARLIGHT_NOTE_TRASH_RETENTION_DAYS`，默认 `30`
- 回收站清理 cron：`STARLIGHT_NOTE_TRASH_CLEANUP_CRON`

Passkey 相关说明：

- 需要管理员启用
- 需要站点 URL 为 `https://`
- 变更站点域名会清空已注册的通行密钥

## 部署

### Docker / GHCR

仓库当前提供：

- 合并镜像：`ghcr.io/futureprayer/starlight:latest`
- 前端镜像：`ghcr.io/futureprayer/starlight:latest-frontend`
- 后端镜像：`ghcr.io/futureprayer/starlight:latest-backend`
- 原生镜像：`latest-native` / `latest-backend-native`

前端镜像通过 `BACKEND_UPSTREAM` 反向代理后端。

### 发布工作流

仓库包含：

- `.github/workflows/release.yml`
- `.github/workflows/release-native.yml`

推送 `v<version>` 标签后会自动构建 Releases 与 GHCR 镜像。

## 项目结构

```text
startlight/
├─ src/main/java/          # Spring Boot backend
├─ src/main/resources/     # application.yaml, Flyway, static assets
├─ frontend/               # Vue 3 + Vite frontend
├─ deploy/nginx/           # Nginx template for standalone frontend
├─ sql/                    # schema reference SQL
└─ .github/workflows/      # release workflows
```

## 相关说明

- 当前工作区目录名是 `startlight`，但项目名、包名、产物名、镜像名均为 `starlight`
- 数据库结构迁移由 **Flyway** 管理，运行时不是依赖 JPA 自动建表
- 搜索功能在不同数据库下会使用不同实现，匹配细节可能略有差异

## License

[**Anti 996**](./LICENSE)

