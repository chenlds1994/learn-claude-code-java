---
kind: frontend_style
name: Next.js + Tailwind CSS v4 前端样式体系（CSS 变量主题、组件化 UI 与文档渲染）
category: frontend_style
scope:
    - '**'
source_files:
    - web/src/app/globals.css
    - web/package.json
    - web/postcss.config.mjs
    - web/next.config.ts
    - web/src/components/ui/badge.tsx
    - web/src/components/ui/card.tsx
    - web/src/components/layout/header.tsx
    - web/src/hooks/useDarkMode.ts
    - web/src/lib/utils.ts
---

## 1. 使用的系统与工具

- **框架**：Next.js 16（App Router，`output: "export"` 静态导出，配合 `trailingSlash: true`），React 19。
- **样式引擎**：Tailwind CSS v4，通过 `@tailwindcss/postcss` PostCSS 插件引入，入口为 `web/src/app/globals.css` 中的 `@import "tailwindcss"`。
- **主题系统**：基于 CSS 自定义属性（CSS Variables）的明/暗双主题。在 `:root` 中定义 `--color-bg`、`--color-text`、`--color-border`、`--color-layer-*` 等语义变量，`.dark` 类覆盖同名变量实现主题切换。
- **图标库**：`lucide-react`。
- **动画**：`framer-motion`（用于可视化图表的过渡）。
- **文档渲染**：remark/rehype 管线（`remark-gfm`、`rehype-highlight`、`rehype-stringify`、`unified`），配合自定义 `.prose-custom` 样式和 highlight.js 主题。

## 2. 关键文件

| 文件 | 作用 |
|---|---|
| `web/src/app/globals.css` | Tailwind 入口、CSS 变量主题、`.prose-custom` 文档样式、highlight.js 语法高亮主题 |
| `web/package.json` | 声明 Tailwind v4、Next.js、Framer Motion、Lucide 等依赖 |
| `web/postcss.config.mjs` | 仅启用 `@tailwindcss/postcss` 插件 |
| `web/next.config.ts` | 静态导出配置（`output: "export"`、`images.unoptimized`、`trailingSlash`） |
| `web/src/components/ui/badge.tsx` | 按 Agent 层级（tools/planning/memory/concurrency/collaboration）映射颜色 Token 的 Badge 组件 |
| `web/src/components/ui/card.tsx` | 基础 Card/CardHeader/CardTitle 原子组件，使用 Tailwind + `cn()` 组合 |
| `web/src/components/layout/header.tsx` | 全局 Header，负责 dark/light 模式切换、多语言切换、导航 |
| `web/src/hooks/useDarkMode.ts` | 监听 `<html class="dark">` 并返回 SVG 图表调色板（`useSvgPalette`） |
| `web/src/lib/utils.ts` | 提供 `cn(...)` 工具（`clsx` + `tailwind-merge` 风格） |
| `web/src/i18n/messages/*.json` | 国际化文案（en/ja/zh） |

## 3. 架构与设计约定

### 3.1 主题与 Token
- 所有页面级背景、文字、边框色统一走 CSS 变量（`var(--color-bg)`、`var(--color-text)`、`var(--color-border)`），避免硬编码颜色。
- 业务层颜色以 `--color-layer-tools`、`--color-layer-planning`、`--color-layer-memory`、`--color-layer-concurrency`、`--color-layer-collaboration` 五个语义 Token 表达 Agent 运行时各层，并在 `LayerBadge` 中以 Tailwind 类名形式复用。
- 深色模式通过给 `<html>` 添加/移除 `.dark` 类实现，`globals.css` 中用 `.dark` 选择器覆盖变量；Header 内通过 `localStorage("theme")` 持久化用户偏好。

### 3.2 组件化 UI 策略
- 通用 UI 集中在 `src/components/ui/`：Badge、Card、Tabs 等原子组件，全部使用 Tailwind 原子类 + `cn()` 拼接，支持 `className` 透传覆盖。
- 业务组件按功能域分目录：`architecture/`、`code/`、`diff/`、`docs/`、`layout/`、`simulator/`、`timeline/`、`visualizations/`，每个子目录对应一个教学阶段或视图。
- 可视化图表（Agent Loop、Workflow 等）通过 `useSvgPalette()` hook 根据当前主题动态输出 SVG 颜色对象，保证图表与主题一致。

### 3.3 文档渲染样式
- 自定义 `.prose-custom` 类集中管理 Markdown 渲染后的排版：标题、段落、列表（有序列表带编号徽章）、表格、引用块、代码块（含语言标签）、水平分割线等。
- 代码块使用 `rehype-highlight` + 自定义 highlight.js 主题（`.hljs-*` 选择器），支持 `data-language` 属性显示语言标识。
- 响应式：`@media (max-width: 640px)` 下调小 `pre/code` 字号。

### 3.4 构建与部署约束
- Next.js 配置为纯静态站点导出（`output: "export"`），图片禁用优化（`images.unoptimized`），因此所有样式必须自包含于 CSS 中，不依赖服务端 API。
- 通过 `postcss.config.mjs` 仅挂载 Tailwind v4 插件，无额外 CSS 预处理链。

## 4. 约定与约束

- **样式来源单一**：所有样式最终由 `globals.css` 经 Tailwind v4 编译产出，组件内不使用独立 CSS 模块或 styled-components。
- **主题开关机制固定**：深色模式通过 `document.documentElement.classList.toggle("dark", ...)` 控制，状态持久化到 `localStorage("theme")`，新组件应沿用此方式而非自行实现。
- **颜色不可硬编码**：页面级背景/文字/边框必须使用 `var(--color-*)`；业务层颜色必须走 `LAYER_COLORS` 映射或新增 CSS 变量，禁止在组件中直接写十六进制色值。
- **组件 className 合并**：所有 UI 组件必须通过 `cn()` 合并外部传入的 `className`，以保证主题类（如 `dark:`）可被正确合并。
- **SVG 图表配色**：图表组件必须通过 `useSvgPalette()` 获取颜色，不得硬编码节点/边/箭头颜色。
- **文档内容**：Markdown 渲染容器必须加 `prose-custom` 类以获得统一的排版与高亮主题。
- **移动端适配**：头部导航在 `md:hidden` 下切换汉堡菜单，所有交互元素最小触控尺寸不低于 `min-h-[44px] min-w-[44px]`。
- **国际化**：文案通过 `useTranslations("nav").key` 从 `src/i18n/messages/{en,zh,ja}.json` 读取，禁止在组件中硬编码英文字符串。