package net.notscape.browser.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * Pure (Android-free) JSON helpers for the store's sidecar files and
 * its crash-recovery choice. Kept separate from AppStore so the
 * recovery and round-trip logic is unit-testable on the JVM.
 */
object StoreJson {

    /** First parseable JSON document of [main], then [bak]; null when
     *  neither parses. This is the store's corruption-recovery rule:
     *  a damaged main document never shadows a good backup. */
    fun pickReadable(main: String?, bak: String?): String? {
        if (main != null && runCatching { JSONObject(main) }.isSuccess) return main
        if (bak != null && runCatching { JSONObject(bak) }.isSuccess) return bak
        return null
    }

    fun historyToJson(entries: List<HistoryEntry>): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("history", JSONArray().apply {
            entries.forEach {
                put(JSONObject().put("id", it.id).put("title", it.title)
                    .put("url", it.url).put("t", it.visitedAt))
            }
        })
        return root.toString()
    }

    /** Null when [text] is not a readable history document. */
    fun parseHistory(text: String): List<HistoryEntry>? = runCatching {
        val arr = JSONObject(text).getJSONArray("history")
        val out = ArrayList<HistoryEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            out.add(HistoryEntry(o.getLong("id"), o.getString("title"), o.getString("url"), o.getLong("t")))
        }
        out
    }.getOrNull()

    fun positionsToJson(map: Map<String, Long>): String {
        val root = JSONObject()
        root.put("schemaVersion", 1)
        root.put("positions", JSONObject().apply {
            map.forEach { (k, v) -> put(k, v) }
        })
        return root.toString()
    }

    /** Null when [text] is not a readable positions document. */
    fun parsePositions(text: String): Map<String, Long>? = runCatching {
        val obj = JSONObject(text).getJSONObject("positions")
        val out = LinkedHashMap<String, Long>()
        val keys = obj.keys()
        while (keys.hasNext()) {
            val k = keys.next()
            out[k] = obj.optLong(k, 0)
        }
        out
    }.getOrNull()
}
