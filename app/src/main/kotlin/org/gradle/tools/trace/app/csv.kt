package org.gradle.tools.trace.app



fun composeCsvRow(vararg values: String) = values.joinToString(",") { convertToCsvValue(it) }

private fun convertToCsvValue(s: String) =
    if (s.contains(",") || s.contains("\"")) {
        "\"${s.replace("\"", "\"\"")}\""
    } else {
        s
    }
