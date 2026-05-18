package com.archivesentinel.domain

enum class ExecutionMode { HOST_NATIVE, DOCKER }
enum class TdarrMode { EXISTING, MANAGED }
enum class DeletionMode { MANUAL, AUTOMATIC }
enum class OutputMode { PARALLEL, SAME_LIBRARY }
enum class TargetType { FOLDER, FILE }
enum class MediaStatus {
    DISCOVERED,
    ANALYZED,
    QUEUED,
    TRANSCODING,
    VALIDATING,
    STAGED_CANDIDATE,
    PROMOTED,
    ARCHIVED,
    AWAITING_DELETION_APPROVAL,
    DELETED,
    RESTORED,
    CANCELLED,
    FAILED
}
enum class RunStatus { QUEUED, RUNNING, COMPLETED, COMPLETED_WITH_FAILURES, CANCELLED, FAILED }
enum class PrecheckStatus { RUNNING, COMPLETED, FAILED }
