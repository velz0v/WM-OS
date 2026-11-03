package dev.wmos.data.prefs

import android.content.Context
import org.json.JSONObject

/**
 * Recoverable drafts.
 *
 * Autosave writes to the database first; this store is a second, cheap safety net for the case
 * where the process dies between two saves. A draft is only offered to the user when it is
 * newer than the stored row, and it is discarded as soon as the editor saves successfully.
 */
class DraftStore(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("wm_drafts", Context.MODE_PRIVATE)

    data class Draft(val title: String, val content: String, val savedAt: Long)

    fun save(itemId: String, title: String, content: String) {
        val payload = JSONObject().apply {
            put("title", title)
            put("content", content)
            put("savedAt", System.currentTimeMillis())
        }
        prefs.edit().putString(key(itemId), payload.toString()).apply()
    }

    fun read(itemId: String): Draft? {
        val raw = prefs.getString(key(itemId), null) ?: return null
        return runCatching {
            val json = JSONObject(raw)
            Draft(
                title = json.optString("title", ""),
                content = json.optString("content", ""),
                savedAt = json.optLong("savedAt", 0L),
            )
        }.getOrNull()
    }

    fun clear(itemId: String) {
        prefs.edit().remove(key(itemId)).apply()
    }

    private fun key(itemId: String) = "draft_$itemId"
}
