package org.gradle.tools.trace.app


sealed class ComponentIdentifier {

    data class Project(val buildPath: String, val projectPath: String) : ComponentIdentifier() {
        override fun toString(): String =
            if (buildPath == ":") projectPath else "$buildPath$projectPath"
    }

    data class Module(val group: String, val module: String, val version: String) : ComponentIdentifier() {
        override fun toString(): String = "$group:$module:$version"
    }

    data class Unknown(val displayName: String, val className: String) : ComponentIdentifier() {
        override fun toString(): String = "$displayName ($className)"
    }
}

data class ExecuteTaskBuildOperationDetails(
    val buildPath: String,
    val taskPath: String,
    val taskId: Long,
    val taskClass: String,
) {

    companion object {

        @Suppress("UNCHECKED_CAST")
        fun fromRecord(record: BuildOperationRecord): ExecuteTaskBuildOperationDetails {
            val details = record.details as Map<String, *>

            return ExecuteTaskBuildOperationDetails(
                buildPath = details["buildPath"] as String,
                taskPath = details["taskPath"] as String,
                taskId = details["taskId"].toString().toDouble().toLong(),
                taskClass = details["taskClass"] as String,
            )
        }
    }
}

data class TransformationIdentity(
    val buildPath: String,
    val projectPath: String,
    val componentId: ComponentIdentifier,
    val sourceAttributes: Map<String, String>,
    val transformType: String,
    val fromAttributes: Map<String, String>,
    val toAttributes: Map<String, String>,
    val transformationNodeId: Long,
) {

    companion object {

        @Suppress("UNCHECKED_CAST")
        fun fromRecord(record: BuildOperationRecord): TransformationIdentity {
            val identityData = (record.details as Map<String, *>)["transformationIdentity"] as Map<String, *>

            return TransformationIdentity(
                buildPath = identityData["buildPath"] as String,
                projectPath = identityData["projectPath"] as String,
                componentId = unpackComponentIdentifier(identityData["componentId"] as Map<String, String>),
                sourceAttributes = (identityData["sourceAttributes"] as Map<String, String>).toSortedMap(),
                transformType = identityData["transformType"] as String,
                fromAttributes = (identityData["fromAttributes"] as Map<String, String>).toSortedMap(),
                toAttributes = (identityData["toAttributes"] as Map<String, String>).toSortedMap(),
                transformationNodeId = identityData["transformationNodeId"].toString().toDouble().toLong(),
            )
        }

        private fun unpackComponentIdentifier(fields: Map<String, String>): ComponentIdentifier {
            val buildPath = fields["buildPath"]
            val projectPath = fields["projectPath"]
            val group = fields["group"]
            val module = fields["module"]
            val version = fields["version"]
            val displayName = fields["displayName"]
            val className = fields["className"]

            return when {
                buildPath != null && projectPath != null -> ComponentIdentifier.Project(buildPath, projectPath)
                group != null && module != null && version != null -> ComponentIdentifier.Module(group, module, version)
                displayName != null && className != null -> ComponentIdentifier.Unknown(displayName, className)
                else -> throw IllegalArgumentException("Unknown component identifier: $fields")
            }
        }
    }
}
