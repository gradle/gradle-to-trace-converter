package org.gradle.tools.trace.app

import perfetto.protos.BuiltinClockOuterClass.BuiltinClock
import perfetto.protos.ClockSnapshotOuterClass.ClockSnapshot
import perfetto.protos.ClockSnapshotOuterClass.ClockSnapshot.Clock
import perfetto.protos.DebugAnnotationOuterClass.DebugAnnotation
import perfetto.protos.ProcessDescriptorOuterClass.ProcessDescriptor
import perfetto.protos.ThreadDescriptorOuterClass.ThreadDescriptor
import perfetto.protos.TracePacketOuterClass.TracePacket
import perfetto.protos.TrackDescriptorOuterClass.TrackDescriptor
import perfetto.protos.TrackEventOuterClass.TrackEvent
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference

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

        fun toDebugAnnotations(args: Map<String, Any?>?, name: String): DebugAnnotation {
            return DebugAnnotation.newBuilder()
                .setName(name)
                .addAllDictEntries(args?.entries?.map { e ->
                    @Suppress("UNCHECKED_CAST")
                    return@map if (e.value is Map<*, *>) toDebugAnnotations(e.value as Map<String, Any>, e.key)
                    else DebugAnnotation.newBuilder().setName(e.key).setStringValue(e.value.toString()).build()
                }.orEmpty())
                .build()
        }

        fun helper(record: BuildOperationRecord, lastTimestamp: AtomicReference<Long>) {
            val beginTime = record.startTime - lastTimestamp.get()
            lastTimestamp.set(record.startTime)

            val ctProcessId = (record.workerLeaseNumber ?: -1) + 1
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
                .setTimestampClockId(64)
                .setTimestamp(beginTime)
                .setTrustedPacketSequenceId(1)
                .setTrackEvent(TrackEvent.newBuilder()
                    .setTrackUuid(uuid)
                    .setName(record.displayName)
                    .addCategories(record.detailsClassName ?: "")
                    .addCategories(record.resultClassName ?: "")
                    .addDebugAnnotations(toDebugAnnotations(record.details, "details"))
                    .addDebugAnnotations(toDebugAnnotations(record.result, "result"))
                    .setType(TrackEvent.Type.TYPE_SLICE_BEGIN)
                )
                .build())

            record.children?.forEachIndexed { _, it ->
                helper(it, lastTimestamp)
            }

            val endTime = record.endTime - lastTimestamp.get()
            events.add(TracePacket.newBuilder()
                .setTimestampClockId(64)
                .setTimestamp(endTime)
                .setTrustedPacketSequenceId(1)
                .setTrackEvent(TrackEvent.newBuilder()
                    .setTrackUuid(uuid)
                    .setType(TrackEvent.Type.TYPE_SLICE_END)
                )
                .build()
            )
            lastTimestamp.set(record.endTime)
        }

        val firstBuildOp = input.first()
        val lastTimestamp = AtomicReference(firstBuildOp.startTime)
        events.add(TracePacket.newBuilder()
            .setTrustedPacketSequenceId(1)
            .setClockSnapshot(ClockSnapshot.newBuilder()
                // set our custom clock:
                .addClocks(Clock.newBuilder()
                    .setTimestamp(lastTimestamp.get())
                    .setUnitMultiplierNs(1000 * 1000) // unit is 'ms'
                    .setIsIncremental(true) // use delta timestamps
                    .setClockId(64) // first user-defined available clock ID
                )
                // synchronize our custom clock with the default, boot-time clock:
                .addClocks(Clock.newBuilder()
                    .setTimestamp(lastTimestamp.get())
                    .setClockId(BuiltinClock.BUILTIN_CLOCK_BOOTTIME_VALUE)
                )
            )
            .build()
        )


        for (it in input) {
            helper(it, lastTimestamp)
        }

        return events
    }

}
