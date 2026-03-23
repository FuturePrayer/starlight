# starlight

`starlight` 是一个基于 Spring Boot + Vue 3 的轻量笔记应用，支持：

- 用户名 / 邮箱 + 密码登录
- 首个注册账户自动成为唯一管理员
- 管理员可控制是否允许注册（默认关闭）
- Markdown 笔记编辑、实时预览、30 秒自动保存
- 无限级分类树
- 文档大纲（识别 `##` ~ `######`）
- 公开 / 私密 / 可过期分享
- 用户级主题切换与外部主题目录加载
- 两套内置 Windows 11 风格主题（浅色 / 深色）

---

## 目录说明

- 后端：`Spring Boot`
- 前端：`frontend/`（Vue 3 + Vite）
- 合并部署：前端构建到 `src/main/resources/static/` 后再打成 Spring Boot Jar

---

## 本地开发

### 1）仅启动后端

应用默认使用本地 H2 文件数据库，无需额外安装 PostgreSQL：

```powershell
cd D:\devProjects\java\startlight
mvn spring-boot:run
```

如果此时没有先构建前端，那么后端只会提供 API，不会带 Web UI。

### 2）前端独立开发

```powershell
cd D:\devProjects\java\startlight\frontend
npm ci
npm run dev
```

Vite 开发服务器会把 `/api` 与 `/theme-files` 代理到 `http://localhost:8080`。

### 3）本地构建前后端合并包

```powershell
cd D:\devProjects\java\startlight\frontend
npm ci
npm run build:combined

cd D:\devProjects\java\startlight
mvn clean package
```

构建完成后会得到合并版 Jar：

- `target/startlight-<version>.jar`

启动后访问：

- 登录页：`http://localhost:8080/login`
- 工作台：`http://localhost:8080/app`

### 4）仅构建前端静态包

```powershell
cd D:\devProjects\java\startlight\frontend
npm ci
npm run build
```

产物位于：

- `frontend/dist/`

---

## 切换数据库

可通过环境变量覆盖：

```powershell
$env:STARLIGHT_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:5432/startlight"
$env:STARLIGHT_DATASOURCE_USERNAME="postgres"
$env:STARLIGHT_DATASOURCE_PASSWORD="postgres"
mvn spring-boot:run
```

---

## 外部主题目录

默认从项目根目录下的 `themes` 目录扫描外部主题，也可通过环境变量覆盖：

```powershell
$env:STARLIGHT_THEME_DIR="D:\themes\starlight"
mvn spring-boot:run
```

每个外部主题目录至少包含：

- `theme.json`
- `theme.css`

示例 `theme.json`：

```json
{
  "id": "forest",
  "name": "森林主题",
  "cssUrl": "/theme-files/forest/theme.css",
  "previewColor": "#2e8b57",
  "backgroundImage": "",
  "backgroundOpacity": 0
}
```

---

## 初始化 SQL

初始化表结构 SQL 位于：

- `sql/starlight-init.sql`

适合作为 PostgreSQL / H2 / MySQL 8+ 的建表参考脚本。

Spring data jpa会自动创建和更新表结构，无需手动执行。

---

## GitHub 自动发布

仓库已提供工作流：`.github/workflows/release.yml`。

### 触发条件

当你从 `main` 或 `master` 对应提交创建并推送版本 Tag 时自动触发。

Tag 必须满足：

- 格式：`v<version>`
- 其中 `<version>` 必须与 `pom.xml` 中的 `project.version` 完全一致

例如当前版本若为 `0.0.1`，则应推送：

```powershell
cd D:\devProjects\java\startlight
git checkout main
git pull
git tag v0.0.1
git push origin v0.0.1
```

> 如果你的默认分支叫 `master`，把上面的 `main` 替换为 `master` 即可。

### 自动生成的发布物

工作流会自动构建并发布三类产物到 GitHub Releases：

1. 前端单独包：`startlight-frontend-<version>.tar.gz`
2. 后端单独包：`startlight-backend-<version>.jar`
3. 前后端合并包：`startlight-combined-<version>.jar`
4. 校验文件：`SHA256SUMS`

