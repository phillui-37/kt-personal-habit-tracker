package app.rota.domain

object CategoryFilter {
    fun matches(category: String, selected: String?): Boolean {
        if (selected == null) return true
        return category.trim().equals(selected, ignoreCase = true)
    }

    fun named(categories: Iterable<String>): List<String> =
        categories
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinctBy { it.lowercase() }
            .sortedBy { it.lowercase() }

    fun visible(named: List<String>, hasUncategorized: Boolean): Boolean =
        named.size + (if (hasUncategorized) 1 else 0) > 1

    fun effective(selected: String?, named: List<String>, hasUncategorized: Boolean): String? = when {
        selected == null -> null
        selected.isEmpty() -> if (hasUncategorized) "" else null
        else -> named.firstOrNull { it.equals(selected, ignoreCase = true) }
    }
}
