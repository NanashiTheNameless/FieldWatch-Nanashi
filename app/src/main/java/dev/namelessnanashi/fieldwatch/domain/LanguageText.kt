package dev.namelessnanashi.fieldwatch.domain

import java.util.Locale

/**
 * The one lookup. Compose and the domain layer both call this.
 * English only: every key resolves to its compiled sentence.
 */
object LanguageText {
    private val token = Regex("\\{([A-Za-z][A-Za-z0-9_]*)\\}")

    /** Sit names and share timestamps. English only, so always Locale.US. */
    fun dateLocale(): Locale = Locale.US

    fun text(key: String): String = EnglishCopy.byKey[key]?.text ?: key

    fun text(key: String, values: Map<String, String>): String {
        val english = EnglishCopy.byKey[key]?.text ?: return key
        val needed = EnglishCopy.placeholders[key].orEmpty()
        if (needed.any { it !in values }) return english
        return fill(english, values)
    }

    fun fill(template: String, values: Map<String, String>): String {
        val needed = placeholderNames(template)
        if (needed.any { it !in values }) return template
        val out = StringBuilder(template.length)
        var i = 0
        while (i < template.length) {
            val start = template.indexOf('{', i)
            if (start < 0) {
                out.append(template, i, template.length)
                break
            }
            out.append(template, i, start)
            val match = token.find(template, start)
            if (match == null || match.range.first != start) {
                out.append(template[start])
                i = start + 1
                continue
            }
            val name = match.groupValues[1]
            if (name in values) out.append(values.getValue(name)) else out.append(match.value)
            i = match.range.last + 1
        }
        return out.toString()
    }
}

fun placeholderNames(text: String): Set<String> {
    val names = LinkedHashSet<String>()
    val re = Regex("\\{([A-Za-z][A-Za-z0-9_]*)\\}")
    for (match in re.findAll(text)) names.add(match.groupValues[1])
    return names
}
