# model-discovery-search Specification

## Purpose
TBD - created by archiving change add-model-discovery-search. Update Purpose after archive.
## Requirements
### Requirement: 搜索框显示在模型发现弹窗顶部
模型发现弹窗 SHALL 在模型列表上方显示一个搜索输入框。

#### Scenario: 弹窗打开时显示搜索框
- **WHEN** 用户打开模型发现弹窗
- **THEN** 弹窗顶部显示搜索输入框，placeholder 为 "搜索模型名称或 ID..."

### Requirement: 支持按 displayName 和 modelId 模糊搜索
搜索功能 SHALL 支持对 `displayName` 和 `modelId` 字段进行模糊匹配，使用 fuse.js 实现，阈值设为 0.3。

#### Scenario: 输入关键词过滤模型列表
- **WHEN** 用户在搜索框输入关键词
- **THEN** 模型列表实时显示匹配的模型（displayName 或 modelId 包含近似匹配）

#### Scenario: 清空搜索框显示全部模型
- **WHEN** 用户清空搜索框内容
- **THEN** 模型列表恢复显示全部可发现模型

### Requirement: 搜索无结果时显示独立提示
当搜索关键词未匹配到任何模型时，弹窗 SHALL 显示 "未找到匹配的模型" 提示，与"无法获取到模型列表"区分开。

#### Scenario: 搜索无匹配结果
- **WHEN** 用户输入一个不存在的模型名称
- **THEN** 弹窗显示 "未找到匹配的模型" 空状态提示

### Requirement: 已选模型状态不受搜索过滤影响
搜索过滤 SHALL 不影响已选模型的选择状态。导入操作 SHALL 始终从原始完整模型列表中读取已选项。

#### Scenario: 搜索过滤后导入已选模型
- **GIVEN** 用户已选中某些模型
- **WHEN** 用户输入搜索关键词过滤掉部分已选模型
- **THEN** 点击"导入"按钮时，所有之前选中的模型（包括被过滤隐藏的）仍然被导入

