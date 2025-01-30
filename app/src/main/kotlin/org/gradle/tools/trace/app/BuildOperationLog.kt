package org.gradle.tools.trace.app

import java.util.stream.Stream

typealias BuildOperationId = Long

sealed interface BuildOperationLog {
    val id: Long
}

class BuildOperationStart(
    override val id: Long,
    val displayName: String,
    val startTime: Long,
    details: Map<String, *>? = null,
    val detailsClassName: String? = null,
    val parentId: Long? = null
) : BuildOperationLog {
    val details: Map<String, Any?>? = details?.toMap()

    override fun toString(): String {
        return "BuildOperationStart{$id->$displayName}"
    }
}

class BuildOperationFinish(
    override val id: Long,
    val endTime: Long,
    result: Map<String, *>? = null,
    val resultClassName: String? = null,
    val failure: String? = null,
) : BuildOperationLog {
    val result: Map<String, Any?>? = result?.toMap()

    override fun toString(): String {
        return "BuildOperationFinish{$id}"
    }
}

class BuildOperationProgress(
    override val id: Long,
    val time: Long,
    details: Map<String, Any?>?,
    val detailsClassName: String?
) : BuildOperationLog {
    val details: Map<String, Any?>? = details?.toMap()

    override fun toString(): String {
        return "Progress{details=$details, detailsClassName='$detailsClassName'}"
    }
}

data class BuildOperationLogs(
    val logs: Stream<BuildOperationLog>,
    val include: Regex? = null,
    val exclude: Regex? = null,
) : AutoCloseable {
    override fun close() {
        logs.close()
    }
}

typealias PostVisit = (BuildOperationStart, BuildOperationFinish) -> Unit

interface BuildOperationVisitor {

    /**
     * Visits the current build operation record.
     *
     * Returns a post-visit callback that will be called after all children have been visited.
     */
    fun visit(start: BuildOperationStart): PostVisit

    fun visit(progress: BuildOperationProgress) {}

    companion object {

        fun visitLogs(traversal: BuildOperationLogs, visitor: BuildOperationVisitor) {
            val include = traversal.include
            val exclude = traversal.exclude
            val openBuildOperations = mutableMapOf<Long, Pair<BuildOperationStart, PostVisit>>()
            var count = 0
            fun helper(log: BuildOperationLog) {
                when (log) {
                    is BuildOperationStart -> {
                        val displayName = log.displayName

                        val included = include == null ||
                            displayName.matches(include) || openBuildOperations.contains(log.parentId)
                        if (!included || (exclude != null && displayName.matches(exclude))) {
                            return
                        }
                        openBuildOperations[log.id] = log to visitor.visit(log)
                    }

                    is BuildOperationFinish -> {
                        val openBuildOp = openBuildOperations.remove(log.id)
                        openBuildOp?.let { (start, postVisit) ->
                            postVisit.invoke(start, log)
                        }
                    }

                    is BuildOperationProgress -> visitor.visit(log)
                }
            }

            for (record in traversal.logs) {
                count++
                helper(record)
            }
            println("Processed $count build operation logs")
        }
    }
}
