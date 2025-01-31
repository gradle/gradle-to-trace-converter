package org.gradle.tools.trace.app.util


fun Map<String, String>.attributesMapToString() = entries
    .joinToString { "${it.key}=${it.value}" }

