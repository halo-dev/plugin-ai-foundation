import {
  AiProviderStatusFailureCategoryEnum,
  AiProviderStatusPhaseEnum,
  ProviderHealthCheckResultEnum,
  ProviderHealthCheckSkipReasonEnum,
  ProviderHealthCheckTargetEnum,
  type AiProvider,
  type ProviderHealthCheck,
} from '@/api/generated'

type ProviderHealthStatus = AiProvider['status']

export const PROVIDER_CHECK_TARGET_ORDER: ProviderHealthCheckTargetEnum[] = [
  ProviderHealthCheckTargetEnum.Configuration,
  ProviderHealthCheckTargetEnum.Network,
  ProviderHealthCheckTargetEnum.Authentication,
  ProviderHealthCheckTargetEnum.ModelDiscovery,
  ProviderHealthCheckTargetEnum.Chat,
  ProviderHealthCheckTargetEnum.Embedding,
]

const CHECK_TARGET_LABELS: Record<ProviderHealthCheckTargetEnum, string> = {
  [ProviderHealthCheckTargetEnum.Configuration]: '配置检查',
  [ProviderHealthCheckTargetEnum.Network]: '网络连通',
  [ProviderHealthCheckTargetEnum.Authentication]: '认证',
  [ProviderHealthCheckTargetEnum.ModelDiscovery]: '模型发现',
  [ProviderHealthCheckTargetEnum.Chat]: '对话模型',
  [ProviderHealthCheckTargetEnum.Embedding]: '向量模型',
}

const CHECK_RESULT_LABELS: Record<ProviderHealthCheckResultEnum, string> = {
  [ProviderHealthCheckResultEnum.Passed]: '通过',
  [ProviderHealthCheckResultEnum.Failed]: '失败',
  [ProviderHealthCheckResultEnum.Skipped]: '已跳过',
}

const SKIP_REASON_LABELS: Record<ProviderHealthCheckSkipReasonEnum, string> = {
  [ProviderHealthCheckSkipReasonEnum.NotRequested]: '未请求此项检查',
  [ProviderHealthCheckSkipReasonEnum.NoModel]: '没有可用的模型',
  [ProviderHealthCheckSkipReasonEnum.ProviderDisabled]: '供应商已禁用',
  [ProviderHealthCheckSkipReasonEnum.BlockedByPreviousFailure]: '前序检查失败',
  [ProviderHealthCheckSkipReasonEnum.Unsupported]: '供应商类型不支持',
}

const FAILURE_CATEGORY_LABELS: Record<AiProviderStatusFailureCategoryEnum, string> = {
  [AiProviderStatusFailureCategoryEnum.Configuration]: '配置',
  [AiProviderStatusFailureCategoryEnum.Authentication]: '认证',
  [AiProviderStatusFailureCategoryEnum.Network]: '网络',
  [AiProviderStatusFailureCategoryEnum.RateLimited]: '限流',
  [AiProviderStatusFailureCategoryEnum.ProviderError]: '供应商故障',
  [AiProviderStatusFailureCategoryEnum.CapabilityUnsupported]: '能力不支持',
  [AiProviderStatusFailureCategoryEnum.Unknown]: '未知',
}

const FAILURE_CATEGORY_HINTS: Record<AiProviderStatusFailureCategoryEnum, string> = {
  [AiProviderStatusFailureCategoryEnum.Configuration]:
    '请检查 Base URL、端点路径、代理和 API Key 绑定。',
  [AiProviderStatusFailureCategoryEnum.Authentication]:
    '请确认 API Key 有效、未过期，或该供应商是否需要凭据。',
  [AiProviderStatusFailureCategoryEnum.Network]:
    '请检查服务器出网、DNS、TLS 证书和代理配置。',
  [AiProviderStatusFailureCategoryEnum.RateLimited]: '供应商限流，请稍后重试。',
  [AiProviderStatusFailureCategoryEnum.ProviderError]:
    '供应商服务异常，请稍后重试或查看其状态页。',
  [AiProviderStatusFailureCategoryEnum.CapabilityUnsupported]:
    '该供应商或模型不支持当前接口，请更换模型或调整端点配置。',
  [AiProviderStatusFailureCategoryEnum.Unknown]: '请查看原始错误摘要并检查供应商配置。',
}

const PHASE_LABELS: Record<AiProviderStatusPhaseEnum, string> = {
  [AiProviderStatusPhaseEnum.Unknown]: '未检查',
  [AiProviderStatusPhaseEnum.Ok]: '正常',
  [AiProviderStatusPhaseEnum.Error]: '异常',
}

function isKnownValue<T extends string>(map: Record<T, string>, value?: string): value is T {
  return !!value && Object.prototype.hasOwnProperty.call(map, value)
}

