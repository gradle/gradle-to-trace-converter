package org.gradle.tools.trace.app

class TraceConverter {

    fun convert(input: List<BuildOperationRecord>): List<TraceEvent> {
        val events = mutableListOf<TraceEvent>()

        fun helper(threadId: Long, record: BuildOperationRecord) {
            if (record.displayName.startsWith("Resolve mutations for")) {
                return
            }
            if (record.endTime - record.startTime < 3) {
                return
            }

            events.add(TraceEvent(
                    name = record.displayName,
                    phaseType = "B",
                    timestamp = record.startTime * 1000,
                    threadId = threadId,
            ))

//            record.progress?.forEach { progress ->
//                events.add(TraceEvent(
//                        name = null,
//                        phaseType = "i",
//                        timestamp = progress.time * 1000,
//                        arguments = mapOf(
//                                "details" to progress.details, "detailsClassName" to progress.detailsClassName)
//                ))
//            }
            val isNewThread = record.displayName == "Run tasks"
            record.children?.forEachIndexed { index, it ->
                val tid = if (!isNewThread) threadId else threadId + index + 1
                helper(tid, it)
            }

            events.add(TraceEvent(
                    name = record.displayName,
                    phaseType = "E",
                    timestamp = record.endTime * 1000,
                    threadId = threadId,
            ))
        }

        for (it in input) {
            helper(1, it)
        }

        return events
    }

}