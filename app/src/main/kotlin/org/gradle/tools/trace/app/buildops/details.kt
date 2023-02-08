package org.gradle.tools.trace.app.buildops

import org.gradle.tools.trace.app.BuildOperationRecord

fun BuildOperationRecord.isExecuteTask() =
    detailsClassName == "org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails"

fun BuildOperationRecord.isExecuteScheduledTransformationStep() =
    detailsClassName == "org.gradle.api.internal.artifacts.transform.ExecuteScheduledTransformationStepBuildOperationDetails"