同时会发布三个容器镜像到 `ghcr.io`：

- `ghcr.io/futureprayer/startlight-frontend:<version>`
- `ghcr.io/futureprayer/startlight-backend:<version>`
- `ghcr.io/futureprayer/startlight:<version>`

其中：

- `ghcr.io/futureprayer/startlight:latest` **只会指向最新的前后端合并镜像**
- 不会给 frontend-only / backend-only 镜像打 `latest`

---

## 部署方式

下面提供两类部署方式：

1. 从 Releases 下载构建产物部署
2. 直接使用 GHCR 容器镜像部署

### 方式一：从 Releases 下载部署

#### A. 使用前后端合并包（推荐，最简单）

下载：

- `startlight-combined-<version>.jar`

启动：

```powershell
java -jar startlight-combined-<version>.jar
```

默认访问：

- `http://localhost:8080/login`

这是最省心的部署方式，适合单机部署、测试环境和小型生产环境。

#### B. 使用后端单独包

下载：

- `startlight-backend-<version>.jar`

启动：

```powershell
java -jar startlight-backend-<version>.jar
```

说明：

- 这个包只提供后端 API 与主题文件接口
- 你需要额外部署前端静态站点，或使用 `ghcr.io/futureprayer/startlight-frontend:<version>`

#### C. 使用前端单独包

下载：

- `startlight-frontend-<version>.tar.gz`

解压后得到一个纯静态站点，可部署到 Nginx、Apache 或任意静态文件服务器。

但要注意：

- 前端默认通过同源路径访问 `/api` 和 `/theme-files`
- 因此前端站点需要把这两个路径反向代理到后端服务

可参考仓库中的 Nginx 模板：

- `deploy/nginx/frontend.conf.template`

一个典型的反向代理思路是：

- `/` -> 前端静态文件目录
- `/api/` -> 后端服务，例如 `http://127.0.0.1:8080`
- `/theme-files/` -> 后端服务，例如 `http://127.0.0.1:8080`

---

### 方式二：使用 GHCR 容器镜像部署

#### A. 直接部署合并镜像（推荐）

拉取并启动最新版：

```bash
docker pull ghcr.io/futureprayer/startlight:latest
docker run -d --name startlight -p 8080:8080 ghcr.io/futureprayer/startlight:latest
```

或者指定版本：

```bash
docker pull ghcr.io/futureprayer/startlight:<version>
docker run -d --name startlight -p 8080:8080 ghcr.io/futureprayer/startlight:<version>
```

#### B. 单独部署后端镜像

```bash
docker pull ghcr.io/futureprayer/startlight-backend:<version>
docker run -d --name startlight-backend -p 8080:8080 ghcr.io/futureprayer/startlight-backend:<version>
```

#### C. 单独部署前端镜像

前端镜像基于 Nginx，并支持通过环境变量 `BACKEND_UPSTREAM` 反向代理后端：

```bash
docker pull ghcr.io/futureprayer/startlight-frontend:<version>
docker run -d --name startlight-frontend -p 8081:80 -e BACKEND_UPSTREAM=http://host.docker.internal:8080 ghcr.io/futureprayer/startlight-frontend:<version>
```

说明：

- `BACKEND_UPSTREAM` 默认值为 `http://backend:8080`
- 如果前后端不在同一个 Docker 网络，需要改成你的真实后端地址
- 前端镜像会自动把 `/api/` 和 `/theme-files/` 代理到该后端地址

#### D. 前后端分开部署到同一个 Docker 网络

```bash
docker network create startlight-net

docker run -d --name startlight-backend --network startlight-net ghcr.io/futureprayer/startlight-backend:<version>
docker run -d --name startlight-frontend --network startlight-net -p 8080:80 -e BACKEND_UPSTREAM=http://startlight-backend:8080 ghcr.io/futureprayer/startlight-frontend:<version>
```

此时访问：

- `http://localhost:8080/login`

---

## 测试

```powershell
cd D:\devProjects\java\startlight
mvn test
```

