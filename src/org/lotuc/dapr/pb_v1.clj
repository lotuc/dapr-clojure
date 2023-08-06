(ns org.lotuc.dapr.pb-v1
  "https://github.com/dapr/dapr/tree/master/dapr/proto
  "
  (:require
   [org.lotuc.dapr.internal.proto-utils :as proto-utils]
   [potemkin]
   [pronto.core :as p])
  (:import
   (io.dapr.v1
    CommonProtos$HTTPExtension$Verb
    CommonProtos$StateOptions$StateConcurrency
    CommonProtos$StateOptions$StateConsistency)))

(potemkin/import-vars
 [pronto.core
  proto-map->bytes
  proto-map->clj-map
  proto-map->proto])

(proto-utils/import-protos
 mapper
 io.dapr.v1
 [CommonProtos$HTTPExtension
  CommonProtos$InvokeRequest
  CommonProtos$InvokeResponse
  CommonProtos$StreamPayload
  CommonProtos$StateItem
  CommonProtos$Etag
  CommonProtos$StateOptions
  CommonProtos$ConfigurationItem

  DaprProtos$InvokeServiceRequest
  DaprProtos$GetStateRequest
  DaprProtos$GetBulkStateRequest
  DaprProtos$GetBulkStateResponse
  DaprProtos$BulkStateItem
  DaprProtos$GetStateResponse
  DaprProtos$DeleteStateRequest
  DaprProtos$DeleteBulkStateRequest
  DaprProtos$SaveStateRequest
  DaprProtos$QueryStateRequest
  DaprProtos$QueryStateItem
  DaprProtos$QueryStateResponse
  DaprProtos$PublishEventRequest
  DaprProtos$BulkPublishRequest
  DaprProtos$BulkPublishRequestEntry
  DaprProtos$BulkPublishResponse
  DaprProtos$BulkPublishResponseFailedEntry
  DaprProtos$InvokeBindingRequest
  DaprProtos$InvokeBindingResponse
  DaprProtos$GetSecretRequest
  DaprProtos$GetSecretResponse
  DaprProtos$GetBulkSecretRequest
  DaprProtos$SecretResponse
  DaprProtos$GetBulkSecretResponse
  DaprProtos$TransactionalStateOperation
  DaprProtos$ExecuteStateTransactionRequest
  DaprProtos$RegisterActorTimerRequest
  DaprProtos$UnregisterActorTimerRequest
  DaprProtos$RegisterActorReminderRequest
  DaprProtos$UnregisterActorReminderRequest
  DaprProtos$RenameActorReminderRequest
  DaprProtos$GetActorStateRequest
  DaprProtos$GetActorStateResponse
  DaprProtos$ExecuteActorStateTransactionRequest
  DaprProtos$TransactionalActorStateOperation
  DaprProtos$InvokeActorRequest
  DaprProtos$InvokeActorResponse
  DaprProtos$GetMetadataResponse
  DaprProtos$ActiveActorsCount
  DaprProtos$RegisteredComponents
  DaprProtos$PubsubSubscription
  DaprProtos$PubsubSubscriptionRules
  DaprProtos$PubsubSubscriptionRule
  DaprProtos$SetMetadataRequest
  DaprProtos$GetConfigurationRequest
  DaprProtos$GetConfigurationResponse
  DaprProtos$SubscribeConfigurationRequest
  DaprProtos$UnsubscribeConfigurationRequest
  DaprProtos$SubscribeConfigurationResponse
  DaprProtos$UnsubscribeConfigurationResponse
  DaprProtos$TryLockRequest
  DaprProtos$TryLockResponse
  DaprProtos$UnlockRequest
  DaprProtos$UnlockResponse
  DaprProtos$SubtleGetKeyRequest
  DaprProtos$SubtleGetKeyResponse
  DaprProtos$SubtleEncryptRequest
  DaprProtos$SubtleEncryptResponse
  DaprProtos$SubtleDecryptRequest
  DaprProtos$SubtleDecryptResponse
  DaprProtos$SubtleWrapKeyRequest
  DaprProtos$SubtleWrapKeyResponse
  DaprProtos$SubtleUnwrapKeyRequest
  DaprProtos$SubtleUnwrapKeyResponse
  DaprProtos$SubtleSignRequest
  DaprProtos$SubtleSignResponse
  DaprProtos$SubtleVerifyRequest
  DaprProtos$SubtleVerifyResponse
  DaprProtos$EncryptRequest
  DaprProtos$EncryptRequestOptions
  DaprProtos$EncryptResponse
  DaprProtos$DecryptRequest
  DaprProtos$DecryptRequestOptions
  DaprProtos$DecryptResponse
  DaprProtos$GetWorkflowRequest
  DaprProtos$GetWorkflowResponse
  DaprProtos$StartWorkflowRequest
  DaprProtos$StartWorkflowResponse
  DaprProtos$TerminateWorkflowRequest
  DaprProtos$PauseWorkflowRequest
  DaprProtos$ResumeWorkflowRequest
  DaprProtos$RaiseEventWorkflowRequest
  DaprProtos$PurgeWorkflowRequest])

(def http-extension-verbs
  (proto-utils/enum-keywords CommonProtos$HTTPExtension$Verb))

(def state-options-concurrency
  (proto-utils/enum-keywords CommonProtos$StateOptions$StateConcurrency))

(def state-options-consistency
  (proto-utils/enum-keywords CommonProtos$StateOptions$StateConsistency))
