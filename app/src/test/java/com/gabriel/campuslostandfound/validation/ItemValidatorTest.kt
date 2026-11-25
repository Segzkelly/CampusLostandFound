package com.gabriel.campuslostandfound.validation

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ItemValidatorTest {
    private val categoryIds = setOf("other")
    private val valid = ItemDraft(
        title = "Black umbrella",
        location = "Library",
        date = "2026-11-24",
        categoryId = "other"
    )

    @Test
    fun lostItemAllowsBlankDescription() {
        assertTrue(validateItem(valid, categoryIds).isValid)
    }

    @Test
    fun foundItemRequiresNonBlankDescription() {
        val found = valid.copy(status = "FOUND", description = "  ")
        val errors = validateItem(found, categoryIds)
        assertFalse(errors.isValid)
        assertNotNull(errors.description)
        assertTrue(validateItem(found.copy(description = "Folding umbrella"), categoryIds).isValid)
    }

    @Test
    fun blankTitleAndLocationAreRejected() {
        val errors = validateItem(valid.copy(title = " ", location = "\t"), categoryIds)
        assertNotNull(errors.title)
        assertNotNull(errors.location)
    }

    @Test
    fun dateMustHaveValidCalendarDayAndIsoFormat() {
        assertTrue(isValidItemDate("2024-02-29"))
        assertTrue(isValidItemDate(" 2026-11-24 "))
        for (invalid in listOf("2026-02-29", "2024-04-31", "2026-11-00", "0000-01-01", "24/11/2026")) {
            assertFalse(invalid, isValidItemDate(invalid))
        }
    }

    @Test
    fun unknownStatusAndCategoryAreRejected() {
        val errors = validateItem(valid.copy(status = "OTHER", categoryId = "missing"), categoryIds)
        assertNotNull(errors.status)
        assertNotNull(errors.category)
    }

    @Test
    fun credentialsValidationCanRunWithoutAuthSdk() {
        assertNotNull(validateCredentials(" ", "secret", registering = false))
        assertNotNull(validateCredentials("student@example.test", "short", registering = true))
        assertNull(validateCredentials("student@example.test", "secret", registering = true))
    }
}
