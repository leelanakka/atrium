package ch.tutteli.atrium.translations

import ch.tutteli.atrium.assertions.DescriptiveAssertion
import ch.tutteli.atrium.reporting.translating.StringBasedTranslatable

/**
 * Contains the [DescriptiveAssertion.description]s of the assertion functions
 * which are applicable to date like instances (e.g. LocalDate, LocaleDateTime, ZonedDateTime etc.)
 */
enum class DescriptionDateTimeLikeAssertion(override val value: String) : StringBasedTranslatable {
    YEAR("year"),
    MONTH("month"),
    DAY("day"),
    DAY_OF_WEEK("day of week")
}
