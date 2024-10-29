package org.gradle.tools.trace.app.buildops

import com.google.gson.Gson
import org.gradle.tools.trace.app.BuildOperationStart


data class ExecuteTaskBuildOperationDetails(
    val buildPath: String,
    val taskPath: String,
    val taskId: Long,
    val taskClass: String,
) {

    companion object {

        @Suppress("UNCHECKED_CAST")
        fun fromStart(record: BuildOperationStart): ExecuteTaskBuildOperationDetails {
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

    companion object {
        fun fromMap(fields: Map<String, String>): ComponentIdentifier {
            val buildPath = fields["buildPath"]
            val projectPath = fields["projectPath"]
            val group = fields["group"]
            val module = fields["module"]
            val version = fields["version"]
            val displayName = fields["displayName"]
            val className = fields["className"]

            return when {
                buildPath != null && projectPath != null -> Project(buildPath, projectPath)
                group != null && module != null && version != null -> Module(group, module, version)
                displayName != null && className != null -> Unknown(displayName, className)
                else -> throw IllegalArgumentException("Unknown component identifier: $fields")
            }
        }
    }
}

data class ComponentVariantIdentifier(
    val componentId: ComponentIdentifier,
    val attributes: Map<String, String>,
    val capabilities: List<Capability>,
) {
    data class Capability(
        val group: String,
        val name: String,
        val version: String?
    ) {

        override fun toString(): String {
            return if (version != null) "$group:$name:$version" else "$group:$name"
        }
    }

    companion object {
        @Suppress("UNCHECKED_CAST")
        fun fromMap(data: Map<String, *>): ComponentVariantIdentifier {
            val componentId = data["componentId"] as Map<String, *>
            val attributes = data["attributes"] as Map<String, String>
            val capabilities = data["capabilities"] as List<Map<String, String?>>

            return ComponentVariantIdentifier(
                componentId = ComponentIdentifier.fromMap(componentId as Map<String, String>),
                attributes = attributes,
                capabilities = capabilities.map {
                    Capability(
                        group = it["group"] as String,
                        name = it["name"] as String,
                        version = it["version"],
                    )
                }
            )
        }
    }
}

data class ConfigurationIdentity(
    val buildPath: String,
    val projectPath: String,
    val name: String,
)

data class TransformationIdentity(
    val buildPath: String,
    val projectPath: String,
    val targetVariant: ComponentVariantIdentifier,
    val artifactName: String,
    val dependenciesConfigurationIdentity: ConfigurationIdentity?,
    val transformationNodeId: Long,
) {

    companion object {

        private val gson = Gson()
        @Suppress("UNCHECKED_CAST")
        fun fromMap(data: Map<String, *>): TransformationIdentity {
            return TransformationIdentity(
                buildPath = data["buildPath"] as String,
                projectPath = data["projectPath"] as String,
                targetVariant = ComponentVariantIdentifier.fromMap(data["targetVariant"] as Map<String, *>),
                artifactName = data["artifactName"] as String,
                dependenciesConfigurationIdentity = (data["dependenciesConfigurationIdentity"] as Map<String, *>?)?.let {
                    ConfigurationIdentity(
                        buildPath = it["buildPath"] as String,
                        projectPath = it["projectPath"] as String,
                        name = it["name"] as String,
                    )
                },
                transformationNodeId = data["transformationNodeId"].toString().toDouble().toLong(),
            )
        }
    }
}

data class ExecuteScheduledTransformationStepBuildOperationDetails(
    val transformationIdentity: TransformationIdentity,
    val sourceAttributes: Map<String, String>,
    val transformType: String,
    val fromAttributes: Map<String, String>,
    val toAttributes: Map<String, String>,
) {

    companion object {

            @Suppress("UNCHECKED_CAST")
            fun fromRecord(record: BuildOperationStart): ExecuteScheduledTransformationStepBuildOperationDetails {
                val details = record.details as Map<String, *>

                return ExecuteScheduledTransformationStepBuildOperationDetails(
                    transformationIdentity = TransformationIdentity.fromMap(details["transformationIdentity"] as Map<String, *>),
                    sourceAttributes = (details["sourceAttributes"] as Map<String, String>).toSortedMap(),
                    transformType = details["transformType"] as String,
                    fromAttributes = (details["fromAttributes"] as Map<String, String>).toSortedMap(),
                    toAttributes = (details["toAttributes"] as Map<String, String>).toSortedMap(),
                )
            }
    }
}

