package org.gradle.tools.trace.app

import java.io.File

class TraceToTransformCsvConverter : BuildOperationVisitor {

    data class TransformInfo(
        val transformationIdentity: TransformationIdentity,
        val identity: String,
        var invocationCount: Int = 1,
        var executionCount: Int = 0,
        var executionTimeMillis: Long = 0,
    )

    private val transformByIdentity = mutableMapOf<String, TransformInfo>()
    private var currentTransformation: TransformationIdentity? = null

    fun convert(slice: BuildOperationTraceSlice, outputFile: File) {
        BuildOperationVisitor.visitRecords(slice, this)

        outputFile.writeText(header)
        transformByIdentity.values.forEach {
            outputFile.appendText("\n")
            outputFile.appendText(composeRow(it))
        }

        println("TRANSFORM SUMMARY: Wrote ${transformByIdentity.size} transforms to ${outputFile.absolutePath}")
    }

    override fun visit(record: BuildOperationRecord): PostVisit {
        if (record.isExecuteScheduledTransformationStep()) {
            return onTransformationNodeExecution(record)
        } else if (record.displayName == "Identifying work" &&
            record.detailsClassName == "org.gradle.api.internal.artifacts.transform.DefaultTransformerInvocationFactory\$AbstractTransformerExecution\$DefaultIdentifyTransformBuildOperationDetails"
        ) {
            onIdentifyTransform(record)
        } else if (record.displayName.startsWith("Executing ") &&
            record.detailsClassName == "org.gradle.internal.execution.steps.ExecuteStep\$1\$1"
        ) {
            onExecuteTransform(record)
        }

        return {}
    }

    private fun onTransformationNodeExecution(record: BuildOperationRecord): PostVisit {
        val identity = TransformationIdentity.fromRecord(record)
        if (currentTransformation != null) {
            System.err.println("Unexpected transformation node execution: $currentTransformation, when entering $identity")
        }

        currentTransformation = identity
        return {
            currentTransformation = null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun onIdentifyTransform(record: BuildOperationRecord) {
        val result = record.result!! as Map<String, Map<String, String>>
        val transformIdentity = result["identity"]!!["uniqueId"]!!

        val details = record.details!!
        val transformType = details["workType"] as String
        val componentId = details["componentId"] as String
        val fromAttributes = namedAttributesToMap(details["fromAttributes"] as List<Map<String, String>>)
        val toAttributes = namedAttributesToMap(details["toAttributes"] as List<Map<String, String>>)

        val transformationIdentity = currentTransformation
            ?: TransformationIdentity(
                // this happens for immediate transforms
                buildPath = "",
                projectPath = "",
                componentId = componentId,
                sourceAttributes = emptyMap(),
                transformType = transformType,
                fromAttributes = fromAttributes.toSortedMap(),
                toAttributes = toAttributes.toSortedMap(),
                transformationNodeId = -1,
            )

        val prevInfo = transformByIdentity[transformIdentity]
        if (prevInfo != null) {
            if (prevInfo.transformationIdentity != transformationIdentity) {
                System.err.println("Unexpected identify transform (different transformation): $transformIdentity, $transformType, $componentId, $fromAttributes, $toAttributes")
            }
            prevInfo.invocationCount++
        } else {
            transformByIdentity[transformIdentity] = TransformInfo(
                transformationIdentity,
                transformIdentity,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun onExecuteTransform(record: BuildOperationRecord) {
        val details = record.details!!
        val workType = details["workType"] as String
        if (workType !in executeTransformWorkTypes) {
            return
        }

        val identity = (details["identity"] as Map<String, *>)["uniqueId"]!! as String
        val executionTimeMillis = record.endTime - record.startTime
        val transformInfo = transformByIdentity[identity] ?: run {
            System.err.println("No transform info for identity $identity")
            return
        }

        if (transformInfo.executionCount != 0) {
            System.err.println("WARNING: Transform ${transformInfo.transformationIdentity.transformType} ($identity) executed multiple times")
        }

        transformInfo.executionTimeMillis += executionTimeMillis
        transformInfo.executionCount++
    }

    private fun namedAttributesToMap(rawAttributes: List<Map<String, String>>): Map<String, String> {
        val result = mutableMapOf<String, String>()
        rawAttributes.forEach {
            result[it["name"]!!] = it["value"]!!
        }
        return result
    }

    companion object {

        private val executeTransformWorkTypes = setOf(
            "org.gradle.api.internal.artifacts.transform.DefaultTransformerInvocationFactory\$ImmutableTransformerExecution",
            "org.gradle.api.internal.artifacts.transform.DefaultTransformerInvocationFactory\$MutableTransformerExecution",
        )

        private val header: String = listOf(
            "buildPath",
            "projectPath",
            "componentId",
            "sourceAttributes",
            "transformType",
            "fromAttributes",
            "toAttributes",
            "transformationNodeId",
            "identity",
            "invocationCount",
            "executionCount",
            "executionTimeMillis"
        ).joinToString(",")

        private fun composeRow(transformInfo: TransformInfo): String = composeCsvRow(
            transformInfo.transformationIdentity.buildPath,
            transformInfo.transformationIdentity.projectPath,
            transformInfo.transformationIdentity.componentId,
            transformInfo.transformationIdentity.sourceAttributes.attributesMapToString(),
            transformInfo.transformationIdentity.transformType,
            transformInfo.transformationIdentity.fromAttributes.attributesMapToString(),
            transformInfo.transformationIdentity.toAttributes.attributesMapToString(),
            transformInfo.transformationIdentity.transformationNodeId.toString(),
            transformInfo.identity,
            transformInfo.invocationCount.toString(),
            transformInfo.executionCount.toString(),
            transformInfo.executionTimeMillis.toString(),
        )
    }

}
