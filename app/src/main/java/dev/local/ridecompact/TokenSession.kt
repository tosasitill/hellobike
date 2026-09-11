package dev.local.ridecompact

import org.json.JSONObject

/**
 * Builds a session from pasted material for the token login: a full session JSON, `key=value` lines, or a
 * bare token that reuses the ticket and user identity already stored on this device. City and district codes
 * fall back to the previous session and then to the region resolved on the login page.
 */
object TokenSession {
    fun parse(text: String, previous: JSONObject?, regionCity: String, regionAdCode: String): JSONObject {
        val value = text.trim()
        if (value.isEmpty()) throw IllegalArgumentException("请粘贴 token 或会话内容")
        val pasted = when {
            value.startsWith("{") -> JSONObject(value)
            value.contains('=') -> pairs(value)
            else -> JSONObject().put("token", value)
        }
        val token = pasted.optString("token").trim()
        if (token.length < 16 || token.any { it.isWhitespace() }) throw IllegalArgumentException("token 无效")
        val source = if (pasted.optString("userGuid").isEmpty()) previous else pasted
        val userGuid = source?.optString("userGuid")?.trim().orEmpty()
        if (userGuid.length < 16) throw IllegalArgumentException("只有 token 无法确定用户号：请一并粘贴 ticket 与 userGuid，或先用短信登录一次")
        val city = first("", pasted.optString("cityCode"), source?.optString("cityCode"), regionCity)
        val district = first("", pasted.optString("adCode"), source?.optString("adCode"), regionAdCode)
        if (!city.matches(Regex("[0-9]{3,6}"))) throw IllegalArgumentException("缺少城市编码：请在登录页用定位自动填写，或粘贴完整会话")
        if (!district.matches(Regex("[0-9]{6}"))) throw IllegalArgumentException("缺少 6 位行政区编码：请在登录页用定位自动填写，或粘贴完整会话")
        return JSONObject()
            .put("token", token)
            .put("ticket", first("", pasted.optString("ticket"), source?.optString("ticket")))
            .put("userGuid", userGuid)
            .put("systemCode", "62")
            .put("version", "6.99.71")
            .put("h5Version", "7.0.30")
            .put("cityCode", city)
            .put("adCode", district)
    }

    private fun first(vararg values: String?) = values.firstOrNull { !it.isNullOrEmpty() } ?: ""

    private fun pairs(text: String): JSONObject {
        val result = JSONObject()
        for (raw in text.split('\n', ',', ';')) {
            val line = raw.trim()
            val at = line.indexOfFirst { it == '=' || it == ':' }
            if (at <= 0) continue
            result.put(line.substring(0, at).trim(), line.substring(at + 1).trim())
        }
        return result
    }
}
