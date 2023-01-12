package org.gradle.tools.trace.app

class BuildOperationRecord(
    val id: Long,
    val parentId: Long,
    val displayName: String,
    val startTime: Long,
    val endTime: Long,
    details: Map<String, *>?,
    val detailsClassName: String?,
    result: Map<String, *>?,
    val resultClassName: String?,
    val failure: String,
    val progress: List<Progress>?,
    val children: List<BuildOperationRecord>?,
    // Experimental fields added for spiking:
    val workerLeaseNumber: Int?,
    val threadDescription: String?,
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

class BuildOperationTraceSlice(
    val records: List<BuildOperationRecord>,
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
            fun helper(record: BuildOperationRecord) {
                val postVisit = visitor.visit(record)
                record.children?.forEach(::helper)
                postVisit()
            }

            for (record in traversal.records) {
                helper(record)
            }
        }
    }
}
