package org.gradle.tools.trace.app

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.google.gson.Gson
import perfetto.protos.TraceOuterClass.Trace
import java.io.File

fun main(args: Array<String>) = ConverterApp().main(args)

enum class OutputFormat {
    CHROME_TRACE,
    TIMELINE,
    TRANSFORM_CSV
}

class ConverterApp : CliktCommand() {

    private val buildOperationTrace: File by argument(name = "trace", help = "Path to the build operation trace file")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    private val outputFormat: OutputFormat by option("-o", "--output-format", help = "The output format to use")
        .choice(
            "chrome" to OutputFormat.CHROME_TRACE,
            "timeline" to OutputFormat.TIMELINE,
            "transform-summary" to OutputFormat.TRANSFORM_CSV,
        )
        .default(OutputFormat.CHROME_TRACE)

    private val include: Regex? by option("-i", "--include", help = "Regex to filter the build operations to include by display name")
        .convert { it.toRegex() }

    private val exclude: Regex? by option("-e", "--exclude", help = "Regex to filter the build operations to exclude")
        .convert { it.toRegex() }

    override fun run() {
        when (outputFormat) {
            OutputFormat.CHROME_TRACE -> convertToChromeTrace(buildOperationTrace)
            OutputFormat.TIMELINE -> convertToTimelineCsv(buildOperationTrace)
            OutputFormat.TRANSFORM_CSV -> convertToTransformSummary(buildOperationTrace)
        }
    }

    private fun convertToChromeTrace(traceFile: File) {
        val slice = readTraceSlice(traceFile)
        val traceEvents = TraceToChromeTraceConverter().convert(slice)
        val trace = Trace.newBuilder()
            .addAllPacket(traceEvents)
            .build()
        val traceFileProto = File(traceFile.parentFile, traceFile.nameWithoutExtension + "-chrome.proto")
        traceFileProto.writeBytes(trace.toByteArray())
        println("Wrote ${traceEvents.size} events to ${traceFileProto.absolutePath}")
    }

    private fun convertToTimelineCsv(traceFile: File) {
        val slice = readTraceSlice(traceFile)
        val outputFile = File(traceFile.parentFile, traceFile.nameWithoutExtension + "-timeline.csv")
        TraceToTimelineConverter().convert(slice, outputFile)
    }

    private fun convertToTransformSummary(traceFile: File) {
        val slice = readTraceSlice(traceFile)
        val outputFile = File(traceFile.parentFile, traceFile.nameWithoutExtension + "-transform-summary.csv")
        TraceToTransformCsvConverter().convert(slice, outputFile)
    }

    private fun readTraceSlice(traceFile: File): BuildOperationTraceSlice {
        val records = readBuildOperationTrace(traceFile)
        println("Read ${records.size} build operation tree roots from ${traceFile.name}")
        return BuildOperationTraceSlice(records.toList(), include, exclude)
    }

    private fun readBuildOperationTrace(traceJsonFile: File): Array<BuildOperationRecord> {
        val inputTraceJsonText = traceJsonFile.readText()
        try {
            return Gson().fromJson(inputTraceJsonText, Array<BuildOperationRecord>::class.java)
        } catch (e: Exception) {
            throw RuntimeException("Failed to read build operation trace file: ${traceJsonFile.absolutePath}", e)
        }
    }

}
