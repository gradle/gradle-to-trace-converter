package org.gradle.tools.trace.app

import org.gradle.tools.trace.app.buildops.ExecuteScheduledTransformationStepBuildOperationDetails
import org.gradle.tools.trace.app.buildops.TransformationIdentity
import org.gradle.tools.trace.app.buildops.isExecuteScheduledTransformationStep
import org.gradle.tools.trace.app.util.attributesMapToString
import org.gradle.tools.trace.app.util.composeCsvRow
import java.io.File

/**
 * A unique identifier for a (single-artifact) transform that is determined by the `IdentifyStep`
 * and doubles as a workspace identifier for transform outputs.
 */
@JvmInline
value class UniqueTransformIdentity(val value: String)

class TraceToTransformCsvConverter : BuildOperationVisitor {

    data class TransformInfo(
        val transformationIdentity: TransformationIdentity?,

        val sourceAttributes: Map<String, String>,
        val fromAttributes: Map<String, String>,
        val toAttributes: Map<String, String>,
        val artifactName: String,
        val transformType: String,

        val uniqueTransformIdentity: UniqueTransformIdentity,
        var invocationCount: Int = 1,
        var executionCount: Int = 0,
        var executionTimeMillis: Long = 0,
    )

    // TODO: this mapping does not cover the case when the unique transform identity does not match the transformation identity
    //  This can happen if two consuming projects require the same transformed version of a producer project
    private val transformByIdentity = mutableMapOf<UniqueTransformIdentity, TransformInfo>()

    /* Visitor state */
    private var currentTransformationDetails: ExecuteScheduledTransformationStepBuildOperationDetails? = null

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
        val details = ExecuteScheduledTransformationStepBuildOperationDetails.fromRecord(record)
        currentTransformationDetails?.let {
            System.err.println("Unexpected transformation node execution: ${it.transformationIdentity}, when entering ${details.transformationIdentity}")
        }

        currentTransformationDetails = details
        return {
            currentTransformationDetails = null
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun onIdentifyTransform(record: BuildOperationRecord) {
        val result = record.result!! as Map<String, Map<String, String>>
        val transformIdentity = UniqueTransformIdentity(result["identity"]!!["uniqueId"]!!)

        val details = record.details!!
        val transformType = details["workType"] as String
        val componentId = details["componentId"] as String
        val fromAttributes = namedAttributesToMap(details["fromAttributes"] as List<Map<String, String>>)
        val toAttributes = namedAttributesToMap(details["toAttributes"] as List<Map<String, String>>)

        // Can be null for immediate transforms
        val currentTransformationDetails = currentTransformationDetails
        val currentTransformationIdentity = currentTransformationDetails?.transformationIdentity

        val prevInfo = transformByIdentity[transformIdentity]
        if (prevInfo != null) {
            if (prevInfo.transformationIdentity != currentTransformationIdentity) {
                System.err.println("\n" + """
                    WARNING: Transform $transformIdentity has multiple transformation identities:
                    - ${prevInfo.transformationIdentity}
                    - $currentTransformationIdentity
                """.trimIndent())
            }
            prevInfo.invocationCount++
        } else {
            transformByIdentity[transformIdentity] = TransformInfo(
                currentTransformationIdentity,
                sourceAttributes = currentTransformationDetails?.sourceAttributes ?: emptyMap(),
                fromAttributes = currentTransformationDetails?.fromAttributes ?: fromAttributes,
                toAttributes = currentTransformationDetails?.toAttributes ?: toAttributes,
                artifactName = currentTransformationIdentity?.artifactName ?: "",
                transformType = transformType,
                uniqueTransformIdentity = transformIdentity,
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

        val uniqueTransformIdentity = UniqueTransformIdentity((details["identity"] as Map<String, *>)["uniqueId"]!! as String)
        val executionTimeMillis = record.endTime - record.startTime
        val transformInfo = transformByIdentity[uniqueTransformIdentity] ?: run {
            System.err.println("No transform info for identity $uniqueTransformIdentity")
            return
        }

        if (transformInfo.executionCount != 0) {
            System.err.println("WARNING: Transform ${transformInfo.transformType} ($uniqueTransformIdentity) executed multiple times")
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
            "targetAttributes",
            "capabilities",
            "artifactName",
            "transformationNodeId",

            "sourceAttributes",
            "fromAttributes",
            "toAttributes",
            "transformType",
            "uniqueTransformIdentity",

            "invocationCount",
            "executionCount",
            "executionTimeMillis"
        ).joinToString(",")

        private fun composeRow(transformInfo: TransformInfo): String = composeCsvRow(
            transformInfo.transformationIdentity?.buildPath,
            transformInfo.transformationIdentity?.projectPath,
            transformInfo.transformationIdentity?.targetVariant?.componentId?.toString(),
            transformInfo.transformationIdentity?.targetVariant?.attributes?.attributesMapToString(),
            transformInfo.transformationIdentity?.targetVariant?.capabilities?.joinToString(),
            transformInfo.transformationIdentity?.artifactName,
            transformInfo.transformationIdentity?.transformationNodeId?.toString(),

            transformInfo.sourceAttributes.attributesMapToString(),
            transformInfo.fromAttributes.attributesMapToString(),
            transformInfo.toAttributes.attributesMapToString(),
            transformInfo.transformType,
            transformInfo.uniqueTransformIdentity.value,

            transformInfo.invocationCount.toString(),
            transformInfo.executionCount.toString(),
            transformInfo.executionTimeMillis.toString(),
        )
    }

}
