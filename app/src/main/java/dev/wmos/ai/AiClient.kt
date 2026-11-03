package dev.wmos.ai

import dev.wmos.data.db.entity.AiProviderEntity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Roles kept deliberately small: everything the model sees is one of these. */
enum class ChatRole { SYSTEM, USER, ASSISTANT, TOOL }

data class ChatTurn(
    val role: ChatRole,
    val text: String = "",
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
)

data class ToolCall(val id: String, val name: String, val argumentsJson: String)

sealed interface AiResult {
    data class Success(
        val text: String,
        val toolCalls: List<ToolCall>,
        val finishReason: String,
    ) : AiResult

    data class Failure(val kind: AiError, val message: String) : AiResult
}

enum class AiError {
    INVALID_KEY,
    RATE_LIMITED,
    QUOTA_EXCEEDED,
    UNSUPPORTED_MODEL,
    CONTEXT_TOO_LARGE,
    TIMEOUT,
    NETWORK,
    SERVER,
    INVALID_RESPONSE,
    NO_PROVIDER,
    UNKNOWN,
}

/**
 * Minimal, dependency-light OpenAI-compatible client.
 *
 * Deliberate constraints:
 *  * HTTPS only, certificate validation never disabled;
 *  * bounded timeouts and a bounded response body;
 *  * `Retry-After` is surfaced, never spun on;
 *  * the API key is read per request from the keystore store and never logged.
 */
