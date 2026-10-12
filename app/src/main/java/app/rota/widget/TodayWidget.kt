package app.rota.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import app.rota.di.rota
import app.rota.domain.model.TodayItem
import app.rota.domain.model.TodayStatus
import kotlinx.coroutines.flow.first

private val HABIT_ID = ActionParameters.Key<Long>("habit_id")

class TodayWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(
        setOf(DpSize(110.dp, 40.dp), DpSize(180.dp, 110.dp), DpSize(250.dp, 180.dp)),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val items = context.rota().habitRepository().observeToday().first()
            .filter { it.status == TodayStatus.DUE || it.status == TodayStatus.LOGGED }
        provideContent {
            GlanceTheme {
                WidgetContent(items)
            }
        }
    }
}

@Composable
private fun WidgetContent(items: List<TodayItem>) {
    val size = LocalSize.current
    val compact = size.height < 80.dp
    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(GlanceTheme.colors.background)
            .padding(12.dp),
    ) {
        Text(
            text = if (items.isEmpty()) "All clear" else "${items.size} due",
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontWeight = FontWeight.Medium,
                fontSize = 14.sp,
            ),
        )
        if (compact) {
            val next = items.firstOrNull()
            if (next != null) {
                Spacer(GlanceModifier.height(4.dp))
                Text(
                    text = listOfNotNull(next.habit.name, next.routineLabel).joinToString(" · "),
                    style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp),
                    maxLines = 1,
                )
            }
        } else {
            Spacer(GlanceModifier.height(8.dp))
            items.take(5).forEach { item ->
                Row(
                    modifier = GlanceModifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp)
                        .clickable(
                            actionRunCallback<CompleteHabitAction>(
                                actionParametersOf(HABIT_ID to item.habit.id),
                            ),
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (item.status == TodayStatus.DONE) "✓" else "○",
                        style = TextStyle(color = GlanceTheme.colors.primary, fontSize = 14.sp),
                    )
                    Spacer(GlanceModifier.width(8.dp))
                    Column {
                        Text(
                            text = item.habit.name,
                            style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 13.sp),
                            maxLines = 1,
                        )
                        item.routineLabel?.let {
                            Text(
                                text = it,
                                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 11.sp),
                                maxLines = 1,
                            )
                        }
                    }
                }
            }
        }
    }
}

class CompleteHabitAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        val habitId = parameters[HABIT_ID] ?: return
        context.rota().habitRepository().toggleCheck(habitId)
        TodayWidget().update(context, glanceId)
    }
}

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()
}
