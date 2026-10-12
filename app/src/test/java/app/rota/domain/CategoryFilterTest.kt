package app.rota.domain

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class CategoryFilterTest {
    @Test
    fun unsetMatchesEveryCategory() {
        assertThat(CategoryFilter.matches("Health", null)).isTrue()
        assertThat(CategoryFilter.matches("", null)).isTrue()
    }

    @Test
    fun matchesIgnoreCaseAndBlankBucket() {
        assertThat(CategoryFilter.matches("Health", "health")).isTrue()
        assertThat(CategoryFilter.matches("  ", "")).isTrue()
        assertThat(CategoryFilter.matches("Health", "")).isFalse()
    }

    @Test
    fun hidesFilterWhenOnlyOneBucket() {
        assertThat(CategoryFilter.visible(listOf("Health"), hasUncategorized = false)).isFalse()
        assertThat(CategoryFilter.visible(emptyList(), hasUncategorized = true)).isFalse()
        assertThat(CategoryFilter.visible(listOf("Health"), hasUncategorized = true)).isTrue()
        assertThat(CategoryFilter.visible(listOf("Health", "Home"), hasUncategorized = false)).isTrue()
    }

    @Test
    fun dropsSelectionThatIsGone() {
        assertThat(CategoryFilter.effective("Gone", listOf("Health"), hasUncategorized = false)).isNull()
        assertThat(CategoryFilter.effective("", listOf("Health"), hasUncategorized = false)).isNull()
        assertThat(CategoryFilter.effective("", listOf("Health"), hasUncategorized = true)).isEmpty()
        assertThat(CategoryFilter.effective("health", listOf("Health"), hasUncategorized = false)).isEqualTo("Health")
    }

    @Test
    fun namedCategoriesDedupeCase() {
        assertThat(CategoryFilter.named(listOf("Health", " health ", "", "Home")))
            .containsExactly("Health", "Home")
            .inOrder()
    }
}
