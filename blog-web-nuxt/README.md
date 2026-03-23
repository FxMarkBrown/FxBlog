# 博客前台

![Nuxt](https://img.shields.io/badge/Nuxt-4.4.2-00DC82?logo=nuxt&logoColor=white)
![Vue](https://img.shields.io/badge/Vue-3.5.30-42B883?logo=vuedotjs&logoColor=white)
![SCSS](https://img.shields.io/badge/SCSS-Sass-CC6699?logo=sass&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![SSR](https://img.shields.io/badge/Rendering-SSR%20%2B%20SWR-0F172A)

项目的前台门户，基于 Nuxt 构建，承担首页、文章详情、归档等内容页的 SSR 与 SEO 输出。

## 项目特性

- 基于 Nuxt 4、Vue 3、Pinia、TypeScript、SCSS
- 以 SSR 为核心，优先覆盖首页、文章详情、归档等公开内容页
- 复用现有后端接口，不单独维护第二套业务协议
- 通过 `useAsyncData`、运行时配置和 Nitro 代理统一处理前后端通信
- 已内置基础 SEO 配置、布局组件、天气装饰、搜索与内容页骨架

## 技术栈

- Nuxt 4
- Vue 3
- Element Plus
- Pinia 3
- TypeScript
- SCSS

## 目录结构

```text
blog-web-nuxt
├── nuxt.config.ts
├── package.json
├── public/
└── src/
    ├── api/
    ├── components/
    ├── composables/
    ├── layouts/
    ├── pages/
    ├── plugins/
    ├── stores/
    ├── styles/
    ├── utils/
    └── views/
```

## 环境要求

- Node.js LTS
- pnpm
- 已启动的后端服务，默认 `http://127.0.0.1:8800`

## 快速开始

### 1. 安装依赖

```bash
pnpm install
```

### 2. 配置环境变量

可参考项目内的 [`.env.example`](/mnt/e/Blog/Blog2/blog-web-nuxt/.env.example)。

当前主要变量：

- `NUXT_API_BASE_SERVER`
- `NUXT_PUBLIC_SITE_URL`
- `NUXT_PUBLIC_SITE_NAME`
- `NUXT_PUBLIC_RECORD_NUM`
- `NUXT_PUBLIC_ADMIN_URL`

示例：

```bash
NUXT_API_BASE_SERVER=http://127.0.0.1:8800
NUXT_PUBLIC_SITE_URL=http://localhost:3000
NUXT_PUBLIC_SITE_NAME=Blog
NUXT_PUBLIC_ADMIN_URL=http://localhost:3001
```

### 3. 启动开发环境

```bash
pnpm dev
```

默认开发地址通常为 `http://localhost:3000`。

### 4. 构建与预览

```bash
pnpm build
pnpm preview
```

如果需要纯静态产物，也可以执行：

```bash
pnpm generate
```
