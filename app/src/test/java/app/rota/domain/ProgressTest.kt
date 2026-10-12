package app.rota.domain

import app.rota.domain.model.CheckInStatus
import app.rota.domain.model.Habit
import app.rota.domain.model.HabitType
import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ProgressTest {
    @Test
    fun countReachesTargetAndRotatesOnce() {
        val habit = Habit(id = 1, name = "Water", type = HabitType.COUNT, target = 8)
        val first = Progress.setValue(habit, null, 3, 1, 10, "Core")
        assertThat(first.checkIn.status).isEqualTo(CheckInStatus.LOG)
        assertThat(first.rotationDelta).isEqualTo(0)

        val done = Progress.setValue(habit, first.checkIn, 8, 1, 10, "Core")
        assertThat(done.checkIn.status).isEqualTo(CheckInStatus.DONE)
        assertThat(done.rotationDelta).isEqualTo(1)

        val extra = Progress.setValue(habit, done.checkIn, 9, 1, 10, "Core")
        assertThat(extra.rotationDelta).isEqualTo(0)

        val undo = Progress.setValue(habit, extra.checkIn, 4, 1, 10, "Core")
        assertThat(undo.checkIn.status).isEqualTo(CheckInStatus.LOG)
        assertThat(undo.rotationDelta).isEqualTo(-1)
    }

    @Test
    fun durationUsesMinutesTarget() {
        val habit = Habit(id = 2, name = "Run", type = HabitType.DURATION, target = 30)
        val log = Progress.setValue(habit, null, 12, 1, null, null)
        assertThat(log.checkIn.status).isEqualTo(CheckInStatus.LOG)
        val done = Progress.setValue(habit, log.checkIn, 30, 1, null, null)
        assertThat(done.checkIn.status).isEqualTo(CheckInStatus.DONE)
    }

    @Test
    fun skipDoesNotRotate() {
        val habit = Habit(id = 3, name = "Meditate")
        val skipped = Progress.skip(habit, null, 1, 4, "Legs")
        assertThat(skipped.checkIn.status).isEqualTo(CheckInStatus.SKIPPED)
        assertThat(skipped.rotationDelta).isEqualTo(0)
    }
}
