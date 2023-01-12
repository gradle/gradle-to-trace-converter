package org.gradle.tools.trace.app

class BuildOperationRecord(
    val id: Long,
    val displayName: String,
    val startTime: Long,
    val endTime: Long,
    details: Map<String, *>? = null,
    val detailsClassName: String? = null,
    result: Map<String, *>? = null,
    val resultClassName: String? = null,
    val failure: String? = null,
    val progress: List<Progress>? = null,
    val children: List<BuildOperationRecord>? = null,
    // Experimental fields added for spiking:
    val workerLeaseNumber: Int? = null,
    val threadDescription: String? = null,
) {
    val details: Map<String, Any?>? = details?.toMap()
    val result: Map<String, Any?>? = result?.toMap()

    override fun toString(): String {
        return "BuildOperationRecord{$id->$displayName}"
    }

    class Progress(
        val time: Long,
        details: Map<String, Any?>?,
        val detailsClassName: String?
    ) {
        val details: Map<String, Any?>? = details?.toMap()

        override fun toString(): String {
            return "Progress{details=$details, detailsClassName='$detailsClassName'}"
        }
    }
}

data class BuildOperationTraceSlice(
    val records: List<BuildOperationRecord>,
    val include: Regex? = null,
    val exclude: Regex? = null,
)

typealias PostVisit = () -> Unit

interface BuildOperationVisitor {

    /**
     * Visits the current build operation record.
     *
     * Returns a post-visit callback that will be called after all children have been visited.
     */
    fun visit(record: BuildOperationRecord): PostVisit

    companion object {
        fun visitRecords(traversal: BuildOperationTraceSlice, visitor: BuildOperationVisitor) {
            val include = traversal.include
            val exclude = traversal.exclude
            fun helper(record: BuildOperationRecord, parentIncluded: Boolean) {
                val displayName = record.displayName

                val included = parentIncluded || include == null || displayName.matches(include)

                if (included && exclude != null && displayName.matches(exclude)) {
                    return
                }

                val postVisit = if (included) visitor.visit(record) else null
                record.children?.forEach { helper(it, included) }
                postVisit?.invoke()
            }

            val initialIncluded = include == null
            for (record in traversal.records) {
                helper(record, initialIncluded)
            }
        }
    }
}
