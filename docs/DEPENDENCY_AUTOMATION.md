# 依赖自动更新运行手册

## 1. 文档定位

本文档说明 `hello-websocket` 的 **GitHub 依赖自动化流程**。目标是：

1. 覆盖所有语言实现、Docker 基础镜像和 GitHub Actions。
2. 自动创建、验证和合并低风险依赖更新。
3. 阻止引入高危漏洞的依赖变更。
4. 保留主版本升级的人工兼容性评审。

---

## 2. 覆盖范围

| 实现 | Dependabot 生态 | 依赖文件 | 自动更新 |
|---|---|---|---|
| Java | `maven` | `pom.xml` | 是 |
| Kotlin | `gradle` | `*.gradle.kts` | 是 |
| Go | `gomod` | `go.mod`、`go.sum` | 是 |
| Rust | `cargo` | `Cargo.toml`、`Cargo.lock` | 是 |
| Node.js | `npm` | `package*.json` | 是 |
| TypeScript | `npm` | `package*.json` | 是 |
| Dart | `pub` | `pubspec.*` | 是 |
| PHP | `composer` | `composer.*` | 是 |
| Python | `pip` | `requirements*.txt` | 是 |
| C# | `nuget` | `*.csproj` | 是 |
| Swift | `swift` | `Package.swift` | 是 |
| C++ | 无外部 CMake 包 | Docker 工具链 | 间接覆盖 |
| Docker | `docker` | `Dockerfile.*` | 是 |
| GitHub Actions | `github-actions` | 工作流 | 是 |

C++ 当前没有 `FetchContent`、Conan 或 vcpkg 依赖。新增此类依赖时，必须同步扩展更新工具和校验脚本。

---

## 3. 自动化流程

### 3.1 版本更新

`.github/dependabot.yml` 每周一按生态错峰检查更新：

1. `minor` 和 `patch` 更新按语言分组，减少 PR 数量。
2. `major` 更新单独创建 PR。
3. 所有 PR 目标分支为 `main`。
4. Dependabot 自动 rebase。

### 3.2 PR 验证

每个依赖 PR 必须经过以下检查：

1. `Build & Test`：运行 12 种语言构建、测试和静态检查。
2. `Dependency Review`：拒绝引入 `high` 或 `critical` 漏洞。
3. `Docker Dependency Validation`：语言依赖或 Docker 基础镜像变化时，构建全部 server/client target。
4. 覆盖校验：确认生态、目录、Dockerfile 和 lockfile 完整。

### 3.3 自动合并

- `patch`、`minor`：启用 squash auto-merge。
- `major`：只运行 CI，不自动合并。
- CI 失败、高危漏洞、分支保护未满足：不允许合并。
- 非主版本安全修复可自动合并。

---

## 4. 周期安全扫描

`Dependency Health` 每周运行：

1. 使用 `OSV-Scanner` 递归扫描 manifest 和 lockfile。
2. 生成 SARIF 结果并上传到 `Security > Code scanning`。
3. 保存扫描结果制品，并在发现已知漏洞时使检查失败。
4. 验证 Dependabot 覆盖矩阵。
5. 支持 `workflow_dispatch` 手动执行。

---

## 5. GitHub 仓库设置

满足以下条件才允许无人值守合并：

1. 在 `Settings > Code security` 启用 Dependency graph、Dependabot alerts、security updates 和 Code scanning。
2. 在 `Settings > General > Pull Requests` 启用 `Allow auto-merge`。
3. 在 `Settings > Actions > General` 允许工作流写权限。
4. 为 `main` 配置分支保护，要求 `Build & Test` 和 `Dependency Review` 通过。
5. 要求分支保持最新，并禁止直接 push 到 `main`。

如果组织禁止 `GITHUB_TOKEN` 合并 PR，应改用最小权限 GitHub App token。

---

## 6. 日常操作

### 6.1 本地校验

```bash
ruby scripts/validate-dependency-automation.rb
```

### 6.2 手动触发

进入 GitHub Actions，选择 `Dependency Health`，执行 `Run workflow`。

### 6.3 暂停某个依赖

在对应生态配置中增加 `ignore`，并注明原因、负责人和恢复日期。不得关闭全部更新来规避单个兼容问题。

---

## 7. 故障处理

| 现象 | 排查方向 | 处理方式 |
|---|---|---|
| 没有更新 PR | 安全设置、目录配置 | 运行覆盖校验 |
| 自动合并未启用 | auto-merge、token 权限 | 检查仓库设置 |
| lockfile 反复变化 | manifest 不一致 | 重新生成 lockfile |
| Docker 更新失败 | 镜像平台、构建阶段 | 查看失败语言 |
| OSV 扫描失败 | 新增高危漏洞 | 升级或替换依赖 |
| major PR 积压 | 缺少负责人 | 指派语言维护者 |

---

## 8. 验收标准

满足以下条件才认为依赖自动化完整：

1. 覆盖校验输出 13 个更新目标和 12 个 Dockerfile。
2. 非主版本 PR 自动运行全量 CI。
3. CI 通过后能够 squash 合并。
4. 主版本升级不会自动合并。
5. 高危变更被 `Dependency Review` 阻止。
6. OSV 扫描可手动和定时执行。
7. 所有 lockfile 与 manifest 一致。
