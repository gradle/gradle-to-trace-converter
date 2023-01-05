package org.gradle.tools.trace.app

class TraceConverter {

    fun convert(input: List<BuildOperationRecord>): List<TraceEvent> {
        val events = mutableListOf<TraceEvent>()

        val bopThreadToId = mutableMapOf<String, Long>()

        fun getThreadId(bopThreadName: String): Long {
            return bopThreadToId.getOrPut(bopThreadName) {
                bopThreadToId.size.toLong() + 1
            }
        }

        fun toChromeTraceTime(time: Long) = time * 1000

        fun helper(ctParentProcessId: Long, record: BuildOperationRecord) {
            val beginTime = toChromeTraceTime(record.startTime)
            val endTime = toChromeTraceTime(record.endTime)

            if (endTime < beginTime) {
                System.err.println("Invalid time interval: $record")
            }

            val ctProcessId = record.workerLeaseNumber?.let { it.toLong() + 1 } ?: ctParentProcessId
            val ctThreadId = getThreadId(record.threadDescription ?: "")
            events.add(TraceEvent(
                name = "thread_name",
                phaseType = "M",
                processId = ctProcessId,
                threadId = ctThreadId,
                timestamp = beginTime,
                arguments = buildMap {
                    put("name", "Thread " + record.threadDescription)
                }
            ))
            events.add(TraceEvent(
                name = "process_name",
                phaseType = "M",
                processId = ctProcessId,
                threadId = ctThreadId,
                timestamp = beginTime,
                arguments = buildMap {
                    put("name", "Worker Lease ") // the pid is appended to the name anyway, which will read e.g. "Worker Lease 2"
                }
            ))
            events.add(TraceEvent(
                name = record.displayName,
                phaseType = "X",
                timestamp = beginTime,
                duration = endTime - beginTime,
                processId = ctProcessId,
                threadId = ctThreadId,
                arguments = buildMap {
                    if (!record.threadDescription.isNullOrBlank()) put("thread", record.threadDescription)
                    if (!record.details.isNullOrEmpty()) put("details", record.details)
                    if (!record.result.isNullOrEmpty()) put("result", record.result)
                }
            ))

//            record.progress?.forEach { progress ->
//                events.add(TraceEvent(
//                    name = null,
//                    phaseType = "i",
//                    timestamp = toChromeTraceTime(progress.time),
//                    arguments = mapOf("details" to progress.details, "detailsClassName" to progress.detailsClassName)
//                ))
//            }

            record.children?.forEachIndexed { _, it ->
                helper(ctProcessId, it)
            }
        }

        for (it in input) {
            helper(0, it)
        }

        return events
    }

}
