package app.rota.data.prefs

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class RoutineTemplateTest {
    @Test
    fun roundTrip() {
        val templates = listOf(RoutineTemplate(id = "1", name = "Easy strength", items = listOf("A", "B")))
        assertThat(RoutineTemplate.decode(RoutineTemplate.encode(templates))).isEqualTo(templates)
    }

    @Test
    fun corruptOrEmptyJsonIsEmpty() {
        assertThat(RoutineTemplate.decode(null)).isEmpty()
        assertThat(RoutineTemplate.decode("")).isEmpty()
        assertThat(RoutineTemplate.decode("{")).isEmpty()
    }
}
