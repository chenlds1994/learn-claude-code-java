---
kind: dependency_management
name: 多语言项目依赖管理：Maven + Next.js（npm）双栈管理
category: dependency_management
scope:
    - '**'
source_files:
    - pom.xml
    - web/package.json
    - web/package-lock.json
    - web/.gitignore
---

## 1. 使用的系统/方法

本项目为 Java + Next.js 前端的双栈仓库，采用两套独立的依赖管理系统：
- **Java 后端**：使用 Maven（`pom.xml`），通过 `dependencies` 声明第三方库，版本集中在 `<properties>` 中统一管理。
- **Web 前端**：使用 npm（`web/package.json` + `web/package-lock.json`），通过 `dependencies` / `devDependencies` 区分运行时与开发时依赖。

没有发现 Go、Python、Rust 等其他语言的依赖管理文件；也没有私有仓库或代理配置。

## 2. 关键文件
- `pom.xml`：Maven 工程描述与依赖声明，定义 Java 17 编译目标、UTF-8 编码、Jackson 与 dotenv-java 等依赖。
- `web/package.json`：Next.js 前端依赖清单，声明 React 19、Next 16.1.6、framer-motion、rehype/remark 生态、Tailwind CSS v4 等。
- `web/package-lock.json`：npm 锁文件，锁定前端依赖树精确版本。
- `web/.gitignore`：忽略 `node_modules`，不提交前端依赖包。
- `target/`：Maven 构建产物目录，不纳入版本控制。

## 3. 架构与约定
- **集中化版本管理**：Maven 将 Jackson 相关依赖的版本统一抽取到 `<properties><jackson.version>2.17.2</jackson.version></properties>`，所有 jackson-databind、jackson-datatype-jsr310 均引用 `${jackson.version}`，避免版本漂移。
- **插件版本固定**：`maven-compiler-plugin`（3.13.0）、`exec-maven-plugin`（3.5.0）在 `<build><plugins>` 中显式声明版本号，保证构建可重现。
- **运行期参数集中配置**：`exec-maven-plugin` 通过 `<systemProperties>` 强制设置 `file.encoding`、`sun.stdout.encoding`、`sun.stderr.encoding` 为 UTF-8，确保控制台输出一致性。
- **前后端解耦**：Java 与 Web 各自维护独立依赖清单，互不影响；前端脚本通过 `tsx scripts/extract-content.ts` 在 `predev` / `prebuild` 钩子中执行内容提取任务。
- **无 vendoring**：未发现 `vendor/`、`lib/` 等本地打包的第三方代码；所有依赖均通过远程仓库（Maven Central、npm registry）拉取。

## 4. 约定与约束
- **Java 依赖**：仅引入最小集——`dotenv-java`（环境变量读取）、`jackson-databind` 与 `jackson-datatype-jsr310`（JSON 序列化/反序列化及 JSR-310 日期类型支持）。未引入 Spring、HTTP 客户端等重型框架，保持教学代码轻量。
- **前端依赖**：运行时依赖聚焦于 Next.js 应用渲染（react/react-dom/next）、可视化动画（framer-motion）、图标（lucide-react）、Markdown/diff 处理（rehype/remark/diff）；开发时依赖包含 TypeScript、Tailwind CSS v4、@types/* 类型声明。
- **版本策略**：Maven 使用固定版本号（如 `3.0.0`、`2.17.2`），前端使用语义化范围（如 `^8.0.3`、`^12.34.0`、`^4`），两者结合 lockfile 保证可重复安装。
- **无私有源/代理**：未在 `pom.xml` 的 `<repositories>` 或 `settings.xml` 中发现自定义仓库；也未见 `.npmrc`、`.yarnrc` 等私有注册表配置。默认使用 Maven Central 与 npm 公共 registry。
- **构建环境**：Java 编译目标锁定为 JDK 17（`maven.compiler.source/target=17`），前端使用 Node 生态（TypeScript 5.x、React 19、Next 16），需匹配对应运行时环境。
- **无 CI 自动化更新**：仓库中未发现 Dependabot、Renovate 等自动升级配置文件，依赖升级需手动编辑 `pom.xml` / `package.json` 后重新生成 lockfile。