---
kind: build_system
name: Maven + Next.js 双工程构建与静态站点发布
category: build_system
scope:
    - '**'
source_files:
    - pom.xml
    - web/package.json
    - web/next.config.ts
    - web/vercel.json
    - .env
---

## 1. 使用的构建系统

本项目为**多语言、双工程**仓库，分别使用两套独立的构建系统：
- **Java 后端（`src/`）**：基于 **Maven**（`pom.xml`），JDK 版本锁定为 17，通过 `exec-maven-plugin` 直接运行各阶段入口类（如 `S01AgentLoop`、`SFull` 等）。
- **Next.js 前端（`web/`）**：基于 **Next.js App Router**，通过 `npm` 脚本驱动开发、构建与启动；构建产物为静态导出（`output: "export"`），可直接部署到任意静态托管平台。

仓库中**不存在** Dockerfile、Makefile、CI 流水线（`.github/workflows`）、Gradle、`mvnw` 等跨平台或持续集成配置。所有构建均依赖本地已安装的 Maven（JDK 17）和 Node.js/npm。

## 2. 关键文件

- `pom.xml`：定义 groupId/artifactId/version、JDK 17 编译目标、UTF-8 编码、Jackson/dotenv-java 依赖、`maven-compiler-plugin` 与 `exec-maven-plugin` 插件配置。
- `web/package.json`：定义 `dev` / `build` / `start` / `extract` 脚本，以及 Next.js 16、React 19、Tailwind CSS v4 等依赖。
- `web/next.config.ts`：设置 `output: "export"`（静态导出）、`images.unoptimized: true`、`trailingSlash: true`，适配 Vercel/静态托管。
- `web/vercel.json`：Vercel 部署时的域名重定向规则（`learn-claude-agents.vercel.app` → `learn.shareai.run`）及根路径 `/` → `/en` 默认跳转。
- `.env`：存放运行时环境变量（如 Anthropic API Key），由 Java 侧 `dotenv-java` 读取。

## 3. 架构与约定

### 3.1 Java 工程
- 单模块 Maven 工程，源码位于 `src/main/java/com.learnclaudecode`。
- 每个学习阶段对应一个独立入口类（`S01AgentLoop` ~ `S12WorktreeTaskIsolation`、`SFull`），通过 `mvn exec:java -Dexec.mainClass=...` 单独运行，便于分步演示。
- 统一通过 `exec-maven-plugin` 强制 JVM 输出/输入编码为 UTF-8（`sun.stdout.encoding`、`sun.stderr.encoding`、`file.encoding`）。
- 版本号硬编码在 `pom.xml` 的 `<version>` 标签中（当前 `1.0.0`），无语义化版本管理或发布插件。

### 3.2 Next.js 前端
- 使用 Next.js 16 的 App Router，采用 `output: "export"` 生成纯静态 HTML/CSS/JS，不依赖 Node 运行时。
- 构建前自动执行 `tsx scripts/extract-content.ts`（通过 `prebuild` 钩子），将 Markdown/数据源提取为前端可消费的 JSON。
- 多语言通过 Next.js i18n 路由 `[locale]/[version]/...` 实现，默认语言为英文（`/` 重定向至 `/en`）。
- 部署目标为 Vercel，通过 `vercel.json` 处理自定义域名重定向。

## 4. 约定与约束

- **JDK 版本约束**：`pom.xml` 中 `maven.compiler.source/target` 固定为 `17`，要求构建环境安装 JDK 17。
- **编码约束**：项目级 `project.build.sourceEncoding=UTF-8`，并通过 `exec-maven-plugin` 在运行时再次强制 JVM 编码为 UTF-8，避免中文乱码。
- **运行方式约定**：Java 部分没有打包成 fat jar 或可执行 JAR，而是以 `exec:java` 形式直接运行各阶段入口类——这是教学场景下的刻意设计，便于逐阶段对比。
- **前端构建产物**：`web/build` 输出为静态文件，不包含 Node 服务器代码；生产部署只需拷贝该目录或使用支持静态站点的托管服务（Vercel、GitHub Pages 等）。
- **环境变量注入**：Java 端通过 `dotenv-java` 从根目录 `.env` 加载配置（如 Anthropic API Key），构建期不嵌入任何敏感信息。
- **无 CI/CD**：仓库未包含 GitHub Actions、GitLab CI 或其他自动化流水线；构建与发布需手动执行 `mvn compile/exec` 与 `npm run build`。
- **无容器化**：未发现 Dockerfile 或 docker-compose 配置，无法通过容器一键构建。
- **无单元测试框架**：`pom.xml` 中未引入 JUnit/TestNG 等测试依赖，也未发现 `src/test` 目录，说明该项目侧重演示而非质量保障流程。