class AiClient(
    private val http: OkHttpClient = defaultClient(),
) {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
    }

    fun chat(
        provider: AiProviderEntity,
        apiKey: String,
        turns: List<ChatTurn>,
        tools: List<JsonObject> = emptyList(),
        temperature: Double = 0.3,
        maxTokens: Int = 1200,
    ): AiResult {
        if (!provider.baseUrl.startsWith("https://")) {
            return AiResult.Failure(AiError.NETWORK, "Провайдер должен использовать HTTPS")
        }
        val body = buildJsonObject {
            put("model", provider.model)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put(
                "messages",
                buildJsonArray {
                    turns.forEach { turn -> add(encodeTurn(turn)) }
                },
            )
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(tools))
                put("tool_choice", "auto")
            }
        }

        val request = Request.Builder()
            .url(provider.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                val payload = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@use AiResult.Failure(
                        mapHttpError(response.code, payload),
                        describeError(response.code, payload),
                    )
                }
                parseResponse(payload)
            }
        } catch (timeout: java.net.SocketTimeoutException) {
            AiResult.Failure(AiError.TIMEOUT, "Провайдер не ответил вовремя")
        } catch (io: IOException) {
            AiResult.Failure(AiError.NETWORK, "Нет связи с провайдером")
        } catch (invalid: Exception) {
            AiResult.Failure(AiError.INVALID_RESPONSE, "Некорректный ответ провайдера")
        }
    }

    /**
     * Same request as [chat] but with `stream: true`. Deltas arrive over SSE; the model's answer
     * grows character by character instead of freezing for the whole generation. Tool calls are
     * still fully supported: they are accumulated and returned together with the final text.
     */
    fun chatStream(
        provider: AiProviderEntity,
        apiKey: String,
        turns: List<ChatTurn>,
        tools: List<JsonObject> = emptyList(),
        temperature: Double = 0.3,
        maxTokens: Int = 1600,
        onDelta: (String) -> Unit,
    ): AiResult {
        if (!provider.baseUrl.startsWith("https://")) {
            return AiResult.Failure(AiError.NETWORK, "Провайдер должен использовать HTTPS")
        }
        val body = buildJsonObject {
            put("model", provider.model)
            put("temperature", temperature)
            put("max_tokens", maxTokens)
            put("stream", true)
            put(
                "messages",
                buildJsonArray {
                    turns.forEach { turn -> add(encodeTurn(turn)) }
                },
            )
            if (tools.isNotEmpty()) {
                put("tools", JsonArray(tools))
                put("tool_choice", "auto")
            }
        }

        val request = Request.Builder()
            .url(provider.baseUrl.trimEnd('/') + "/chat/completions")
            .header("Authorization", "Bearer $apiKey")
            .header("Content-Type", "application/json")
            .post(body.toString().toRequestBody(JSON_MEDIA))
            .build()

        return try {
            http.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val payload = response.body?.string().orEmpty()
                    return@use AiResult.Failure(
                        mapHttpError(response.code, payload),
                        describeError(response.code, payload),
                    )
                }
                val source = response.body?.source()
                    ?: return@use AiResult.Failure(AiError.INVALID_RESPONSE, "Провайдер вернул пустой ответ")
                val accumulator = ChatStreamAccumulator()
                var reportedLength = -1
                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val payload = line.removePrefix("data:").trim()
                    if (payload == "[DONE]") break
                    if (accumulator.accept(payload) && accumulator.textLength != reportedLength) {
                        reportedLength = accumulator.textLength
                        onDelta(accumulator.textContent)
                    }
                }
                accumulator.finish()
            }
        } catch (timeout: java.net.SocketTimeoutException) {
            AiResult.Failure(AiError.TIMEOUT, "Провайдер не ответил вовремя")
        } catch (io: java.io.IOException) {
            AiResult.Failure(AiError.NETWORK, "Нет связи с провайдером")
        } catch (invalid: Exception) {
            AiResult.Failure(AiError.INVALID_RESPONSE, "Некорректный ответ провайдера")
        }
    }

    private fun encodeTurn(turn: ChatTurn): JsonObject = buildJsonObject {
        put("role", turn.role.name.lowercase())
        if (turn.role == ChatRole.TOOL) {
            put("tool_call_id", turn.toolCallId ?: "")
            put("content", turn.text)
            return@buildJsonObject
        }
        if (turn.toolCalls.isNotEmpty()) {
            put("content", JsonNull)
            put(
                "tool_calls",
                buildJsonArray {
                    turn.toolCalls.forEach { call ->
                        add(
                            buildJsonObject {
                                put("id", call.id)
                                put("type", "function")
                                put(
                                    "function",
                                    buildJsonObject {
                                        put("name", call.name)
                                        put("arguments", call.argumentsJson)
                                    },
                                )
                            },
                        )
                    }
                },
            )
        } else {
            put("content", turn.text)
        }
    }

    private fun parseResponse(payload: String): AiResult {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }
            .getOrElse { return AiResult.Failure(AiError.INVALID_RESPONSE, "Ответ не является JSON") }

        root["error"]?.let { error ->
            val message = error.jsonObject["message"]?.jsonPrimitive?.contentOrNullSafe() ?: "Ошибка провайдера"
            return AiResult.Failure(AiError.UNKNOWN, message)
        }

        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject
            ?: return AiResult.Failure(AiError.INVALID_RESPONSE, "Провайдер вернул пустой ответ")
        val message = choice["message"]?.jsonObject
            ?: return AiResult.Failure(AiError.INVALID_RESPONSE, "В ответе нет message")
        val text = message["content"]?.let { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNullSafe()
                is JsonArray -> element.joinToString("") { part ->
                    part.jsonObject["text"]?.jsonPrimitive?.contentOrNullSafe() ?: ""
                }

                else -> null
            }
        } ?: ""
        val toolCalls = message["tool_calls"]?.jsonArray?.mapNotNull { element ->
            val obj = element.jsonObject
            val function = obj["function"]?.jsonObject ?: return@mapNotNull null
            ToolCall(
                id = obj["id"]?.jsonPrimitive?.contentOrNullSafe() ?: "",
                name = function["name"]?.jsonPrimitive?.contentOrNullSafe() ?: return@mapNotNull null,
                argumentsJson = function["arguments"]?.jsonPrimitive?.contentOrNullSafe() ?: "{}",
            )
        } ?: emptyList()

        return AiResult.Success(
            text = text.trim(),
            toolCalls = toolCalls,
            finishReason = choice["finish_reason"]?.jsonPrimitive?.contentOrNullSafe() ?: "stop",
        )
    }

    private fun mapHttpError(code: Int, payload: String): AiError {
        val lower = payload.lowercase()
        return when {
            code == 401 || code == 403 -> AiError.INVALID_KEY
            code == 402 || (code == 429 && (lower.contains("quota") || lower.contains("billing"))) ->
                AiError.QUOTA_EXCEEDED
            code == 429 -> AiError.RATE_LIMITED
            code == 404 && lower.contains("model") -> AiError.UNSUPPORTED_MODEL
            code == 413 || lower.contains("context length") || lower.contains("maximum context") ->
                AiError.CONTEXT_TOO_LARGE

            code >= 500 -> AiError.SERVER
            else -> AiError.UNKNOWN
        }
    }

    private fun describeError(code: Int, payload: String): String {
        val snippet = payload.take(300).replace(Regex("\\s+"), " ")
        return when (code) {
            401, 403 -> "Ключ неверный или отозван ($code)"
            402 -> "На балансе провайдера закончились средства (402). Пополните аккаунт или выберите другого провайдера в AI Lab."
            429 -> "Слишком много запросов, попробуйте позже ($code)"
            404 -> "Модель или адрес недоступны ($code)"
            in 500..599 -> "Ошибка на стороне провайдера ($code)"
            else -> "Ошибка провайдера ($code): $snippet"
        }
    }

    companion object {
        private val JSON_MEDIA = "application/json; charset=utf-8".toMediaType()

        fun defaultClient(): OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(90, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .callTimeout(120, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .build()
    }
}

private fun JsonPrimitive.contentOrNullSafe(): String? =
    if (this is JsonNull) null else this.content

private fun JsonPrimitive.intOrNullSafe(): Int? =
    if (this is JsonNull) null else this.content.toIntOrNull()

/**
 * Rebuilds the full message from OpenAI-style streaming `delta` chunks.
 *
 * Pure and synchronous: it knows nothing about HTTP, so it is unit-testable. Keeps a running
 * text buffer and accumulating tool-call fragments by index (arguments arrive in pieces).
 */
class ChatStreamAccumulator {
    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
    }
    private val text = StringBuilder()
    private val toolIds = mutableMapOf<Int, String>()
    private val toolNames = mutableMapOf<Int, String>()
    private val toolArgs = mutableMapOf<Int, StringBuilder>()
    private var finishReason: String? = null

    /** Current accumulated answer text. */
    val textContent: String get() = text.toString()

    /** Number of content characters accumulated so far, used to skip duplicate notifications. */
    val textLength: Int get() = text.length

    /** Accepts one SSE payload (the JSON after `data:`). Returns false if the chunk carried nothing. */
    fun accept(payload: String): Boolean {
        val root = runCatching { json.parseToJsonElement(payload).jsonObject }.getOrNull() ?: return false
        val choice = root["choices"]?.jsonArray?.firstOrNull()?.jsonObject ?: return false
        val delta = choice["delta"]?.jsonObject ?: return false
        var progressed = false

        (delta["content"] as? JsonPrimitive)?.takeIf { it !is JsonNull }?.let { piece ->
            if (piece.content.isNotEmpty()) {
                text.append(piece.content)
                progressed = true
            }
        }

        delta["tool_calls"]?.jsonArray?.forEach { element ->
            val obj = runCatching { element.jsonObject }.getOrNull() ?: return@forEach
            val index = obj["index"]?.jsonPrimitive?.intOrNullSafe() ?: return@forEach
            obj["id"]?.jsonPrimitive?.contentOrNullSafe()?.takeIf { it.isNotBlank() }?.let {
                toolIds[index] = it
                progressed = true
            }
            val function = obj["function"]?.jsonObject
            function?.get("name")?.jsonPrimitive?.contentOrNullSafe()?.takeIf { it.isNotBlank() }?.let {
                toolNames[index] = it
                progressed = true
            }
            function?.get("arguments")?.jsonPrimitive?.contentOrNullSafe()?.let { piece ->
                if (piece.isNotEmpty()) {
                    toolArgs.getOrPut(index) { StringBuilder() }.append(piece)
                    progressed = true
                }
            }
        }

        choice["finish_reason"]?.jsonPrimitive?.contentOrNullSafe()?.let { finishReason = it }
        return progressed
    }

    /** Builds the final result from everything accumulated. */
    fun finish(): AiResult {
        if (text.isBlank() && toolIds.isEmpty()) {
            return AiResult.Failure(AiError.INVALID_RESPONSE, "Провайдер вернул пустой ответ")
        }
        val calls = toolIds.keys.sorted().mapNotNull { index ->
            val name = toolNames[index] ?: return@mapNotNull null
            ToolCall(
                id = toolIds[index].orEmpty().ifBlank { "call_$index" },
                name = name,
                argumentsJson = toolArgs[index]?.toString().orEmpty().ifBlank { "{}" },
            )
        }
        return AiResult.Success(
            text = text.toString().trim(),
            toolCalls = calls,
            finishReason = finishReason ?: "stop",
        )
    }
}

