import type { AiProvider, ProviderHealthCheck } from '@/api/generated'
import {
  AiProviderStatusFailureCategoryEnum,
  AiProviderStatusPhaseEnum,
  ProviderHealthCheckResultEnum,
  ProviderHealthCheckSkipReasonEnum,
  ProviderHealthCheckTargetEnum,
} from '@/api/generated'
import { describe, expect, it } from 'vitest'
import {
  formatProviderCheckMeta,
  orderedProviderChecks,
  providerCheckResultDotState,
  providerCheckResultLabel,
  providerCheckSummary,
  providerCheckTargetLabel,
  providerFailureCategoryLabel,
  providerFailureHint,
  providerPhaseLabel,
  providerSkipReasonLabel,
  providerStatusDotState,
  providerStatusLabel,
  truncateProviderText,
} from './provider-health'

function check(partial: Partial<ProviderHealthCheck>): ProviderHealthCheck {
  return { ...partial }
}

describe('provider health labels', () => {
  it('labels every phase and treats legacy status as unchecked', () => {
    expect(providerPhaseLabel({ phase: AiProviderStatusPhaseEnum.Ok })).toBe('正常')
    expect(providerPhaseLabel({ phase: AiProviderStatusPhaseEnum.Error })).toBe('异常')
    expect(providerPhaseLabel({ phase: AiProviderStatusPhaseEnum.Unknown })).toBe('未检查')
    expect(providerPhaseLabel(undefined)).toBe('未检查')
    expect(providerStatusLabel(undefined)).toBe('未检查')
  })

  it('includes the failure category for abnormal providers', () => {
    expect(
      providerStatusLabel({
        phase: AiProviderStatusPhaseEnum.Error,
        failureCategory: AiProviderStatusFailureCategoryEnum.Authentication,
      }),
    ).toBe('异常 · 认证')
    expect(
      providerStatusLabel({
        phase: AiProviderStatusPhaseEnum.Error,
        failureCategory: AiProviderStatusFailureCategoryEnum.RateLimited,
      }),
    ).toBe('异常 · 限流')
    expect(providerStatusLabel({ phase: AiProviderStatusPhaseEnum.Error })).toBe('异常')
  })

  it('labels every check target in a stable order', () => {
    expect(
      Object.values(ProviderHealthCheckTargetEnum).map((target) =>
        providerCheckTargetLabel(target),
      ),
    ).toEqual(['配置检查', '网络连通', '认证', '模型发现', '对话模型', '向量模型'])
    expect(providerCheckTargetLabel(undefined)).toBe('未知检查')
    expect(providerCheckTargetLabel('FUTURE_TARGET')).toBe('未知检查')
  })

  it('labels every check result and maps dot states', () => {
    expect(providerCheckResultLabel(ProviderHealthCheckResultEnum.Passed)).toBe('通过')
    expect(providerCheckResultLabel(ProviderHealthCheckResultEnum.Failed)).toBe('失败')
    expect(providerCheckResultLabel(ProviderHealthCheckResultEnum.Skipped)).toBe('已跳过')
    expect(providerCheckResultDotState(ProviderHealthCheckResultEnum.Passed)).toBe('success')
    expect(providerCheckResultDotState(ProviderHealthCheckResultEnum.Failed)).toBe('error')
    expect(providerCheckResultDotState(ProviderHealthCheckResultEnum.Skipped)).toBe('default')
  })

  it('labels every skip reason', () => {
    expect(providerSkipReasonLabel(ProviderHealthCheckSkipReasonEnum.NotRequested)).toBe(
      '未请求此项检查',
    )
    expect(providerSkipReasonLabel(ProviderHealthCheckSkipReasonEnum.NoModel)).toBe(
      '没有可用的模型',
    )
    expect(providerSkipReasonLabel(ProviderHealthCheckSkipReasonEnum.ProviderDisabled)).toBe(
      '供应商已禁用',
    )
    expect(
      providerSkipReasonLabel(ProviderHealthCheckSkipReasonEnum.BlockedByPreviousFailure),
    ).toBe('前序检查失败')
    expect(providerSkipReasonLabel(ProviderHealthCheckSkipReasonEnum.Unsupported)).toBe(
      '供应商类型不支持',
    )
    expect(providerSkipReasonLabel(undefined)).toBeUndefined()
  })

  it('labels every failure category with an actionable hint', () => {
    for (const category of Object.values(AiProviderStatusFailureCategoryEnum)) {
      expect(providerFailureCategoryLabel(category)).toBeTruthy()
      expect(providerFailureHint(category)).toBeTruthy()
    }
    expect(providerFailureCategoryLabel(undefined)).toBeUndefined()
    expect(providerFailureHint(undefined)).toBeUndefined()
    expect(providerFailureCategoryLabel('FUTURE_CATEGORY')).toBe('未知')
    expect(providerFailureHint('FUTURE_CATEGORY')).toContain('原始错误摘要')
  })

  it('maps phase to the list status dot', () => {
    expect(providerStatusDotState({ phase: AiProviderStatusPhaseEnum.Ok })).toBe('success')
    expect(providerStatusDotState({ phase: AiProviderStatusPhaseEnum.Error })).toBe('error')
    expect(providerStatusDotState({ phase: AiProviderStatusPhaseEnum.Unknown })).toBe('default')
    expect(providerStatusDotState(undefined)).toBe('default')
  })
})

