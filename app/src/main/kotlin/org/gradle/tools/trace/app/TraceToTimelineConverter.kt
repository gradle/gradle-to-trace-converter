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

    fun convert(logs: BuildOperationLogs, outputFile: File) {
        BuildOperationVisitor.visitLogs(logs, this)

        outputFile.writeText(header)
        nodes.forEach {
            outputFile.appendText("\n")
            outputFile.appendText(composeRow(it))
        }

        println("TIMELINE: Wrote ${nodes.size} nodes to ${outputFile.absolutePath}")
    }

    override fun visit(start: BuildOperationStart): PostVisit {
        if (start.isExecuteTask()) {
            return onExecuteTask(start)
        } else if (start.isExecuteScheduledTransformationStep()) {
            return onExecuteTransform(start)
        }

        return { _, _ -> }
    }

    private fun onExecuteTask(start: BuildOperationStart): PostVisit {
        val executeTaskDetails = ExecuteTaskBuildOperationDetails.fromStart(start)

        return { _, finish ->
            val node = Node(
                description = executeTaskDetails.taskPath,
                type = NodeType.TASK,
                inTypeId = executeTaskDetails.taskId,
                workType = executeTaskDetails.taskClass,
                buildPath = executeTaskDetails.buildPath,
                projectPath = executeTaskDetails.taskPath.substringBeforeLast(':'),
                startTime = start.startTime,
                duration = finish.endTime - start.startTime,
            )

            nodes.add(node)
        }
    }

    private fun onExecuteTransform(start: BuildOperationStart): PostVisit {
        val details = ExecuteScheduledTransformationStepBuildOperationDetails.fromRecord(start)

        return { _, finish ->
            val node = Node(
                description = createTransformationDescription(details),
                type = NodeType.TRANSFORM,
                inTypeId = details.transformationIdentity.transformationNodeId,
                workType = details.transformType,
                buildPath = details.transformationIdentity.buildPath,
                projectPath = details.transformationIdentity.projectPath,
                startTime = start.startTime,
                duration = finish.endTime - start.startTime,
            )

            nodes.add(node)
        }
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
