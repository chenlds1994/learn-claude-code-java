---
kind: build_system
name: Maven + Next.js 双前端构建系统
category: build_system
scope:
    - '**'
source_files:
    - pom.xml
    - web/package.json
    - web/next.config.ts
    - web/tsconfig.json
    - web/postcss.config.mjs
    - web/scripts/extract-content.ts
    - .env
    - src/main/java/com/learnclaudecode/Main.java
---

## 1. 使用的构建系统

本项目采用**双构建体系**：后端 Java Agent 运行时使用 **Maven**（单工程），前端教学可视化站点使用 **Next.js**（npm/Node）。根目录无 Makefile、Dockerfile、CI 流水线或脚本，构建完全依赖 Maven 与 npm 自身生命周期。

## 2. 关键文件

- `pom.xml`：Maven 工程定义，声明 JDK 17、Jackson 2.17.2、JUnit 5.10.2 等依赖，配置 maven-compiler-plugin 3.11.0、maven-surefire-plugin 3.1.2、exec-maven-plugin 3.5.0。
- `web/package.json`：Next.js 应用入口，提供 `dev` / `build` / `start` / `extract` 脚本。
- `web/next.config.ts`、`web/tsconfig.json`、`web/postcss.config.mjs`：Next.js 编译与 TypeScript/Tailwind 配置。
- `.env`：项目级环境变量（由后端 `dotenv-java` 加载）。
- `src/main/java/com/learnclaudecode/Main.java`：Java 应用入口，通过 exec-maven-plugin 的 systemProperties 统一设置 UTF-8 编码。

## 3. 架构与约定

### 后端（Maven 单工程）
- 源码位于 `src/main/java/com/learnclaudecode/`，测试位于 `src/test/java/com/learnclaudecode/`，遵循标准 Maven 布局。
- 版本固定为 `1.0.0`，所有依赖版本在 `<properties>` 中集中声明（`jackson.version`、`junit.version`），避免散落的硬编码。
- 运行期通过 `exec-maven-plugin` 注入 `file.encoding`、`sun.stdout.encoding`、`sun.stderr.encoding` 三个系统属性强制 UTF-8，保证跨平台输出一致。
- 无自定义 assembly/shade 插件，产物即标准 `target/*.jar`；无 Spring Boot 等可执行打包插件，需通过 `java -cp` 或 IDE 直接运行 `Main`。

### 前端（Next.js）
- 独立子工程 `web/`，使用 Next.js 16.1.6 + React 19.2.3 + TypeScript 5 + Tailwind v4。
- 构建脚本约定：`npm run extract`（基于 `tsx scripts/extract-content.ts` 从 skills/ 文档提取内容生成数据）作为 `predev` 与 `prebuild` 钩子自动执行，确保构建前数据同步。
- 开发流程：`npm run dev` → `next dev`；生产构建：`npm run build` → `npm run start`。
- 部署目标为 Vercel（存在 `vercel.json`），未包含 Docker 化步骤。

### 多语言/模块组织
- 无 Gradle、无多模块 POM，所有 S01~SFull 渐进阶段以同包下不同类（`S01AgentLoop.java` … `S17GoalLoop.java`、`SFull.java`）呈现，由 `Main` 选择启动。
- 前端与后端共享 `skills/` 目录下的 SKILL.md 文档，前端通过 `scripts/extract-content.ts` 解析并注入到可视化页面。

## 4. 约定与约束

- **JDK 版本锁定**：`maven.compiler.source/target=17`，要求构建环境安装 JDK 17。
- **编码强制 UTF-8**：通过 `exec-maven-plugin` 的系统属性与 `project.build.sourceEncoding=UTF-8` 双重保障，避免 Windows 默认 GBK 导致的乱码。
- **依赖版本集中管理**：Jackson、JUnit 等第三方库版本集中在 `<properties>`，新增依赖应沿用此模式。
- **测试框架**：统一使用 JUnit Jupiter 5.10.2，测试类位于 `src/test/java`，由 maven-surefire-plugin 自动发现执行。
- **前端构建前置步骤**：任何 `npm run dev` / `npm run build` 都会先执行 `tsx scripts/extract-content.ts`，不得跳过该步骤。
- **无 CI/CD 配置**：仓库未包含 GitHub Actions、GitLab CI、Jenkinsfile、Dockerfile 等自动化流水线；发布/部署依赖本地 Maven + npm 命令手动完成。
- **无容器化**：未发现 Dockerfile 或 docker-compose 文件，后端不以内嵌镜像方式交付。
- **环境变量来源**：后端通过 `io.github.cdimascio:dotenv-java` 读取根目录 `.env` 文件，无需额外启动参数。