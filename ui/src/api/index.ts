import { axiosInstance } from '@halo-dev/api-client'
import {
  AiModelV1alpha1Api,
  AiProviderV1alpha1Api,
  ConsoleApiAifoundationHaloRunV1alpha1ModelApi,
  ConsoleApiAifoundationHaloRunV1alpha1ProviderApi,
  ConsoleApiAifoundationHaloRunV1alpha1ProviderDebugApi,
} from './generated'

const aiCoreApiClient = {
  model: new AiModelV1alpha1Api(undefined, '', axiosInstance),
  provider: new AiProviderV1alpha1Api(undefined, '', axiosInstance),
}

const aiConsoleApiClient = {
  model: new ConsoleApiAifoundationHaloRunV1alpha1ModelApi(undefined, '', axiosInstance),
  provider: new ConsoleApiAifoundationHaloRunV1alpha1ProviderApi(undefined, '', axiosInstance),
  debug: new ConsoleApiAifoundationHaloRunV1alpha1ProviderDebugApi(undefined, '', axiosInstance),
}

export { aiConsoleApiClient, aiCoreApiClient }
