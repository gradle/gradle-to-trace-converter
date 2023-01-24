package org.gradle.tools.trace.app

import com.github.ajalt.clikt.core.CliktCommand
import com.github.ajalt.clikt.parameters.arguments.argument
import com.github.ajalt.clikt.parameters.options.convert
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.types.choice
import com.github.ajalt.clikt.parameters.types.file
import com.google.gson.Gson
import java.io.File

fun main(args: Array<String>) = ConverterApp().main(args)

enum class OutputFormat(val filePostfix: String) {
    ALL(""),
    CHROME_TRACE("-chrome.proto"),
    TIMELINE("-timeline.csv"),
    TRANSFORM_CSV("-transform-summary.csv");
}

class ConverterApp : CliktCommand() {

    private val buildOperationTrace: File by argument(name = "trace", help = "Path to the build operation trace file")
        .file(mustExist = true, canBeDir = false, mustBeReadable = true)

    private val outputFormat: OutputFormat by option("-o", "--output-format", help = "The output format to use")
        .choice(
            "all" to OutputFormat.ALL,
            "chrome" to OutputFormat.CHROME_TRACE,
            "timeline" to OutputFormat.TIMELINE,
            "transform-summary" to OutputFormat.TRANSFORM_CSV,
        )
        .default(OutputFormat.ALL)

    private val include: Regex? by option("-i", "--include", help = "Regex to filter the build operations to include by display name")
        .convert { it.toRegex() }

    private val exclude: Regex? by option("-e", "--exclude", help = "Regex to filter the build operations to exclude")
        .convert { it.toRegex() }

    override fun run() {
        val slice = readTraceSlice(buildOperationTrace)
        val formats = when (outputFormat) {
            OutputFormat.ALL -> OutputFormat.values().filter { it != OutputFormat.ALL }
            else -> listOf(outputFormat)
        }

        for (outputFormat in formats) {
            val outputFile = outputFile(buildOperationTrace, outputFormat)
            when (outputFormat) {
                OutputFormat.CHROME_TRACE -> convertToChromeTrace(outputFile, slice)
                OutputFormat.TIMELINE -> convertToTimelineCsv(outputFile, slice)
                OutputFormat.TRANSFORM_CSV -> convertToTransformSummary(outputFile, slice)
                else -> error("Unexpected output format: $outputFormat")
            }
        }
    }

    private fun outputFile(traceFile: File, format: OutputFormat): File {
        return File(traceFile.parentFile, traceFile.nameWithoutExtension + format.filePostfix)
    }

    private fun convertToChromeTrace(outputFile: File, slice: BuildOperationTraceSlice) {
        TraceToChromeTraceConverter().convert(slice, outputFile)
    }

    private fun convertToTimelineCsv(outputFile: File, slice: BuildOperationTraceSlice) {
        TraceToTimelineConverter().convert(slice, outputFile)
    }

    private fun convertToTransformSummary(outputFile: File, slice: BuildOperationTraceSlice) {
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
