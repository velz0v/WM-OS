package dev.wmos.ai

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The streaming parser rebuilds the answer from OpenAI-style `delta` chunks. These tests pin the
 * exact shapes real providers emit: text, tool calls assembled from argument fragments, and the
 * [DONE] sentinel handled by the caller (not the accumulator).
 */
class ChatStreamAccumulatorTest {
    private fun chunk(content: String? = null, toolCalls: String? = null, finish: String? = null): String {
        val delta = buildString {
            append("{")
            var first = true
            if (content != null) {
                append("\"content\":")
                append('"')
                append(content)
                append('"')
                first = false
            }
            if (toolCalls != null) {
                if (!first) append(",")
                append("\"tool_calls\":")
                append(toolCalls)
                first = false
            }
            append("}")
        }
        val finishPart = finish?.let { ", \"finish_reason\": \"$it\"" } ?: ""
        return """{"choices": [{"delta": $delta$finishPart}]}"""
    }

    @Test
    fun `text chunks accumulate in order`() {
        val acc = ChatStreamAccumulator()
        assertTrue(acc.accept(chunk(content = "Привет")))
        assertTrue(acc.accept(chunk(content = ", мир")))
        assertTrue(acc.accept(chunk(content = "!")))

        val result = acc.finish()
        assertTrue(result is AiResult.Success)
        result as AiResult.Success
        assertEquals("Привет, мир!", result.text)
        assertTrue(result.toolCalls.isEmpty())
        assertEquals("stop", result.finishReason)
    }

    @Test
    fun `tool call arguments reassemble from fragments`() {
        val acc = ChatStreamAccumulator()
        acc.accept(
            chunk(
                toolCalls = """[{"index": 0, "id": "call_1", "function": {"name": "create_task", "arguments": "{\"ti"}}]""",
            ),
        )
        acc.accept(
            chunk(
                toolCalls = """[{"index": 0, "function": {"arguments": "tle\": \"Тест\"}"}}]""",
                finish = "tool_calls",
            ),
        )

        val result = acc.finish()
        assertTrue(result is AiResult.Success)
        result as AiResult.Success
        assertEquals(1, result.toolCalls.size)
        val call = result.toolCalls.single()
        assertEquals("create_task", call.name)
        assertEquals("call_1", call.id)
        assertEquals("""{"title": "Тест"}""", call.argumentsJson)
    }

    @Test
    fun `several tool calls keep their order by index`() {
        val acc = ChatStreamAccumulator()
        acc.accept(
            chunk(
                toolCalls = """[{"index": 1, "id": "call_b", "function": {"name": "create_note", "arguments": "{}"}}]""",
            ),
        )
        acc.accept(
            chunk(
                toolCalls = """[{"index": 0, "id": "call_a", "function": {"name": "create_task", "arguments": "{}"}}]""",
            ),
        )

        val result = acc.finish() as AiResult.Success
        assertEquals(listOf("create_task", "create_note"), result.toolCalls.map { it.name })
    }

    @Test
    fun `text and tool calls can arrive in one pass`() {
        val acc = ChatStreamAccumulator()
        acc.accept(chunk(content = "Создаю задачу"))
        acc.accept(
            chunk(
                toolCalls = """[{"index": 0, "id": "call_x", "function": {"name": "create_task", "arguments": "{\"title\":\"A\"}"}}]""",
            ),
        )

        val result = acc.finish() as AiResult.Success
        assertEquals("Создаю задачу", result.text)
        assertEquals(1, result.toolCalls.size)
    }

    @Test
    fun `broken and empty chunks are ignored`() {
        val acc = ChatStreamAccumulator()
        assertFalse(acc.accept("not json"))
        assertFalse(acc.accept("""{"choices": []}"""))
        assertFalse(acc.accept(chunk(content = "")))

        assertTrue(acc.finish() is AiResult.Failure)
    }
}
