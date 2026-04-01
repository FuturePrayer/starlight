# GitHub 工作流同时推送到另一个 Docker 镜像仓库的修改说明

本文仅说明如何修改 GitHub Actions，使现有镜像在发布到 `ghcr.io` 的同时，再额外推送到另一个需要登录的 Docker 镜像仓库（例如你自己的私库）。

> 本文是说明文档，不会自动修改当前工作流文件。

---

## 目标

当前工作流已经会把镜像推送到：

- `ghcr.io/<owner>/starlight:<tag>`

如果你还想同步推送到另一个仓库，例如：

- `registry.example.com/myteam/starlight:<tag>`

则核心思路是：

1. 在 GitHub Secrets 中保存第二个仓库的登录信息
2. 在工作流中增加一次登录步骤
3. 在每次 `docker/build-push-action` 时，为同一次构建补充第二套 tags

这样一次构建就可以同时推送到两个 registry。

---

## 一、建议新增的 GitHub Secrets

在仓库的 **Settings -> Secrets and variables -> Actions** 中新增：

- `EXTRA_REGISTRY`：第二个镜像仓库地址，例如 `registry.example.com`
- `EXTRA_REGISTRY_NAMESPACE`：命名空间/项目路径，例如 `myteam`
- `EXTRA_REGISTRY_USERNAME`：登录用户名
- `EXTRA_REGISTRY_PASSWORD`：登录密码或 access token

如果你的私库只接受 token，也可以把 `EXTRA_REGISTRY_PASSWORD` 理解为 token。

---

## 二、工作流中需要增加的登录步骤

你现在的工作流里已经有 GHCR 登录，例如：

```yaml
- name: Log in to GHCR
  uses: docker/login-action@v3
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}
```

如果要再推送到另一个私库，可以在后面再加一个登录步骤：

```yaml
- name: Log in to extra registry
  uses: docker/login-action@v3
  with:
    registry: ${{ secrets.EXTRA_REGISTRY }}
    username: ${{ secrets.EXTRA_REGISTRY_USERNAME }}
    password: ${{ secrets.EXTRA_REGISTRY_PASSWORD }}
```

只要登录成功，后面的构建步骤就可以同时把镜像推送到两个仓库。

---

## 三、如何修改 tags

现在每个镜像构建步骤里，`tags:` 只包含 GHCR 地址。

例如前端镜像现在类似：

```yaml
tags: |
  ghcr.io/${{ needs.validate.outputs.owner_lc }}/${{ needs.validate.outputs.image_name }}:${{ needs.validate.outputs.version }}-frontend
  ghcr.io/${{ needs.validate.outputs.owner_lc }}/${{ needs.validate.outputs.image_name }}:latest-frontend
```

如果要同步推送到另一个仓库，可以直接在同一个 `tags:` 里再加两行：

```yaml
tags: |
  ghcr.io/${{ needs.validate.outputs.owner_lc }}/${{ needs.validate.outputs.image_name }}:${{ needs.validate.outputs.version }}-frontend
  ghcr.io/${{ needs.validate.outputs.owner_lc }}/${{ needs.validate.outputs.image_name }}:latest-frontend
  ${{ secrets.EXTRA_REGISTRY }}/${{ secrets.EXTRA_REGISTRY_NAMESPACE }}/${{ needs.validate.outputs.image_name }}:${{ needs.validate.outputs.version }}-frontend
  ${{ secrets.EXTRA_REGISTRY }}/${{ secrets.EXTRA_REGISTRY_NAMESPACE }}/${{ needs.validate.outputs.image_name }}:latest-frontend
```

backend / combined / native / backend-native 也按同样方式追加第二套 tag 即可。

---

## 四、推荐的修改位置

你需要修改两个工作流文件：

- `.github/workflows/release.yml`
- `.github/workflows/release-native.yml`

### 1）`release.yml`

需要处理这三个构建步骤：

- `Build and push frontend image`
- `Build and push backend image`
- `Build and push combined image`

做法：

1. 保留原有 GHCR 登录
2. 新增“登录额外仓库”的步骤
3. 在这三个步骤的 `tags:` 中，分别追加第二个 registry 的对应 tags

### 2）`release-native.yml`

需要处理这两个构建步骤：

- `Build and push backend native image`
- `Build and push combined native image`

做法完全一样：

1. 增加一次额外 registry 登录
2. 在两个步骤的 `tags:` 中追加第二个 registry 地址

---

## 五、一个更完整的示例

下面给出一个简化示例，展示“同时推送到 GHCR 和私库”的写法：

