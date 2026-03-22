# 博客管理后台

![Vue](https://img.shields.io/badge/Vue-3.5.30-42B883?logo=vuedotjs&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-8.0.0-646CFF?logo=vite&logoColor=white)
![SCSS](https://img.shields.io/badge/SCSS-Sass-CC6699?logo=sass&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)

项目的后台管理前端，负责系统配置、文章管理、消息管理、监控页、AI 管理页等后台能力。

## 项目特性

- 基于 Vue 3、Vite、Pinia、TypeScript、SCSS
- 使用 Element Plus 作为后台交互组件基础
- 覆盖文章、分类、标签、评论、文件、系统设置、监控、AI 配额等后台页面
- 内置权限处理、全局路由、自动导入、图标构建与统一请求层

## 技术栈

- Vue 3
- Element Plus
- Vite 8
- Pinia 3
- TypeScript
- SCSS

## 目录结构

```text
blog-admin
├── index.html
├── package.json
├── src/
│   ├── api/
│   ├── components/
│   ├── layouts/
│   ├── router/
│   ├── store/
│   ├── styles/
│   ├── utils/
│   └── views/
└── vite.config.ts
```

## 环境要求

- Node.js LTS
- pnpm
- 已启动的后端服务

## 快速开始

### 1. 安装依赖

```bash
pnpm install
```

### 2. 配置环境变量

当前主要变量：

- `VITE_APP_PORT`
- `VITE_APP_API_URL`
- `VITE_APP_BASE_API`
- `VITE_APP_BASE_PATH`
- `VITE_APP_SITE_URL`
- `VITE_APP_LOGO`

示例：

```bash
VITE_APP_PORT=3001
VITE_APP_API_URL=http://127.0.0.1:8800
VITE_APP_BASE_API=/api
VITE_APP_BASE_PATH=/
VITE_APP_SITE_URL=http://localhost:3000
VITE_APP_LOGO=/logo.svg
```

### 3. 启动开发环境

```bash
pnpm dev
```

如未额外配置，Vite 默认端口由 `VITE_APP_PORT` 控制。

### 4. 构建与预览

```bash
pnpm build
pnpm preview
```
