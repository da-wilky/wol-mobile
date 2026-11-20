package app.swilk.wolmobile.data

private val macSeparators = Regex("[:.-]")

fun normalizeMac(input: String): String? {
    val hex = input.trim().replace(macSeparators, "")
    if (hex.length != 12 || hex.any { it.digitToIntOrNull(16) == null }) return null
    return hex.uppercase().chunked(2).joinToString(":")
}