export function providerPhaseLabel(status?: ProviderHealthStatus): string {
  return isKnownValue(PHASE_LABELS, status?.phase)
    ? PHASE_LABELS[status.phase]
    : PHASE_LABELS[AiProviderStatusPhaseEnum.Unknown]
}

export function providerFailureCategoryLabel(category?: string): string | undefined {
  if (!category) {
    return undefined
  }
  return isKnownValue(FAILURE_CATEGORY_LABELS, category)
    ? FAILURE_CATEGORY_LABELS[category]
    : FAILURE_CATEGORY_LABELS[AiProviderStatusFailureCategoryEnum.Unknown]
}

export function providerFailureHint(category?: string): string | undefined {
  if (!category) {
    return undefined
  }
  return isKnownValue(FAILURE_CATEGORY_HINTS, category)
    ? FAILURE_CATEGORY_HINTS[category]
    : FAILURE_CATEGORY_HINTS[AiProviderStatusFailureCategoryEnum.Unknown]
}

export function providerStatusLabel(status?: ProviderHealthStatus): string {
  if (status?.phase === AiProviderStatusPhaseEnum.Ok) {
    return PHASE_LABELS[AiProviderStatusPhaseEnum.Ok]
  }
  if (status?.phase === AiProviderStatusPhaseEnum.Error) {
    const category = providerFailureCategoryLabel(status.failureCategory)
    return category ? `${PHASE_LABELS[AiProviderStatusPhaseEnum.Error]} · ${category}` : '异常'
  }
  return PHASE_LABELS[AiProviderStatusPhaseEnum.Unknown]
}

export function providerStatusDotState(status?: ProviderHealthStatus) {
  if (status?.phase === AiProviderStatusPhaseEnum.Ok) {
    return 'success' as const
  }
  if (status?.phase === AiProviderStatusPhaseEnum.Error) {
    return 'error' as const
  }
  return 'default' as const
}

export function truncateProviderText(value?: string, maxLength = 80): string | undefined {
  if (!value) {
    return undefined
  }
  const text = value.trim()
  if (!text) {
    return undefined
  }
  if (text.length <= maxLength) {
    return text
  }
  return `${text.slice(0, Math.max(0, maxLength - 1))}…`
}

export function providerCheckTargetLabel(target?: string): string {
  return isKnownValue(CHECK_TARGET_LABELS, target) ? CHECK_TARGET_LABELS[target] : '未知检查'
}

export function providerCheckResultLabel(result?: string): string {
  return isKnownValue(CHECK_RESULT_LABELS, result) ? CHECK_RESULT_LABELS[result] : '未知'
}

export function providerCheckResultDotState(result?: string) {
  switch (result) {
    case ProviderHealthCheckResultEnum.Passed:
      return 'success' as const
    case ProviderHealthCheckResultEnum.Failed:
      return 'error' as const
    default:
      return 'default' as const
  }
}

export function providerSkipReasonLabel(reason?: string): string | undefined {
  if (!reason) {
    return undefined
  }
  return isKnownValue(SKIP_REASON_LABELS, reason) ? SKIP_REASON_LABELS[reason] : '未知跳过原因'
}

export function providerCheckSummary(check: ProviderHealthCheck): string | undefined {
  if (check.result === ProviderHealthCheckResultEnum.Failed) {
    return (
      truncateProviderText(check.message, 160) ||
      providerFailureCategoryLabel(check.category) ||
      '检查失败'
    )
  }
  if (check.result === ProviderHealthCheckResultEnum.Skipped) {
    return providerSkipReasonLabel(check.skipReason)
  }
  return truncateProviderText(check.message, 160)
}

export function orderedProviderChecks(
  checks?: ProviderHealthCheck[] | null,
): ProviderHealthCheck[] {
  if (!checks?.length) {
    return []
  }
  const order = new Map(
    PROVIDER_CHECK_TARGET_ORDER.map((target, index) => [target as string, index]),
  )
  return [...checks].sort((left, right) => {
    const leftIndex = order.get(left.target as string) ?? Number.MAX_SAFE_INTEGER
    const rightIndex = order.get(right.target as string) ?? Number.MAX_SAFE_INTEGER
    return leftIndex - rightIndex
  })
}

export function formatProviderCheckMeta(check: ProviderHealthCheck): string | undefined {
  const parts: string[] = []
  if (check.httpStatus) {
    parts.push(`HTTP ${check.httpStatus}`)
  }
  if (typeof check.latencyMs === 'number') {
    parts.push(`${check.latencyMs} ms`)
  }
  return parts.length ? parts.join(' · ') : undefined
}
