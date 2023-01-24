package org.gradle.tools.trace.app


fun BuildOperationRecord.isExecuteTask() =
    detailsClassName == "org.gradle.api.internal.tasks.execution.ExecuteTaskBuildOperationDetails"

fun BuildOperationRecord.isExecuteScheduledTransformationStep() =
    detailsClassName == "org.gradle.api.internal.artifacts.transform.ExecuteScheduledTransformationStepBuildOperationDetails"


data class ExecuteTaskBuildOperationDetails(
    val buildPath: String,
    val taskPath: String,
    val taskId: Long,
    val taskClass: String,
) {

    companion object {

        @Suppress("UNCHECKED_CAST")
        fun fromRecord(record: BuildOperationRecord): ExecuteTaskBuildOperationDetails {
            val details = record.details as Map<String, *>

            return ExecuteTaskBuildOperationDetails(
                buildPath = details["buildPath"] as String,
                taskPath = details["taskPath"] as String,
                taskId = details["taskId"].toString().toDouble().toLong(),
                taskClass = details["taskClass"] as String,
            )
        }
    }
}

data class TransformationIdentity(
    val buildPath: String,
    val projectPath: String,
    val componentId: String,
    val sourceAttributes: Map<String, String>,
    val transformType: String,
    val fromAttributes: Map<String, String>,
    val toAttributes: Map<String, String>,
    val transformationNodeId: Long,
) {

    companion object {

        @Suppress("UNCHECKED_CAST")
        fun fromRecord(record: BuildOperationRecord): TransformationIdentity {
            val identityData = (record.details as Map<String, *>)["transformationIdentity"] as Map<String, *>

            return TransformationIdentity(
                buildPath = identityData["buildPath"] as String,
                projectPath = identityData["projectPath"] as String,
                componentId = identityData["componentId"] as String,
                sourceAttributes = (identityData["sourceAttributes"] as Map<String, String>).toSortedMap(),
                transformType = identityData["transformType"] as String,
                fromAttributes = (identityData["fromAttributes"] as Map<String, String>).toSortedMap(),
                toAttributes = (identityData["toAttributes"] as Map<String, String>).toSortedMap(),
                transformationNodeId = identityData["transformationNodeId"].toString().toDouble().toLong(),
            )
        }
    }
}

fun Map<String, String>.attributesMapToString() = entries
    .joinToString(",", prefix = "{", postfix = "}") { "${it.key}=${it.value}" }

