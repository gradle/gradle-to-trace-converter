package org.gradle.tools.trace.app

import com.google.protobuf.CodedOutputStream
import perfetto.protos.BuiltinClockOuterClass.BuiltinClock
import perfetto.protos.ClockSnapshotOuterClass.ClockSnapshot
import perfetto.protos.ClockSnapshotOuterClass.ClockSnapshot.Clock
import perfetto.protos.DebugAnnotationOuterClass.DebugAnnotation
import perfetto.protos.ProcessDescriptorOuterClass.ProcessDescriptor
import perfetto.protos.ThreadDescriptorOuterClass.ThreadDescriptor
import perfetto.protos.TracePacketOuterClass.TracePacket
import perfetto.protos.TrackDescriptorOuterClass.TrackDescriptor
import perfetto.protos.TrackEventOuterClass.TrackEvent
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStream
import java.nio.file.Files
import java.util.concurrent.atomic.AtomicLong

class TraceToChromeTraceConverter(val outputFile: File) : BuildOperationConverter {

    private var packetCount = 0
    private val uuidCounter = AtomicLong(1)
    private val knownPidTid = mutableMapOf<Int, MutableMap<Int, Long>>()
    private val startTime = AtomicLong(0)
    private val fileOutputStream: OutputStream = Files.newOutputStream(outputFile.toPath())
    private val codedStream = CodedOutputStream.newInstance(fileOutputStream)

    private val bopThreadToId = mutableMapOf<String, Int>()

    override fun write() {
        codedStream.flush()
        fileOutputStream.close()
        println("CHROME TRACE: Wrote $packetCount packets to ${outputFile.absolutePath}")
    }

    private fun writeTracePacket(packet: TracePacket) {
        codedStream.writeMessage(1, packet)
        packetCount++
    }

    override fun visit(start: BuildOperationStart): PostVisit {
        if (packetCount == 0) {
            onFirstRecord(start)
        }

        val ctProcessId = 0
        val ctThreadId = getThreadId("")
        if (!knownPidTid.containsKey(ctProcessId)) {
            writeTracePacket(TracePacket.newBuilder()
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
            writeTracePacket(TracePacket.newBuilder()
                .setTrustedPacketSequenceId(1)
                .setTrackDescriptor(TrackDescriptor.newBuilder()
                    .setUuid(uuid)
                    .setThread(ThreadDescriptor.newBuilder()
                        .setPid(ctProcessId)
                        .setTid(ctThreadId)
                        .setThreadName("thread($ctThreadId)")
                    )
                )
                .build()
            )
            knownPidTid[ctProcessId]!![ctThreadId] = uuid
        } else {
            uuid = knownPidTid[ctProcessId]!![ctThreadId]!!
        }

        writeTracePacket(TracePacket.newBuilder()
            .setTimestampClockId(64)
            .setTimestamp(start.startTime - startTime.get())
            .setTrustedPacketSequenceId(1)
            .setTrackEvent(TrackEvent.newBuilder()
                .setTrackUuid(uuid)
                .setName(start.displayName)
                .addCategories(start.detailsClassName ?: "")
                .addDebugAnnotations(toDebugAnnotations(start.details, "details"))
                .setType(TrackEvent.Type.TYPE_SLICE_BEGIN)
            )
            .build())

        return { _, finish ->
            writeTracePacket(TracePacket.newBuilder()
                .setTimestampClockId(64)
                .setTimestamp(finish.endTime - startTime.get())
                .setTrustedPacketSequenceId(1)
                .setTrackEvent(TrackEvent.newBuilder()
                    .setTrackUuid(uuid)
                    .addDebugAnnotations(toDebugAnnotations(finish.result, "result"))
                    .setType(TrackEvent.Type.TYPE_SLICE_END)
                )
                .build()
            )
        }
    }

    private fun onFirstRecord(record: BuildOperationStart) {
        startTime.set(record.startTime)

        writeTracePacket(TracePacket.newBuilder()
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
