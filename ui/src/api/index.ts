import { axiosInstance } from '@halo-dev/api-client'
import {
  AiModelV1alpha1Api,
  AiProviderV1alpha1Api,
  ConsoleApiAifoundationHaloRunV1alpha1DefaultModelSlotApi,
  ConsoleApiAifoundationHaloRunV1alpha1ModelApi,
  ConsoleApiAifoundationHaloRunV1alpha1ProviderApi,
  ConsoleApiAifoundationHaloRunV1alpha1ProviderTypeApi,
} from './generated'

const aiCoreApiClient = {
  model: new AiModelV1alpha1Api(undefined, '', axiosInstance),
  provider: new AiProviderV1alpha1Api(undefined, '', axiosInstance),
}

const aiConsoleApiClient = {
  defaultModelSlot: new ConsoleApiAifoundationHaloRunV1alpha1DefaultModelSlotApi(
    undefined,
    '',
    axiosInstance,
  ),
  model: new ConsoleApiAifoundationHaloRunV1alpha1ModelApi(undefined, '', axiosInstance),
  provider: new ConsoleApiAifoundationHaloRunV1alpha1ProviderApi(undefined, '', axiosInstance),
  providerType: new ConsoleApiAifoundationHaloRunV1alpha1ProviderTypeApi(
    undefined,
    '',
    axiosInstance,
  ),
}

export { aiConsoleApiClient, aiCoreApiClient }
