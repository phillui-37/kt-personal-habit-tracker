package app.rota.ui.habit

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class BatchCategoryTest {
    private val existing = listOf("Health", "Home")

    @Test
    fun blankClearsCategory() {
        assertThat(resolvedBatchCategory(existing, selected = "", typed = "")).isEmpty()
        assertThat(resolvedBatchCategory(existing, selected = "Health", typed = "  ")).isEqualTo("Health")
    }

    @Test
    fun typedNameReplacesSelection() {
        assertThat(resolvedBatchCategory(existing, selected = "Home", typed = "Garden")).isEqualTo("Garden")
    }

    @Test
    fun typedNameReusesExistingSpelling() {
        assertThat(resolvedBatchCategory(existing, selected = "", typed = " health ")).isEqualTo("Health")
    }
}
