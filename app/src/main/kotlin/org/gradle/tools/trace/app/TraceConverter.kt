package org.gradle.tools.trace.app

import perfetto.protos.DebugAnnotationOuterClass.DebugAnnotation
import perfetto.protos.ProcessDescriptorOuterClass.ProcessDescriptor
import perfetto.protos.ThreadDescriptorOuterClass.ThreadDescriptor
import perfetto.protos.TracePacketOuterClass.TracePacket
import perfetto.protos.TrackDescriptorOuterClass.TrackDescriptor
import perfetto.protos.TrackEventOuterClass.TrackEvent
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong

class TraceConverter {

    fun convert(input: List<BuildOperationRecord>): List<TracePacket> {
        val events = mutableListOf<TracePacket>()
        val uuidCounter = AtomicLong(1)
        val knownPidTid = mutableMapOf<Int, MutableMap<Int, Long>>()

        val bopThreadToId = mutableMapOf<String, Int>()

        fun getThreadId(bopThreadName: String): Int {
            return bopThreadToId.getOrPut(bopThreadName) {
                bopThreadToId.size + 1
            }
        }

        fun toChromeTraceTime(time: Long) = TimeUnit.MILLISECONDS.toNanos(time)

        fun helper(record: BuildOperationRecord) {
            val beginTime = toChromeTraceTime(record.startTime)
            val endTime = toChromeTraceTime(record.endTime)

            if (endTime < beginTime) {
                System.err.println("Invalid time interval: $record")
            }

            val ctProcessId = record.workerLeaseNumber!! + 1
            val ctThreadId = getThreadId(record.threadDescription ?: "")
            if (!knownPidTid.containsKey(ctProcessId)) {
                events.add(TracePacket.newBuilder()
                    .setTrustedPacketSequenceId(1)
                    .setTrackDescriptor(TrackDescriptor.newBuilder()
                        .setUuid(0) // irrelevant, but needed
                        .setProcess(ProcessDescriptor.newBuilder()
                            .setPid(ctProcessId)
                            .setProcessName("Worker Lease $ctProcessId")
                        )
                    )
                    .build()
                )
                knownPidTid[ctProcessId] = mutableMapOf()
            }
            val uuid: Long
            if (!(knownPidTid[ctProcessId]!!.containsKey(ctThreadId))) {
                uuid = uuidCounter.getAndIncrement()
                events.add(TracePacket.newBuilder()
                    .setTrustedPacketSequenceId(1)
                    .setTrackDescriptor(TrackDescriptor.newBuilder()
                        .setUuid(uuid)
                        .setThread(ThreadDescriptor.newBuilder()
                            .setPid(ctProcessId)
                            .setTid(ctThreadId)
                            .setThreadName(record.threadDescription)
                        )
                    )
                    .build()
                )
                knownPidTid[ctProcessId]!![ctThreadId] = uuid
            } else {
                uuid = knownPidTid[ctProcessId]!![ctThreadId]!!
            }

            events.add(TracePacket.newBuilder()
                .setTimestamp(beginTime)
                .setTrustedPacketSequenceId(1)
                .setTrackEvent(TrackEvent.newBuilder()
                    .setTrackUuid(uuid)
                    .setName(record.displayName)
                    .addCategories(record.detailsClassName ?: "")
                    .addCategories(record.resultClassName ?: "")
                    .setType(TrackEvent.Type.TYPE_SLICE_BEGIN)
                )
                .build())
            events.add(TracePacket.newBuilder()
                .setTimestamp(endTime)
                .setTrustedPacketSequenceId(1)
                .setTrackEvent(TrackEvent.newBuilder()
                    .setTrackUuid(uuid)
                    .setType(TrackEvent.Type.TYPE_SLICE_END)
                )
                .build())

            record.children?.forEachIndexed { _, it ->
                helper(it)
            }
        }

        for (it in input) {
            helper(it)
        }

        return events
    }

}
