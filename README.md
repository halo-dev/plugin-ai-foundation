# Halo AI Foundation

简体中文 | [English](./README_EN.md)

Halo 官方 AI 能力平台，统一接入主流大模型，为插件生态提供文本生成、嵌入向量、工具调用等智能化能力。

![](./images/preview-providers.png)

## 功能特性

- **多提供商支持**：内置 OpenAI、OpenRouter、DeepSeek、月之暗面 Kimi、硅基流动、阿里云百炼、豆包、文心一言、智谱开放平台、Ollama、OpenAI 兼容、AIHubMix、Gitee 模力方舟、MiniMax 和 Xiaomi MiMo
- **统一模型管理**：在 Halo 控制台管理 Provider、API Key Secret、语言 / Embedding / Rerank / 图像生成模型、Adapter 与模型能力
- **模型自动发现**：从 Provider 拉取模型，并保留远程声明、内建能力和管理员覆盖的来源信息
- **统一参数映射**：按 Provider 或模型配置采样、重试、推理、Embedding、Rerank 和图像参数到供应方字段的映射
- **文本与多模态生成**：支持非流式与流式文本、图片 / 文件输入、推理内容、来源、结构化输出和多步骤结果
- **工具调用**：支持服务端与外部工具、流式工具输入、审批、输入修复、并行工具调用和步骤控制
- **Embedding、Rerank 与 RAG**：提供文本向量、批处理、相似度、重排和可组合的检索 / 重排 / 上下文注入 middleware
- **图像生成**：支持文生图、图生图、蒙版编辑、多图聚合和图像 middleware
- **UI Message Stream**：提供可持久化的消息协议、SSE 响应、消息校验 / 转换、取消和前端工具续跑
- **默认模型设置**：分别配置语言、Embedding、Rerank 和图像生成默认模型
- **模型测试工作台**：在控制台验证对话、Embedding、Rerank、图像生成和单轮 RAG，并查看流事件、用量、warning 与诊断信息
- **消费方 SDK**：提供 Provider-neutral Java API、浏览器 / Vue npm 包和 FormKit `aiModelSelector`

## 已接入插件

- [AI 回评](https://www.halo.run/store/apps/app-mo5tivjt)
- [Live2d 看板娘](https://www.halo.run/store/apps/app-oPNFQ)
- [智阅全能AI助手](https://www.halo.run/store/apps/app-OWBzA)
- [评论组件 Next](https://www.halo.run/store/apps/app-p8xona4f)
- [轻言](https://www.halo.run/store/apps/app-cmisffbv)

## 界面预览

![](./images/preview-providers.png)

![](./images/preview-models.png)

![](./images/preview-default.png)

![](./images/preview-test.png)

## 项目结构

本项目为多模块 Gradle 项目：

| 模块   | 说明                                                                                         |
| ------ | -------------------------------------------------------------------------------------------- |
| `api/` | 对外发布的 Java SDK（`run.halo.aifoundation:api`）。其他 Halo 插件依赖此模块即可调用 AI 能力 |
| `app/` | 插件实现模块。包含 Extension 定义、提供商类型、Endpoint、Service 实现和 RBAC 配置            |
| `ui/`  | 基于 Vue 3 + Rsbuild 的控制台界面，用于提供商和模型的可视化管理                              |

## 开发环境

- Java 21
- Node.js 24
- pnpm
- Docker（`haloServer` 开发服务器需要）

## 开发

```bash
# 1. 启动 Halo 开发服务器（会自动构建并加载插件）
./gradlew haloServer

# 2. 启动前端开发服务器
cd ui && pnpm install && pnpm dev
```

开发服务器启动后，访问 `http://127.0.0.1:8090/console/`（默认账号 admin / admin）即可在控制台中看到「Ai Foundation」菜单。

修改后端代码后，重载插件：

```bash
./gradlew reloadPlugin
```

修改后端 API 或字段后，重新生成前端 API 客户端：

```bash
./gradlew generateApiClient
```

## 构建

```bash
# 完整构建（后端 + 前端 + 测试）
./gradlew build

# 仅编译检查
./gradlew compileJava

# 运行测试
./gradlew test
```

构建完成后，插件 JAR 文件位于 `app/build/libs/` 目录。

## 其他插件集成

其他 Halo 插件可以通过依赖 `api` 模块调用语言、Embedding、Rerank、图像生成、RAG 和
UI Message 能力。

本插件还注册了 FormKit `aiModelSelector` 输入，供其他插件在设置页中选择已配置的 AI 模型。

- [SDK Core](./dev/zh-CN/sdk-core/README.md)：Java 后端的文本、工具、结构化输出、Embedding、
  Rerank、RAG、图像生成、middleware 与错误处理。
- [SDK UI](./dev/zh-CN/sdk-ui/README.md)：Vue Chat、消息持久化、工具交互、Completion、
  Object stream、Transport 与 UI Message 协议。
- [插件集成示例](./dev/zh-CN/plugin-integration-examples.md)：Halo 插件依赖、后端模型调用、
  UI Message Endpoint、Vue Chat 与模型设置。

[SDK Core 单页参考](./dev/zh-CN/dev.md) 与
[UI Message Stream 单页参考](./dev/zh-CN/ui-message-stream.md) 适合全文搜索。

## AI 辅助开发

仓库提供可分发的
[`use-ai-foundation-sdk`](./skills/use-ai-foundation-sdk/SKILL.md) Skill。可以使用
[Skills CLI](https://skills.sh/docs/cli) 安装到当前项目：

```bash
npx skills add halo-dev/plugin-ai-foundation --skill use-ai-foundation-sdk
```

也可以为 Codex 全局安装：

```bash
npx skills add halo-dev/plugin-ai-foundation --skill use-ai-foundation-sdk --global --agent codex
```

安装后通过 `$use-ai-foundation-sdk` 调用。Skill 会识别目标插件使用的 SDK 版本，优先复用
本地已安装的 Java 或 npm 依赖；只有需要更多文档或源码上下文时才拉取对应版本的官方仓库。

## 许可证

[GPL-3.0](./LICENSE) © Halo