```yaml
- name: Log in to GHCR
  uses: docker/login-action@v3
  with:
    registry: ghcr.io
    username: ${{ github.actor }}
    password: ${{ secrets.GITHUB_TOKEN }}

- name: Log in to extra registry
  uses: docker/login-action@v3
  with:
    registry: ${{ secrets.EXTRA_REGISTRY }}
    username: ${{ secrets.EXTRA_REGISTRY_USERNAME }}
    password: ${{ secrets.EXTRA_REGISTRY_PASSWORD }}

- name: Build and push backend image
  uses: docker/build-push-action@v6
  with:
    context: .
    file: ./Dockerfile.backend
    push: true
    build-args: |
      JAR_FILE=.docker/backend/${{ needs.validate.outputs.artifact_id }}-backend-${{ needs.validate.outputs.version }}.jar
    tags: |
      ghcr.io/${{ needs.validate.outputs.owner_lc }}/${{ needs.validate.outputs.image_name }}:${{ needs.validate.outputs.version }}-backend
      ghcr.io/${{ needs.validate.outputs.owner_lc }}/${{ needs.validate.outputs.image_name }}:latest-backend
      ${{ secrets.EXTRA_REGISTRY }}/${{ secrets.EXTRA_REGISTRY_NAMESPACE }}/${{ needs.validate.outputs.image_name }}:${{ needs.validate.outputs.version }}-backend
      ${{ secrets.EXTRA_REGISTRY }}/${{ secrets.EXTRA_REGISTRY_NAMESPACE }}/${{ needs.validate.outputs.image_name }}:latest-backend
```

这个模式可以直接复制到其他镜像构建步骤。

---

## 六、如果两个仓库的命名空间不同

如果 GHCR 用的是：

- `ghcr.io/futureprayer/starlight:*`

而私库希望用：

- `registry.example.com/my-prod/starlight:*`

那也没有问题，只要把第二套 tag 写成：

```yaml
${{ secrets.EXTRA_REGISTRY }}/my-prod/${{ needs.validate.outputs.image_name }}:${{ needs.validate.outputs.version }}
```

即可。

如果连镜像名也想不同，也可以把 `starlight` 换成另一个 secret 或变量，但通常建议两个仓库保持同名，便于维护。

---

## 七、是否需要改 release notes

如果你希望 GitHub Release 页面也显示第二个仓库的镜像地址，则需要同步修改：

- `.github/workflows/release.yml` 中 `Generate checksums and release notes` 这一段

例如在 `Container images` 列表里，再额外追加私库地址：

- `registry.example.com/myteam/starlight:<version>`
- `registry.example.com/myteam/starlight:latest`
- `registry.example.com/myteam/starlight:<version>-backend`
- `registry.example.com/myteam/starlight:latest-backend`
- 等等

如果你不在意 Release Notes 展示第二仓库地址，也可以不改。

---

## 八、私库常见注意事项

### 1）登录凭据权限

私库账号需要有 push 权限，否则登录成功也可能推送失败。

### 2）TLS / 证书问题

如果私库使用自签名证书，GitHub Hosted Runner 上可能会遇到证书校验问题。
这种情况通常需要：

- 改用受信任证书
- 或使用自建 Runner 预装证书

### 3）仓库自动创建策略

有些私库会在首次 push 时自动创建仓库，有些则要求你提前手工创建项目/仓库。
这点要看你的私库产品（Harbor、Nexus、Docker Registry、阿里云、腾讯云等）的规则。

### 4）tag 覆盖策略

如果私库对 `latest` / `latest-*` 覆盖有限制，也要提前确认。
当前工作流设计本身会在每次新版本发布时覆盖这些 latest 类 tag。

---

## 九、推荐做法

如果你后续真的要改，建议按下面顺序操作：

1. 先在 GitHub Secrets 中配置第二仓库登录信息
2. 先只给 `release.yml` 增加第二仓库推送
3. 用一个测试 tag 验证 frontend / backend / combined 三类镜像是否都成功
4. 再修改 `release-native.yml`
5. 最后再决定是否把第二仓库地址写进 Release Notes 和 README

这样排查问题会更简单。

---

## 十、最小改动总结

如果只追求“同时推送到第二个仓库”，最小改动其实只有两类：

1. **增加一次 login**
2. **给每个构建步骤的 `tags:` 再追加第二仓库的 tags**

也就是说，不需要新增第二次构建；同一次 `docker/build-push-action` 就可以把同一个镜像推送到多个 registry。

