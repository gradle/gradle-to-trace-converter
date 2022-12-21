package org.gradle.tools.trace.app

class TraceConverter {

    private val ignoredBuildOperations = setOf("Execute transform")

    fun convert(input: List<BuildOperationRecord>): List<TraceEvent> {
        val events = mutableListOf<TraceEvent>()

        fun helper(threadId: Long, record: BuildOperationRecord) {
            if (record.displayName.startsWith("Resolve mutations for")) {
                return
            }
            if (record.endTime - record.startTime < 3) {
                return
            }
            val newThreadId = (record.workerLeaseNumber ?: threadId).toLong()

            if (record.displayName !in ignoredBuildOperations) {
                events.add(TraceEvent(
                        name = record.displayName,
                        phaseType = "B",
                        timestamp = record.startTime * 1000,
                        threadId = newThreadId
                ))
            }

//            record.progress?.forEach { progress ->
//                events.add(TraceEvent(
//                        name = null,
//                        phaseType = "i",
//                        timestamp = progress.time * 1000,
//                        arguments = mapOf(
//                                "details" to progress.details, "detailsClassName" to progress.detailsClassName)
//                ))
//            }
            record.children?.forEach {
                helper(newThreadId, it)
            }

            if (record.displayName !in ignoredBuildOperations) {
                events.add(TraceEvent(
                        name = record.displayName,
                        phaseType = "E",
                        timestamp = record.endTime * 1000,
                        threadId = newThreadId
                ))
            }
        }

        for (it in input) {
            helper(0, it)
        }

        return events
    }

}