describe('provider check presentation', () => {
  it('summarizes failed, skipped and passed checks', () => {
    expect(
      providerCheckSummary(
        check({ result: ProviderHealthCheckResultEnum.Failed, message: 'HTTP 401 Unauthorized' }),
      ),
    ).toBe('HTTP 401 Unauthorized')
    expect(
      providerCheckSummary(
        check({
          result: ProviderHealthCheckResultEnum.Failed,
          category: AiProviderStatusFailureCategoryEnum.Network,
        }),
      ),
    ).toBe('网络')
    expect(
      providerCheckSummary(
        check({
          result: ProviderHealthCheckResultEnum.Skipped,
          skipReason: ProviderHealthCheckSkipReasonEnum.NoModel,
        }),
      ),
    ).toBe('没有可用的模型')
    expect(
      providerCheckSummary(
        check({ result: ProviderHealthCheckResultEnum.Passed, message: 'Authentication accepted' }),
      ),
    ).toBe('Authentication accepted')
  })

  it('orders checks by the fixed target order and keeps unknown targets last', () => {
    const checks = [
      check({ target: ProviderHealthCheckTargetEnum.Embedding }),
      check({ target: 'FUTURE_TARGET' as ProviderHealthCheckTargetEnum }),
      check({ target: ProviderHealthCheckTargetEnum.Configuration }),
      check({ target: ProviderHealthCheckTargetEnum.Network }),
    ]

    expect(orderedProviderChecks(checks).map((item) => item.target)).toEqual([
      ProviderHealthCheckTargetEnum.Configuration,
      ProviderHealthCheckTargetEnum.Network,
      ProviderHealthCheckTargetEnum.Embedding,
      'FUTURE_TARGET',
    ])
    expect(orderedProviderChecks(undefined)).toEqual([])
    expect(orderedProviderChecks([])).toEqual([])
  })

  it('formats http status and latency when available', () => {
    expect(formatProviderCheckMeta(check({ httpStatus: 401, latencyMs: 420 }))).toBe(
      'HTTP 401 · 420 ms',
    )
    expect(formatProviderCheckMeta(check({ latencyMs: 0 }))).toBe('0 ms')
    expect(formatProviderCheckMeta(check({}))).toBeUndefined()
  })

  it('truncates long summaries', () => {
    expect(truncateProviderText('short', 10)).toBe('short')
    expect(truncateProviderText('  ', 10)).toBeUndefined()
    expect(truncateProviderText(undefined, 10)).toBeUndefined()
    expect(truncateProviderText('a'.repeat(20), 10)).toBe(`${'a'.repeat(9)}…`)
  })

  it('treats providers without a health status as unchecked', () => {
    const legacyProvider = {
      metadata: { name: 'legacy' },
      spec: { providerType: 'openai', displayName: 'Legacy' },
    } as AiProvider

    expect(providerStatusLabel(legacyProvider.status)).toBe('未检查')
    expect(orderedProviderChecks(legacyProvider.status?.checks)).toEqual([])
  })
})
