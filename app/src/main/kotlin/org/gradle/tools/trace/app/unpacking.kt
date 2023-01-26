package org.gradle.tools.trace.app


fun BuildOperationRecord.isExecuteTask() =
    detailsClassName == "org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails"

fun BuildOperationRecord.isExecuteScheduledTransformationStep() =
    detailsClassName == "org.gradle.api.internal.artifacts.transform.ExecuteScheduledTransformationStepBuildOperationDetails"

fun Map<String, String>.attributesMapToString() = entries
    .joinToString(",", prefix = "{", postfix = "}") { "${it.key}=${it.value}" }

