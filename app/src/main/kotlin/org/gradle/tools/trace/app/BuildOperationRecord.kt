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
