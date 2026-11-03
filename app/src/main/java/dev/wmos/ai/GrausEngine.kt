package dev.wmos.ai

import dev.wmos.AppContainer
import dev.wmos.data.db.entity.ChatMessageEntity

/**
 * Orchestrates one Граус request:
 *   model -> tool calls -> (confirmation) -> tool result -> model -> answer.
 *
 * The model never writes directly: mutating tools are captured as [PendingAction] and only
 * `confirm()` executes them. The engine reports what the tool actually returned, not what the
 * model claims.
 */
class GrausEngine(private val container: AppContainer) {
    private val tools = ToolRegistry(container)
    private val client = AiClient()

    data class Reply(
        val text: String,
        val pending: PendingAction? = null,
        val error: String? = null,
        val toolNotes: List<String> = emptyList(),
    )

    private val continuations = mutableMapOf<String, MutableList<ChatTurn>>()

    suspend fun ask(
        conversationId: String,
        history: List<ChatMessageEntity>,
        userText: String,
        onDelta: (String) -> Unit = {},
    ): Reply {
        val provider = container.providers.default()
            ?: return Reply(
                text = "",
                error = "Провайдер не настроен. Откройте «Ещё → AI Lab» и добавьте свой API-ключ.",
            )
        val key = container.providers.apiKey(provider)
            ?: return Reply(text = "", error = "Ключ провайдера не найден. Сохраните его заново в AI Lab.")

        val turns = mutableListOf<ChatTurn>()
        turns += ChatTurn(ChatRole.SYSTEM, ToolRegistry.SYSTEM_PROMPT)
        history.forEach { message ->
            val role = when (message.role) {
                "user" -> ChatRole.USER
                "assistant" -> ChatRole.ASSISTANT
                else -> return@forEach
            }
            if (message.content.isNotBlank()) turns += ChatTurn(role, message.content)
        }
        turns += ChatTurn(ChatRole.USER, userText)
        continuations[conversationId] = turns

        return loop(conversationId, provider.id, onDelta)
    }

    suspend fun confirm(
        pending: PendingAction,
        conversationId: String,
        onDelta: (String) -> Unit = {},
    ): Reply {
        val outcome = tools.commit(pending.tool, pending.argumentsJson)
        container.actionLog.log(
            actor = "graus",
            tool = pending.tool,
            status = outcome.status.name,
            summary = outcome.summary,
            targetIds = outcome.affectedIds,
        )
        val turns = continuations[conversationId]
            ?: return Reply(text = "", error = "Подтверждение устарело. Повторите запрос.")
        turns += ChatTurn(
            role = ChatRole.TOOL,
            text = outcome.payloadJson,
            toolCallId = pending.toolCallId,
        )
        return loop(conversationId, pending.providerId, onDelta)
    }

    fun cancelPending(conversationId: String) {
        continuations.remove(conversationId)
    }

    private suspend fun loop(
        conversationId: String,
        providerId: String,
        onDelta: (String) -> Unit,
    ): Reply {
        val provider = container.providers.get(providerId)
            ?: return Reply(text = "", error = "Провайдер удалён")
        val key = container.providers.apiKey(provider)
            ?: return Reply(text = "", error = "Ключ провайдера не найден")

        val turns = continuations[conversationId] ?: return Reply(text = "", error = "Сессия истекла")
        val notes = mutableListOf<String>()

        repeat(MAX_ITERATIONS) {
            // A new model pass starts a fresh answer; tell the UI to reset its live bubble.
            onDelta("")
            when (val result = client.chatStream(provider, key, turns, tools.definitions) { delta -> onDelta(delta) }) {
                is AiResult.Failure -> return Reply(text = "", error = result.message)

                is AiResult.Success -> {
                    if (result.toolCalls.isEmpty()) {
                        continuations.remove(conversationId)
                        return Reply(text = result.text.ifBlank { "Готово." }, toolNotes = notes)
                    }
                    turns += ChatTurn(
                        role = ChatRole.ASSISTANT,
                        text = result.text,
                        toolCalls = result.toolCalls,
                    )
                    for (call in result.toolCalls) {
                        if (tools.isMutating(call.name)) {
                            val prepared = tools.prepare(call.name, call.argumentsJson)
                            if (prepared == null) {
                                turns += ChatTurn(
                                    role = ChatRole.TOOL,
                                    text = """{"status":"invalid_input","message":"Аргументы не прошли валидацию"}""",
                                    toolCallId = call.id,
                                )
                                notes += "Инструмент ${call.name}: неверные аргументы"
                                continue
                            }
                            container.actionLog.log(
                                actor = "graus",
                                tool = call.name,
                                status = "NEEDS_CONFIRMATION",
                                summary = prepared.description,
                            )
                            return Reply(
                                text = result.text,
                                pending = prepared.copy(toolCallId = call.id, providerId = provider.id),
                                toolNotes = notes,
                            )
                        }
                        val outcome = tools.execute(call.name, call.argumentsJson)
                        container.actionLog.log(
                            actor = "graus",
                            tool = call.name,
                            status = outcome.status.name,
                            summary = outcome.summary,
                            targetIds = outcome.affectedIds,
                        )
                        notes += "${call.name}: ${outcome.summary}"
                        turns += ChatTurn(
                            role = ChatRole.TOOL,
                            text = outcome.payloadJson,
                            toolCallId = call.id,
                        )
                    }
                }
            }
        }
        continuations.remove(conversationId)
        return Reply(text = "Не удалось завершить за отведённое число шагов.", toolNotes = notes)
    }

    companion object {
        private const val MAX_ITERATIONS = 4
    }
}
