---
kind: dependency_management
name: Maven + npm 双语言依赖管理（集中版本属性与锁定文件）
category: dependency_management
scope:
    - '**'
source_files:
    - pom.xml
    - web/package.json
    - web/package-lock.json
---

## 1. 使用的系统/方法

本项目采用**双语言、双仓库式**的依赖管理策略：
- **Java 后端**：使用 **Maven**（`pom.xml`）作为唯一依赖声明与构建入口，通过 `<properties>` 集中管理第三方库版本。
- **Next.js 前端**：使用 **npm**（`web/package.json` + `web/package-lock.json`）进行依赖声明与锁定。

两个子工程相互独立，不存在跨语言的共享依赖或统一的依赖聚合层。

## 2. 关键文件

| 文件 | 作用 |
|---|---|
| `pom.xml` | Maven 单工程 POM，声明 Java 依赖、插件及编译属性 |
| `web/package.json` | Next.js 应用依赖与脚本定义 |
| `web/package-lock.json` | npm 依赖锁定文件（已提交至仓库） |
| `.env` | 运行时环境变量（dotenv-java 读取），不属于依赖但影响外部服务接入 |

## 3. 架构与约定

### Maven（Java）
- **单模块工程**：`groupId=com.learnclaudecode`，`artifactId=learn-claude-code-java`，无 `<modules>` 聚合。
- **版本集中化**：所有可复用的第三方库版本集中在 `<properties>` 中定义（如 `jackson.version=2.17.2`、`junit.version=5.10.2`），具体依赖通过 `${...}` 引用，避免散落的硬编码版本号。
- **仅引入极少量依赖**：后端仅依赖 `dotenv-java`（配置加载）、`jackson-databind` + `jackson-datatype-jsr310`（JSON/时间序列化）、`junit-jupiter`（测试）。没有 Spring、HTTP 框架等重型依赖，符合教学工程的极简定位。
- **构建插件**：固定 `maven-compiler-plugin`、`maven-surefire-plugin`、`exec-maven-plugin` 的版本，并通过 exec 插件强制 UTF-8 编码输出。
- **源码/目标版本**：统一为 Java 17（`maven.compiler.source/target=17`）。
- **无私有仓库/镜像配置**：POM 未声明 `<repositories>`，默认使用中央仓库。

### npm（Next.js 前端）
- **依赖声明**：`web/package.json` 中按 `dependencies` / `devDependencies` 分类，生产依赖包括 Next.js、React、Framer Motion、rehype/remark 生态、diff 等。
- **锁定文件**：`web/package-lock.json` 已纳入版本控制，确保团队与 CI 安装结果一致。
- **脚本约定**：`predev` / `prebuild` 自动执行 `tsx scripts/extract-content.ts` 从 `skills/` 目录提取内容注入前端数据，体现“文档即数据”的前端依赖来源。
- **无私有 registry**：未配置 `.npmrc`，默认使用 npm 官方源。

## 4. 约定与约束

- **版本集中管理**：Maven 侧要求所有共享库版本必须放入 `<properties>` 并以变量形式引用（当前 jackson、junit 均遵循此模式）；新依赖若被多处使用，应优先加入 properties。
- **最小依赖原则**：项目刻意保持依赖数量极少，仅引入运行 Agent 运行时和演示所需的最少第三方库，便于教学理解。
- **前后端依赖隔离**：Java 与 Web 依赖完全解耦，各自维护各自的 manifest，不存在跨语言依赖解析。
- **无 vendoring**：未使用任何依赖打包/vendoring 工具（如 `mvn dependency:copy-dependencies` 产物仅在 `target/` 下生成，不入库；Node 依赖通过 `node_modules/` 本地安装，已被 `.gitignore` 忽略）。
- **无私有仓库/代理**：仓库内未发现 `~/.m2/settings.xml`、`.npmrc`、`sonatype` 等私有源配置，依赖全部来自公共仓库。
- **测试依赖范围明确**：JUnit 通过 `<scope>test</scope>` 限定，不会进入生产 classpath。
