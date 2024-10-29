package org.gradle.tools.trace.app.buildops

import org.gradle.tools.trace.app.BuildOperationStart

fun BuildOperationStart.isExecuteTask() =
    detailsClassName == "org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails"

fun BuildOperationStart.isExecuteScheduledTransformationStep() =
    detailsClassName == "org.gradle.api.internal.artifacts.transform.ExecuteScheduledTransformationStepBuildOperationDetails"
