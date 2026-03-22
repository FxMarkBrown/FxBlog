# 博客后端

![Java](https://img.shields.io/badge/Java-25-437291?logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-4.1.0--M2-6DB33F?logo=springboot&logoColor=white)
![Spring AI](https://img.shields.io/badge/Spring%20AI-2.0.0--M3-6DB33F)
![Qdrant](https://img.shields.io/badge/Qdrant-Vector%20DB-DC244C)
![Swagger](https://img.shields.io/badge/Swagger-OpenAPI%203-85EA2D?logo=swagger&logoColor=black)
![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16+-4169E1?logo=postgresql&logoColor=white)
![MyBatis-Plus](https://img.shields.io/badge/MyBatis--Plus-3.5.16-1F6FEB)
![Gradle](https://img.shields.io/badge/Gradle-Kotlin%20DSL-02303A?logo=gradle&logoColor=white)

当前项目的后端服务端工程，负责博客站点的公开接口、后台管理接口、鉴权、文件存储、定时任务与 AI 能力接入。

## 项目特性

- 基于 Spring Boot 4、Gradle Kotlin DSL、Java 25 的多模块后端架构
- 使用 MyBatis-Plus 作为 ORM 与数据访问基础
- 集成 Sa-Token，覆盖登录态、权限与会话管理
- 集成 SpringDoc + Knife4j，便于接口调试与文档查看
- 已接入 Spring Cache，适合在热点读场景下做注解化缓存
- 支持 Redis、文件存储、定时任务、第三方登录、微信公众号相关能力
- 集成 Spring AI + Qdrant，可承载对话、知识库检索等扩展能力
- 通知、文章浏览量、操作日志等链路已采用事件发布 + 异步监听方式解耦，走 Java 虚拟线程

## 技术栈

- Java 25
- Spring Boot 4
- SpringAI 2
- MyBatis-Plus
- Sa-Token
- Spring Cache + Redis
- SpringDoc + Knife4j
- Quartz
- x-file-storage
- Spring AI + Qdrant
- JustAuth / 微信相关 SDK

## 模块说明

- `module-server`：启动聚合模块，应用主入口在 `top.fxmarkbrown.blog.BlogApplication`
- `module-commom`：公共配置、实体、Mapper、通用工具与基础设施
- `module-api`：面向前台站点的公开接口
- `module-admin`：后台管理接口
- `module-auth`：登录鉴权、第三方登录、微信相关能力
- `module-file`：文件上传与多存储源接入
- `module-quartz`：定时任务能力
- `module-ai`：AI 对话、检索增强、向量存储相关能力

## 目录结构

```text
blog
├── build.gradle.kts
├── settings.gradle.kts
├── gradle/
├── module-admin/
├── module-ai/
├── module-api/
├── module-auth/
├── module-commom/
├── module-file/
├── module-quartz/
└── module-server/
```

## 环境要求

在本地启动前，至少需要准备以下依赖：

- JDK 25
- PostgreSQL 16+
- Redis

按你启用的功能不同，可能还需要：

- 对象存储服务，例如 OSS、COS、七牛或本地存储
- OpenAI 兼容模型服务或其他 AI 提供方
- Qdrant
- 邮件服务、第三方 OAuth 配置、微信相关配置

## 快速开始

### 1. 克隆并进入后端目录

```bash
git clone <your-repo-url>
cd blog
```

### 2. 配置环境变量

运行所需配置位于：

- `module-server/src/main/resources/application.yml`
- `module-server/src/main/resources/application-dev.yml`
- `module-server/src/main/resources/application-prod.yml`

### 3. 初始化 PostgreSQL

执行根目录下的 [`blog-pg.sql`](../blog-pg.sql) 初始化库表与基础数据。

### 4. 启动服务

推荐直接启动聚合模块：

```bash
bash ./gradlew :module-server:bootRun
```

如果需要完整构建：

```bash
bash ./gradlew build
```

## API 文档

项目已集成 SpringDoc 与 Knife4j。服务启动后，可先尝试访问：

- `/swagger-ui.html`
- `/v3/api-docs`

如果需要开放后台接口，建议结合鉴权与网关策略控制访问范围，而不是直接裸露文档入口。
