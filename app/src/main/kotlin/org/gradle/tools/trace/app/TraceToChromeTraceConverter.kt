package org.gradle.tools.trace.app

import perfetto.protos.BuiltinClockOuterClass.BuiltinClock
import perfetto.protos.ClockSnapshotOuterClass.ClockSnapshot
import perfetto.protos.ClockSnapshotOuterClass.ClockSnapshot.Clock
import perfetto.protos.DebugAnnotationOuterClass.DebugAnnotation
import perfetto.protos.ProcessDescriptorOuterClass.ProcessDescriptor
import perfetto.protos.ThreadDescriptorOuterClass.ThreadDescriptor
import perfetto.protos.TraceOuterClass
import perfetto.protos.TracePacketOuterClass.TracePacket
import perfetto.protos.TrackDescriptorOuterClass.TrackDescriptor
import perfetto.protos.TrackEventOuterClass.TrackEvent
import java.io.File
import java.util.concurrent.atomic.AtomicLong

class TraceToChromeTraceConverter : BuildOperationVisitor {

    private val events = mutableListOf<TracePacket>()
    private val uuidCounter = AtomicLong(1)
    private val knownPidTid = mutableMapOf<Int, MutableMap<Int, Long>>()
    private val startTime = AtomicLong(0)

    private val bopThreadToId = mutableMapOf<String, Int>()

    fun convert(slice: BuildOperationTraceSlice, outputFile: File) {
        if (slice.records.isNotEmpty()) {
            BuildOperationVisitor.visitRecords(slice, this)
        }

        val trace = TraceOuterClass.Trace.newBuilder()
            .addAllPacket(events)
            .build()

        outputFile.writeBytes(trace.toByteArray())
        println("CHROME TRACE: Wrote ${events.size} events to ${outputFile.absolutePath}")
    }

    override fun visit(record: BuildOperationRecord): PostVisit {
        if (events.isEmpty()) {
            onFirstRecord(record)
        }

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
                        .setThreadName(record.threadDescription ?: "thread($ctThreadId)")
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
            .setTimestamp(record.startTime - startTime.get())
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

        // Children are visited automatically if present and not filtered out

        return {
            events.add(TracePacket.newBuilder()
                .setTimestampClockId(64)
                .setTimestamp(record.endTime - startTime.get())
                .setTrustedPacketSequenceId(1)
                .setTrackEvent(TrackEvent.newBuilder()
                    .setTrackUuid(uuid)
                    .setType(TrackEvent.Type.TYPE_SLICE_END)
                )
                .build()
            )
        }
    }

    private fun onFirstRecord(record: BuildOperationRecord) {
        startTime.set(record.startTime)

        events.add(TracePacket.newBuilder()
            .setTrustedPacketSequenceId(1)
            .setClockSnapshot(ClockSnapshot.newBuilder()
                // set our custom clock
                // - let the 0 of our clock be the startTime on the global boot-time clock
                // - use 'ms' unit since that's anyway the precision we get by the build operation infrastructure
                .addClocks(Clock.newBuilder()
                    .setTimestamp(0)
                    .setUnitMultiplierNs(1000 * 1000) // unit is 'ms'
                    .setClockId(64) // first user-defined available clock ID
                )
                .addClocks(Clock.newBuilder()
                    .setTimestamp(startTime.get())
                    .setClockId(BuiltinClock.BUILTIN_CLOCK_BOOTTIME_VALUE)
                )
            )
            .build()
        )
    }

    private fun getThreadId(bopThreadName: String): Int {
        return bopThreadToId.getOrPut(bopThreadName) {
            bopThreadToId.size + 1
        }
    }

    private fun toDebugAnnotations(args: Map<String, Any?>?, name: String): DebugAnnotation {
        return DebugAnnotation.newBuilder()
            .setName(name)
            .addAllDictEntries(args?.entries?.map { e ->
                @Suppress("UNCHECKED_CAST")
                return@map if (e.value is Map<*, *>) toDebugAnnotations(e.value as Map<String, Any>, e.key)
                else DebugAnnotation.newBuilder().setName(e.key).setStringValue(e.value.toString()).build()
            }.orEmpty())
            .build()
    }
}
