## 1. UI 模块与路由

- [x] 1.1 创建 `ui/` 模块并接入当前多模块构建
- [x] 1.2 在 `ui/src/index.ts` 中于系统菜单下注册 Console 路由
- [x] 1.3 确保 UI 构建产物正确打包进插件资源

## 2. Provider Workspace

- [x] 2.1 创建 `ProviderManager.vue` 作为主管理页面，采用"左侧 provider 列表 + 右侧 provider workspace"的布局
- [x] 2.2 创建 `ProviderList.vue`，支持 provider 搜索、状态展示和切换
- [x] 2.3 创建 `ProviderDetail.vue`，在同一页面展示 provider 配置与关联模型列表
- [x] 2.4 创建 `ProviderForm.vue` 用于创建/编辑 provider，支持 baseUrl、apiKeySecretName、enabled 和高级配置
- [x] 2.5 对内置 provider type 提供"选厂商 + 填密钥"的预设表单；仅 `openailike` 暴露必填 `baseUrl`
- [x] 2.6 实现 Halo Secret 绑定/创建流程，以及密钥脱敏展示与替换交互

## 3. 模型管理

- [x] 3.1 创建 `ModelList.vue`，按 group 分组展示模型，并显示 capability 标签
- [x] 3.2 创建 `ModelForm.vue` 用于添加/编辑模型，支持 modelId、displayName、group、capabilities、endpointType、supportedTextDelta
- [x] 3.3 实现提供商增删改查操作，使用 `@tanstack/vue-query` 和 `axiosInstance`
- [x] 3.4 实现模型增删改查操作，使用 `@tanstack/vue-query` 和 `axiosInstance`
- [x] 3.5 实现从提供商 API 获取模型列表、筛选、批量添加和共享默认值设置
- [x] 3.6 实现模型搜索、按 capability 过滤、按 group 折叠展示
- [x] 3.7 添加表单校验（provider 结构化字段、模型唯一性、providerResourceName/modelId 必填），并与服务端校验规则保持一致
- [x] 3.8 删除 provider 前检测是否仍有关联模型，并在 UI 中阻止删除

## 4. 调试与权限

- [x] 4.1 实现连通性测试按钮，包含加载状态、检测结果和 `lastCheckedAt` 展示
- [x] 4.2 接入 `test-chat` 调试接口，允许管理员以 `providerResourceName/modelId + prompt` 发送测试请求
- [x] 4.3 确保页面受适当的 RBAC 权限保护，并在无权限时隐藏菜单
- [x] 4.4 使用 `@halo-dev/components` 和 UnoCSS 进行 UI 样式设计
