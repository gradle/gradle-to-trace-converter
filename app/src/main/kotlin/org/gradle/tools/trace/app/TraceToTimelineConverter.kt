package org.gradle.tools.trace.app

import org.gradle.tools.trace.app.buildops.*
import org.gradle.tools.trace.app.util.composeCsvRow
import java.io.File

class TraceToTimelineConverter : BuildOperationVisitor {

    enum class NodeType { TASK, TRANSFORM }

    data class Node (
        val description: String,
        val type: NodeType,
        val inTypeId: Long,
        val workType: String,
        val buildPath: String,
        val projectPath: String,
        val startTime: Long,
        val duration: Long,
    )

    private val nodes = mutableListOf<Node>()

    fun convert(slice: BuildOperationTraceSlice, outputFile: File) {
        BuildOperationVisitor.visitRecords(slice, this)

        outputFile.writeText(header)
        nodes.forEach {
            outputFile.appendText("\n")
            outputFile.appendText(composeRow(it))
        }

        println("TIMELINE: Wrote ${nodes.size} nodes to ${outputFile.absolutePath}")
    }

    override fun visit(record: BuildOperationRecord): PostVisit {
        if (record.isExecuteTask()) {
            onExecuteTask(record)
        } else if (record.isExecuteScheduledTransformationStep()) {
            onExecuteTransform(record)
        }

        return {}
    }

    private fun onExecuteTask(record: BuildOperationRecord) {
        val executeTaskDetails = ExecuteTaskBuildOperationDetails.fromRecord(record)

        val node = Node(
            description = executeTaskDetails.taskPath,
            type = NodeType.TASK,
            inTypeId = executeTaskDetails.taskId,
            workType = executeTaskDetails.taskClass,
            buildPath = executeTaskDetails.buildPath,
            projectPath = executeTaskDetails.taskPath.substringBeforeLast(':'),
            startTime = record.startTime,
            duration = record.endTime - record.startTime,
        )

        nodes.add(node)
    }

    private fun onExecuteTransform(record: BuildOperationRecord) {
        val details = ExecuteScheduledTransformationStepBuildOperationDetails.fromRecord(record)

        val node = Node(
            description = createTransformationDescription(details),
            type = NodeType.TRANSFORM,
            inTypeId = details.transformationIdentity.transformationNodeId,
            workType = details.transformType,
            buildPath = details.transformationIdentity.buildPath,
            projectPath = details.transformationIdentity.projectPath,
            startTime = record.startTime,
            duration = record.endTime - record.startTime,
        )

        nodes.add(node)
    }

    private fun createTransformationDescription(details: ExecuteScheduledTransformationStepBuildOperationDetails): String {
        val identity = details.transformationIdentity
        return identity.targetVariant.componentId.toString() + compressAttributes(details)
    }

    private fun compressAttributes(details: ExecuteScheduledTransformationStepBuildOperationDetails): String {
        return buildString {
            for ((name, toValue) in details.toAttributes) {
                val fromValue = details.fromAttributes[name]
                if (toValue == fromValue) continue

                append(" ").append(name).append("(")
                if (fromValue == null) {
                    append(toValue)
                } else {
                    append(fromValue).append("->").append(toValue)
                }
                append(")")
            }
        }
    }

    companion object {
        private val header: String = listOf(
            "description",
            "type",
            "inTypeId",
            "workType",
            "buildPath",
            "projectPath",
            "startTime",
            "duration",
        ).joinToString(",")

        private fun composeRow(node: Node): String = composeCsvRow(
            node.description,
            node.type.toString(),
            node.inTypeId.toString(),
            node.workType,
            node.buildPath,
            node.projectPath,
            node.startTime.toString(),
            node.duration.toString(),
        )
    }
}
