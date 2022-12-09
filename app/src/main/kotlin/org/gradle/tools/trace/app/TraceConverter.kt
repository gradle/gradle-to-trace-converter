package org.gradle.tools.trace.app

class TraceConverter {

    fun convert(input: List<BuildOperationRecord>): List<TraceEvent> {
        val events = mutableListOf<TraceEvent>()

        fun helper(record: BuildOperationRecord) {
            events.add(TraceEvent(
                    name = record.displayName,
                    phaseType = "B",
                    timestamp = record.startTime * 1000
            ))

            record.progress?.forEach { progress ->
                events.add(TraceEvent(
                        name = null,
                        phaseType = "i",
                        timestamp = progress.time * 1000,
                        arguments = mapOf(
                                "details" to progress.details, "detailsClassName" to progress.detailsClassName)
                ))
            }
            record.children?.forEach { helper(it) }
            events.add(TraceEvent(
                    name = record.displayName,
                    phaseType = "E",
                    timestamp = record.endTime * 1000
            ))
        }

        input.forEach(::helper)

        return events
    }

}