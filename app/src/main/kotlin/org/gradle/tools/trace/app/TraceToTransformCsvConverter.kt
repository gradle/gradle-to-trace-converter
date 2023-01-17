package org.gradle.tools.trace.app

import java.io.File

class TraceToTransformCsvConverter : BuildOperationVisitor {

    data class TransformInfo(
        val identity: String,
        val type: String,
        val componentId: String,
        val fromAttributes: String,
        val toAttribute: String,
        var invocationCount: Int = 1,
        var executionTimeMillis: Long = -1,
    )

    private val executeTransformWorkTypes = setOf(
        "org.gradle.api.internal.artifacts.transform.DefaultTransformerInvocationFactory\$ImmutableTransformerExecution",
        "org.gradle.api.internal.artifacts.transform.DefaultTransformerInvocationFactory\$MutableTransformerExecution",
    )

    private val transformByIdentity = mutableMapOf<String, TransformInfo>()

    fun convert(slice: BuildOperationTraceSlice, outputFile: File) {
        BuildOperationVisitor.visitRecords(slice, this)

        outputFile.writeText("identity,type,componentId,fromAttributes,toAttributes,invocationCount,executionTimeMillis")
        transformByIdentity.values.forEach { transform ->
            outputFile.appendText("\n${transform.identity},${transform.type},${transform.componentId},\"${transform.fromAttributes}\",\"${transform.toAttribute}\",${transform.invocationCount},${transform.executionTimeMillis}")
        }

        println("Wrote ${transformByIdentity.size} transforms to ${outputFile.absolutePath}")
    }

    override fun visit(record: BuildOperationRecord): PostVisit {
        if (record.displayName == "Identifying work" &&
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

    @Suppress("UNCHECKED_CAST")
    private fun onIdentifyTransform(record: BuildOperationRecord) {
        val result = record.result!! as Map<String, Map<String, String>>
        val transformIdentity = result["identity"]!!["uniqueId"]!!

        val details = record.details!!
        val transformType = details["workType"] as String
        val componentId = details["componentId"] as String
        val fromAttributes = attributesToString(details["fromAttributes"] as List<Map<String, String>>)
        val toAttributes = attributesToString(details["toAttributes"] as List<Map<String, String>>)

        val prevInfo = transformByIdentity[transformIdentity]
        if (prevInfo != null) {
            prevInfo.invocationCount++
        } else {
            transformByIdentity[transformIdentity] = TransformInfo(
                transformIdentity,
                transformType,
                componentId,
                fromAttributes,
                toAttributes
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

        if (transformInfo.executionTimeMillis == -1L) {
            transformInfo.executionTimeMillis = executionTimeMillis
        } else {
            System.err.println("WARNING: Transform ${transformInfo.type} ($identity) executed multiple times")
        }
    }

    private fun attributesToString(rawAttributes: List<Map<String, String>>) =
        rawAttributes
            .sortedBy { it["name"] }
            .joinToString(",", prefix = "{", postfix = "}") { "${it["name"]}=${it["value"]}" }

}
