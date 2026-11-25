package com.gabriel.campuslostandfound.validation

internal val itemStatuses = listOf("LOST", "FOUND", "RESOLVED")

internal data class ItemDraft(
    val title: String = "",
    val description: String = "",
    val location: String = "",
    val date: String = "",
    val status: String = "LOST",
    val categoryId: String = ""
)

internal data class ItemErrors(
    val title: String? = null,
    val description: String? = null,
    val location: String? = null,
    val date: String? = null,
    val status: String? = null,
    val category: String? = null
) {
    val isValid: Boolean
        get() = title == null && description == null && location == null &&
            date == null && status == null && category == null
}

private val itemDatePattern = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}")

internal fun validateItem(draft: ItemDraft, categoryIds: Set<String>): ItemErrors =
    ItemErrors(
        title = "Enter an item title.".takeIf { draft.title.isBlank() },
        description = "Enter a description for a found item.".takeIf {
            draft.status == "FOUND" && draft.description.isBlank()
        },
        location = "Enter a location.".takeIf { draft.location.isBlank() },
        date = "Enter a valid date (YYYY-MM-DD).".takeUnless { isValidItemDate(draft.date) },
        status = "Choose a valid status.".takeUnless { draft.status in itemStatuses },
        category = "Choose a category.".takeUnless { draft.categoryId in categoryIds }
    )

internal fun isValidItemDate(value: String): Boolean {
    val date = value.trim()
    if (!itemDatePattern.matches(date)) return false

    val (year, month, day) = date.split('-').map { it.toInt() }
    if (year == 0 || month !in 1..12) return false

    val leapYear = year % 400 == 0 || (year % 4 == 0 && year % 100 != 0)
    val daysInMonth = when (month) {
        2 -> if (leapYear) 29 else 28
        4, 6, 9, 11 -> 30
        else -> 31
    }
    return day in 1..daysInMonth
}
