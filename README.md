# AI 基础设施（ai-foundation）

为 Halo 提供统一的 AI 模型配置与管理能力，并暴露 Java SDK 供其他插件调用 AI 能力。

## 功能特性

- **多提供商支持**：内置 OpenAI、DeepSeek、Kimi（Moonshot）、SiliconFlow、豆包、文心一言、智谱 AI、Ollama、OpenAI-like 等主流 AI 提供商
- **统一模型管理**：通过 Halo 控制台统一管理 AI 提供商和模型配置
- **模型自动发现**：支持从提供商自动拉取可用模型列表
- **流式对话**：支持 SSE 流式输出，适用于实时聊天场景
- **文本嵌入**：支持文本向量化，适用于语义搜索、RAG 等场景
- **Java SDK**：通过 `api` 模块为其他 Halo 插件提供标准化的 AI 调用接口

## 项目结构

本项目为多模块 Gradle 项目：

| 模块 | 说明 |
|------|------|
| `api/` | 对外发布的 Java SDK（`run.halo.aifoundation:api`）。其他 Halo 插件依赖此模块即可调用 AI 能力 |
| `app/` | 插件实现模块。包含 Extension 定义、提供商类型、Endpoint、Service 实现和 RBAC 配置 |
| `ui/` | 基于 Vue 3 + Rsbuild 的控制台界面，用于提供商和模型的可视化管理 |

## 开发环境

- Java 21+
- Node.js 18+
- pnpm

## 开发

```bash
# 1. 启动 Halo 开发服务器（会自动构建并加载插件）
./gradlew haloServer

# 2. 启动前端开发服务器（需另开终端）
cd ui && pnpm install && pnpm dev
```

开发服务器启动后，访问 `http://127.0.0.1:8090/console/`（默认账号 admin / admin）即可在控制台中看到「AI 基础设施」菜单。

修改后端代码后，需要重启开发容器：

```bash
docker rm halo-for-plugin-development -f && ./gradlew haloServer
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

其他 Halo 插件可以通过依赖 `api` 模块来调用本插件提供的 AI 能力。

详细集成说明请参考 [dev/dev.md](./dev/dev.md)。

## 许可证

[GPL-3.0](./LICENSE) © Halo