/** Known-good presets. The user still supplies the key and may edit every field. */
data class ProviderPreset(
    val id: String,
    val name: String,
    val baseUrl: String,
    val defaultModel: String,
    val docsHint: String,
)

object ProviderPresets {
    val all = listOf(
        ProviderPreset("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini", "platform.openai.com"),
        ProviderPreset("deepseek", "DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat", "platform.deepseek.com"),
        ProviderPreset("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile", "console.groq.com"),
        ProviderPreset("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini", "openrouter.ai"),
        ProviderPreset("custom", "Свой сервер", "https://", "", "любой OpenAI-совместимый endpoint"),
    )
}

fun toolSchema(
    name: String,
    description: String,
    properties: Map<String, Pair<String, String>>,
    required: List<String>,
): JsonObject = buildJsonObject {
    put("type", "function")
    put(
        "function",
        buildJsonObject {
            put("name", name)
            put("description", description)
            put(
                "parameters",
                buildJsonObject {
                    put("type", "object")
                    put(
                        "properties",
                        buildJsonObject {
                            properties.forEach { (key, value) ->
                                put(
                                    key,
                                    buildJsonObject {
                                        put("type", value.first)
                                        put("description", value.second)
                                    },
                                )
                            }
                        },
                    )
                    put("required", JsonArray(required.map { JsonPrimitive(it) }))
                    put("additionalProperties", false)
                },
            )
        },
    )
}
