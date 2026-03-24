# FxBlog

![Java](https://img.shields.io/badge/Java-25-437291?logo=openjdk&logoColor=white)
![TypeScript](https://img.shields.io/badge/TypeScript-5-3178C6?logo=typescript&logoColor=white)
![SCSS](https://img.shields.io/badge/SCSS-Sass-CC6699?logo=sass&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0--M2-6DB33F?logo=springboot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M3-6DB33F)
![Swagger](https://img.shields.io/badge/Swagger-OpenAPI%203-85EA2D?logo=swagger&logoColor=black)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16+-4169E1?logo=postgresql&logoColor=white)
![Redis](https://img.shields.io/badge/Redis-Cache-D82C20?logo=redis&logoColor=white)
![Qdrant](https://img.shields.io/badge/Qdrant-Vector%20DB-DC244C)
![Nuxt](https://img.shields.io/badge/Nuxt-4.4.2-00DC82?logo=nuxt&logoColor=white)
![Vue](https://img.shields.io/badge/Vue-3.5.30-42B883?logo=vuedotjs&logoColor=white)
![Vite](https://img.shields.io/badge/Vite-8.0.0-646CFF?logo=vite&logoColor=white)

![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)
![Codacy Badge](https://api.codacy.com/project/badge/Grade/5452bac245a94ba6899786194e064830)


`FxBlog` 是我的新[个人博客](https://blog.fxmarkbrown.top)网站项目，它是我面向新一轮架构升级整理的博客工程，包含博客后端、后台管理端以及新的 Nuxt 前台门户。

## 项目特性

- 后端基于 Spring Boot 4、Java 25、MyBatis-Plus、Spring Cache、Sa-Token
- 数据库使用 PostgreSQL，缓存使用 Redis
- 前台新门户基于 Nuxt 4、Vue 3、Pinia、TypeScript、SCSS，优先支持 SSR 与 SEO
- 后台管理端基于 Vue 3、Vite、Pinia、Element Plus
- 已集成 Spring AI，提供基于 Qdrant 的全站文章 RAG
- 异步事件链路已事件化并走 Java 虚拟线程
- 具有文件存储和定时任务能力

## 技术栈

- Java 25
- TypeScript
- SCSS


- Vue 3
- Nuxt 4
- Pinia
- Vite


- Spring Boot 4
- Spring AI 2
- MyBatis-Plus
- Sa-Token
- Spring Cache
- Swagger 3


- PostgreSQL
- Redis
- Qdrant

## 目录结构

```text
FxBlog
├── blog/               # 后端服务
├── blog-admin/         # 后台管理前端
├── blog-web-nuxt/      # 新前台门户
├── blog-web/           # 旧前台工程
└── blog-pg.sql         # PostgreSQL 初始化脚本
```

## 子项目说明

- [`blog`](blog)：Spring Boot 多模块后端，提供公开接口、后台接口、鉴权、文件、定时任务与 AI 能力
- [`blog-admin`](blog-admin)：后台管理端
- [`blog-web-nuxt`](blog-web-nuxt)：新的前台门户，面向 SSR 与 SEO

## 环境要求

- JDK 25
- Node.js LTS
- pnpm
- PostgreSQL 16+
- Redis

按启用功能不同，还可能需要：

- Qdrant
- 邮件服务
- 第三方 OAuth 配置
- 微信相关配置
- 对象存储或本地存储目录

## 快速开始

### 1. 初始化数据库

执行根目录下的 [`blog-pg.sql`](blog-pg.sql)。

### 2. 启动后端

进入 [`blog`](blog) 并配置环境变量后启动：

```bash
bash ./gradlew :module-server:bootRun
```

### 3. 启动后台管理端

进入 [`blog-admin`](blog-admin)：

```bash
pnpm install
pnpm dev
```

### 4. 启动前台门户

进入 [`blog-web-nuxt`](blog-web-nuxt)：

```bash
pnpm install
pnpm dev
```

### 5. （生产环境）配置 Nginx

参考 [`nginx_example.txt`](nginx_example.txt)

## 当前状态
- blog-web-nuxt: 前端门户正在持续改进中
- blog-admin: 后台管理转为稳定维护
- blog: SpringBoot后端转为稳定维护

## 致谢

- 感谢 [拾壹博客](https://gitee.com/quequnlong/shiyi-blog)（Apache License 2.0）项目，当前项目在演进过程中援引了其中部分代码与实现思路。
- 感谢 JetBrains 提供 IDE 与开发工具支持。
