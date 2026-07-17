# 依赖自动更新运行手册

## 1. 文档定位

本文档说明 `hello-websocket` 的 **GitHub 依赖自动化流程**及其当前实现状态。目标是：

1. 覆盖所有语言实现、Docker 基础镜像和 GitHub Actions。
2. 自动创建、验证依赖更新 PR。
3. 阻止引入高危漏洞的依赖变更。
4. 保留主版本升级的人工兼容性评审。

**实现状态**：`.github/dependabot.yml`（第 2 节覆盖矩阵）与 `.github/workflows/build-test.yml`（第 3.2 节 `Build & Test`）已实现并纳入版本控制。第 3.2 节的 `Dependency Review` 与 `Docker Dependency Validation`、第 4 节的周期性 `Dependency Health`/OSV 扫描、以及第 3.3 节的自动合并仍是**路线图（未实现）**，标注为「计划」。执行 `ruby scripts/validate-dependency-automation.rb` 前，请先确认本节的实现状态，不要假定路线图条目已生效。

---

## 2. 覆盖范围（已实现）

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

### 3.1 版本更新（已实现）

`.github/dependabot.yml` 每周一（Asia/Shanghai 时区）按生态错峰检查更新：

1. `minor` 和 `patch` 更新按语言分组，减少 PR 数量。
2. `major` 更新（不在分组内）单独创建 PR。
3. 所有 PR 目标分支为 `main`。
4. Dependabot 自动 rebase（`rebase-strategy: auto`）。

### 3.2 PR 验证

1. `Build & Test`（**已实现**，`.github/workflows/build-test.yml`）：在 push/PR 时对 12 种语言分别执行 `scripts/build.sh`（构建 + 测试）。
2. `Dependency Review`（**计划**）：拒绝引入 `high` 或 `critical` 漏洞的依赖变更。
3. `Docker Dependency Validation`（**计划**）：语言依赖或 Docker 基础镜像变化时，构建全部 server/client target。
4. 覆盖校验（**已实现**，`scripts/validate-dependency-automation.rb`）：确认生态、目录、Dockerfile 和 lockfile 完整。

### 3.3 自动合并（计划）

以下策略尚未配置为仓库自动化，合并仍需人工审阅：

- `patch`、`minor`：计划启用 squash auto-merge。
- `major`：计划仅运行 CI，不自动合并。
- CI 失败、高危漏洞、分支保护未满足：不允许合并。

---

## 4. 周期安全扫描（计划）

以下 `Dependency Health` 工作流尚未创建，是后续路线图：

1. 使用 `OSV-Scanner` 递归扫描 manifest 和 lockfile。
2. 生成 SARIF 结果并上传到 `Security > Code scanning`。
3. 保存扫描结果制品，并在发现已知漏洞时使检查失败。
4. 验证 Dependabot 覆盖矩阵。
5. 支持 `workflow_dispatch` 手动执行。

---

## 5. GitHub 仓库设置（计划）

以下条件尚未配置，是启用无人值守合并前需要完成的仓库设置：

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

校验 `.github/dependabot.yml` 的覆盖矩阵、调度策略与 lockfile 完整性；不校验第 3.2/4/5 节标注为「计划」的能力。

### 6.2 手动触发 Build & Test

进入 GitHub Actions，选择 `Build & Test`，执行 `Run workflow`（`workflow_dispatch`）。

### 6.3 暂停某个依赖

在对应生态配置中增加 `ignore`，并注明原因、负责人和恢复日期。不得关闭全部更新来规避单个兼容问题。

---

## 7. 故障处理

| 现象 | 排查方向 | 处理方式 |
|---|---|---|
| 没有更新 PR | 安全设置、目录配置 | 运行 `scripts/validate-dependency-automation.rb` |
| lockfile 反复变化 | manifest 不一致 | 重新生成 lockfile |
| `Build & Test` 失败 | 对应语言的 `scripts/build.sh` 输出 | 查看失败 job 日志 |
| （计划）OSV 扫描失败 | 新增高危漏洞 | 升级或替换依赖 |
| （计划）major PR 积压 | 缺少负责人 | 指派语言维护者 |

---

## 8. 验收标准

满足以下条件才认为**已实现部分**（第 2、3.1、3.2 的 `Build & Test`/覆盖校验、第 6 节）完整：

1. `scripts/validate-dependency-automation.rb` 校验通过，输出 13 个更新目标和 12 个 Dockerfile。
2. `Build & Test` 在 push/PR 时对 12 种语言全部运行并可手动触发。
3. 所有 lockfile 与 manifest 一致。

以下为路线图验收标准，尚未实现：

4. `Dependency Review`、`Docker Dependency Validation` 工作流落地并接入分支保护。
5. 非主版本 PR 通过 CI 后能够 squash 自动合并；主版本升级不自动合并。
6. OSV 扫描可手动和定时执行，高危变更被阻止。
