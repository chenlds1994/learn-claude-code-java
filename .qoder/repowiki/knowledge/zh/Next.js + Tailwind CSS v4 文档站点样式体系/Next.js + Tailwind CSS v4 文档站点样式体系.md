---
kind: frontend_style
name: Next.js + Tailwind CSS v4 文档站点样式体系
category: frontend_style
scope:
    - '**'
source_files:
    - web/src/app/globals.css
    - web/postcss.config.mjs
    - web/package.json
    - web/src/hooks/useDarkMode.ts
    - web/src/components/layout/header.tsx
    - web/src/components/ui/card.tsx
    - web/src/lib/utils.ts
---

## 1. 使用的系统与工具

前端基于 **Next.js App Router**（v16）构建，样式方案采用 **Tailwind CSS v4**（通过 `@tailwindcss/postcss` 插件集成），配合 **PostCSS** 处理。项目未使用任何 UI 组件库（如 shadcn/ui、MUI、Ant Design），而是自实现轻量级原子化组件（`src/components/ui/`）。动画与图标分别使用 **Framer Motion** 和 **Lucide React**。代码高亮通过 rehype 生态（`rehype-highlight`、`rehype-raw`、`rehype-stringify`）结合自定义 Highlight.js 主题实现。

## 2. 关键文件

- `web/src/app/globals.css`：全局样式入口，导入 Tailwind、定义 CSS 变量主题、Prose 文档渲染样式、Highlight.js 语法高亮主题。
- `web/postcss.config.mjs`：仅启用 `@tailwindcss/postcss` 插件。
- `web/package.json`：声明 Tailwind v4、Next.js、React 19、Framer Motion、Lucide 等依赖。
- `web/src/hooks/useDarkMode.ts`：深色模式状态 Hook，提供 `useSvgPalette` 为可视化图表输出明暗两套配色。
- `web/src/components/layout/header.tsx`：导航栏，通过切换 `<html class="dark">` 控制主题。
- `web/src/components/ui/*.tsx`：自实现的 Card/Badge/Tabs 等基础 UI 组件。
- `web/src/lib/utils.ts`：导出 `cn` 工具函数（类名合并），供各组件组合 Tailwind 类。

## 3. 架构与设计约定

### 3.1 设计令牌（Design Tokens）
所有颜色以 **CSS 自定义属性** 集中声明在 `:root` 与 `.dark` 选择器中：
- 背景色：`--color-bg`、`--color-bg-secondary`
- 文本色：`--color-text`、`--color-text-secondary`
- 边框色：`--color-border`
- 语义层颜色：`--color-layer-tools`、`--color-layer-planning`、`--color-layer-memory`、`--color-layer-concurrency`、`--color-layer-collaboration`（用于 Agent 架构图的视觉分层）

这些变量被 Header、Sidebar 等布局组件直接通过 `var(--color-*)` 引用，保证全局一致。

### 3.2 主题系统（明/暗模式）
- 通过 `@custom-variant dark (&:where(.dark, .dark *));` 注册 Tailwind 的 `dark:` 变体。
- 深色模式由 `Header` 组件在客户端初始化时读取 `localStorage.theme` 或 `prefers-color-scheme`，并切换 `<html>` 的 `dark` 类。
- `useDarkMode` Hook 监听 DOM MutationObserver 同步状态；`useSvgPalette` 根据当前主题返回两套硬编码的 SVG 调色板对象，供可视化组件消费。

### 3.3 文档渲染样式（Prose Custom）
`globals.css` 中定义了完整的 `.prose-custom` 样式块，覆盖 h1–h4、段落、blockquote（含 hero-callout）、pre/code、列表、表格、hr、strong/em 等元素，并提供对应的 `.dark .prose-custom ...` 变体。该样式专门服务于通过 remark/rehype 管线渲染的教程内容。

### 3.4 代码高亮主题
通过自定义 `.hljs-*` 选择器实现一套偏紫/蓝/绿的暗色系语法高亮主题，覆盖关键字、字符串、注释、标题、删除/新增行等 token，与整体品牌色调保持一致。

### 3.5 响应式策略
- 使用 Tailwind 断点（`sm:`、`md:`、`lg:`）控制布局（如 Header 在 `md:hidden` 切换移动端菜单）。
- 针对小屏代码块单独降级：`@media (max-width: 640px) { pre, code { font-size: 11px; } }`。
- 移动端交互统一设置 `min-h-[44px] min-w-[44px]` 以满足触摸目标尺寸要求。

### 3.6 组件样式组织
- 通用 UI 组件集中在 `src/components/ui/`，每个组件用 `cn(...)` 合并默认 Tailwind 类与外部传入的 `className`，支持 `dark:` 前缀覆盖。
- 业务组件按功能域分目录（`architecture/`、`diff/`、`simulator/`、`timeline/`、`visualizations/`），样式全部内联于 JSX 的 className 中，无独立 CSS 模块。
- 可视化组件通过 `useSvgPalette` 获取主题色，避免在组件内部硬编码颜色。

## 4. 约定与约束

- **禁止引入第三方 UI 框架**：项目中没有引入 Ant Design、Material-UI、shadcn/ui 等，所有可复用 UI 均以 `components/ui/` 下的手写组件形式存在。
- **颜色必须走 CSS 变量**：全局背景、文本、边框等基础色通过 `--color-*` 变量暴露，布局组件直接使用 `var(--color-*)`，而非写死十六进制值。
- **深色模式通过 `class="dark"` 驱动**：所有主题相关样式均遵循 `:root` / `.dark` 双声明模式，组件内使用 Tailwind 的 `dark:` 前缀。
- **文档内容统一走 `.prose-custom`**：教程渲染产物需包裹在带有 `prose-custom` 类的容器中，以获得一致的排版风格。
- **SVG 可视化图的颜色通过 `useSvgPalette` 获取**：不直接在组件中硬编码节点/边颜色，以保证明暗主题自动适配。
- **Tailwind v4 语法**：使用 `@import "tailwindcss"` 替代旧版 `@tailwind base/utilities/components` 指令，并通过 `postcss.config.mjs` 中的 `@tailwindcss/postcss` 插件加载。
- **移动端最小触控区域**：按钮与链接统一设置 `min-h-[44px] min-w-[44px]`，确保可点击性。
- **无 Tailwind 配置文件**：项目未创建 `tailwind.config.*`，所有配置（包括自定义变体）均在 `globals.css` 中以 CSS 原生方式完成。