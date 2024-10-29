package org.gradle.tools.trace.app

import com.google.gson.Gson
import com.google.gson.JsonElement
import com.google.gson.TypeAdapter
import com.google.gson.TypeAdapterFactory
import com.google.gson.reflect.TypeToken
import com.google.gson.stream.JsonReader
import com.google.gson.stream.JsonWriter

class BuildOperationLogAdapterFactory : TypeAdapterFactory {
    val baseType = BuildOperationLog::class

    override fun <T : Any?> create(gson: Gson, type: TypeToken<T>?): TypeAdapter<T>? {
        if (type == null || baseType.java != type.rawType) {
            return null
        }
        val jsonElementAdapter: TypeAdapter<JsonElement> = gson.getAdapter(JsonElement::class.java)
        val operationStartAdapter = getDelegateAdapter<BuildOperationStart>(gson)
        val operationFinishAdapter = getDelegateAdapter<BuildOperationFinish>(gson)
        val operationProgressAdapter = getDelegateAdapter<BuildOperationProgress>(gson)

        return object : TypeAdapter<T>() {
            override fun write(out: JsonWriter?, value: T) {
                throw UnsupportedOperationException("${baseType.simpleName} adaptor does not support writing")
            }

            override fun read(`in`: JsonReader?): T {
                val jsonElement = jsonElementAdapter.read(`in`)
                val jsonObject = jsonElement.asJsonObject
                val delegateAdapter = when {
                    jsonObject.has("startTime") -> operationStartAdapter
                    jsonObject.has("endTime") -> operationFinishAdapter
                    else -> operationProgressAdapter
                }
                @Suppress("UNCHECKED_CAST")
                return delegateAdapter.fromJsonTree(jsonElement) as T
            }

        }
    }

    private inline fun <reified T : BuildOperationLog> getDelegateAdapter(gson: Gson): TypeAdapter<T> =
        gson.getDelegateAdapter(this, TypeToken.get(T::class.java))
}
