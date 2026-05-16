package com.garuna.iahybridcoach.ui.calendario

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.garuna.iahybridcoach.data.workouts.Workout
import com.garuna.iahybridcoach.ui.entrenamientos.AddWorkoutSheet
import com.kizitonwose.calendar.compose.HorizontalCalendar
import com.kizitonwose.calendar.compose.rememberCalendarState
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.util.Locale

private val ES_LOCALE = Locale("es", "ES")

/* CLAUDE CODE:
 * Pantalla de Calendario. SegmentedButton arriba para alternar Mes/Semana.
 *
 * Mes: cada celda es un dia; los dias con entrenos tienen el fondo tintado
 * con primaryContainer para que destaquen claramente.
 *
 * Semana: cada celda es ancha y alta, con dia + numero arriba y los chips
 * de los entrenos del dia apilados debajo.
 *
 * FAB siempre disponible; usa el dia seleccionado como fecha pre-rellenada.
 */
@Composable
fun CalendarioScreen(
    modifier: Modifier = Modifier,
    viewModel: CalendarioViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsState()
    var selectedDate by remember { mutableStateOf(LocalDate.now()) }
    var viewMode by remember { mutableStateOf(CalendarViewMode.Mes) }
    var showAddSheet by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        when (val state = uiState) {
            CalendarioUiState.Loading -> Centered { CircularProgressIndicator() }
            is CalendarioUiState.Error -> Centered {
                Text(
                    text = state.message,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
            is CalendarioUiState.Ready -> CalendarReadyContent(
                workoutsByDate = state.workoutsByDate,
                selectedDate = selectedDate,
                onDateSelected = { selectedDate = it },
                viewMode = viewMode,
                onViewModeChange = { viewMode = it }
            )
        }

        FloatingActionButton(
            onClick = { showAddSheet = true },
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = "Anyadir entrenamiento"
            )
        }
    }

    if (showAddSheet) {
        AddWorkoutSheet(
            initialFechaMillis = startOfDayMillis(selectedDate),
            onDismiss = { showAddSheet = false },
            onSave = { workout ->
                viewModel.addWorkout(workout)
                showAddSheet = false
            }
        )
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(32.dp),
        contentAlignment = Alignment.Center
    ) { content() }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CalendarReadyContent(
    workoutsByDate: Map<LocalDate, List<Workout>>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit,
    viewMode: CalendarViewMode,
    onViewModeChange: (CalendarViewMode) -> Unit
) {
    Column(modifier = Modifier.fillMaxSize()) {
        Spacer(modifier = Modifier.height(8.dp))

        SingleChoiceSegmentedButtonRow(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
        ) {
            CalendarViewMode.values().forEachIndexed { index, mode ->
                SegmentedButton(
                    selected = viewMode == mode,
                    onClick = { onViewModeChange(mode) },
                    shape = SegmentedButtonDefaults.itemShape(
                        index = index,
                        count = CalendarViewMode.values().size
                    )
                ) {
                    Text(mode.label)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        when (viewMode) {
            CalendarViewMode.Mes -> MonthCalendar(
                workoutsByDate = workoutsByDate,
                selectedDate = selectedDate,
                onDateSelected = onDateSelected
            )
            CalendarViewMode.Semana -> WeekCalendarView(
                workoutsByDate = workoutsByDate,
                selectedDate = selectedDate,
                onDateSelected = onDateSelected
            )
        }
    }
}

// ---- Vista Mes ----

@Composable
private fun MonthCalendar(
    workoutsByDate: Map<LocalDate, List<Workout>>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    val currentMonth = remember { YearMonth.now() }
    val startMonth = remember { currentMonth.minusMonths(12) }
    val endMonth = remember { currentMonth.plusMonths(12) }
    val firstDayOfWeek = remember { DayOfWeek.MONDAY }
    val daysOfWeek = remember { daysOfWeek(firstDayOfWeek = firstDayOfWeek) }

    val state = rememberCalendarState(
        startMonth = startMonth,
        endMonth = endMonth,
        firstVisibleMonth = currentMonth,
        firstDayOfWeek = firstDayOfWeek
    )
    val scope = rememberCoroutineScope()

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
        MonthHeader(
            visibleMonth = state.firstVisibleMonth.yearMonth,
            onPrev = {
                scope.launch {
                    state.animateScrollToMonth(state.firstVisibleMonth.yearMonth.minusMonths(1))
                }
            },
            onNext = {
                scope.launch {
                    state.animateScrollToMonth(state.firstVisibleMonth.yearMonth.plusMonths(1))
                }
            }
        )

        DaysOfWeekRow(daysOfWeek = daysOfWeek)

        HorizontalCalendar(
            state = state,
            dayContent = { day ->
                MonthDayCell(
                    day = day,
                    hasWorkouts = workoutsByDate[day.date]?.isNotEmpty() == true,
                    isSelected = day.date == selectedDate,
                    onClick = {
                        if (day.position == DayPosition.MonthDate) {
                            onDateSelected(day.date)
                        }
                    }
                )
            }
        )
    }
}

@Composable
private fun MonthDayCell(
    day: CalendarDay,
    hasWorkouts: Boolean,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isToday = day.date == LocalDate.now()
    val isInCurrentMonth = day.position == DayPosition.MonthDate

    /* CLAUDE CODE: prioridad de fondo:
     *   seleccionado -> primary (fuerte)
     *   tiene entrenos -> primaryContainer (tinte suave)
     *   resto -> transparente
     * Asi de un vistazo se ve que dias has entrenado.
     */
    val backgroundColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        hasWorkouts && isInCurrentMonth -> MaterialTheme.colorScheme.primaryContainer
        else -> Color.Transparent
    }
    val textColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        hasWorkouts && isInCurrentMonth -> MaterialTheme.colorScheme.onPrimaryContainer
        !isInCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.4f)
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .clip(CircleShape)
            .background(backgroundColor)
            .then(
                if (isToday && !isSelected) {
                    Modifier.border(
                        width = 1.5.dp,
                        color = MaterialTheme.colorScheme.primary,
                        shape = CircleShape
                    )
                } else Modifier
            )
            .clickable(enabled = isInCurrentMonth, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = day.date.dayOfMonth.toString(),
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            fontWeight = if (isToday || hasWorkouts) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}

// ---- Vista Semana ----

/* CLAUDE CODE:
 * Vista de Semana en lista VERTICAL: lunes arriba, domingo abajo. Cada fila
 * tiene a la izquierda el dia (nombre + numero) y a la derecha los entrenos
 * del dia. Asi caben descripciones legibles en lugar de chips minusculos.
 *
 * Implementacion propia (sin WeekCalendar de la libreria) porque la libreria
 * solo ofrece layout horizontal. El estado de "semana visible" es un simple
 * LocalDate del lunes de esa semana.
 */
@Composable
private fun WeekCalendarView(
    workoutsByDate: Map<LocalDate, List<Workout>>,
    selectedDate: LocalDate,
    onDateSelected: (LocalDate) -> Unit
) {
    var weekStart by remember(selectedDate) {
        mutableStateOf(selectedDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)))
    }
    val weekEnd = weekStart.plusDays(6)

    Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        WeekHeader(
            start = weekStart,
            end = weekEnd,
            onPrev = { weekStart = weekStart.minusWeeks(1) },
            onNext = { weekStart = weekStart.plusWeeks(1) }
        )

        Spacer(modifier = Modifier.height(8.dp))

        // 7 filas, una por dia desde lunes
        for (offset in 0..6) {
            val date = weekStart.plusDays(offset.toLong())
            WeekDayRow(
                date = date,
                workouts = workoutsByDate[date].orEmpty(),
                isSelected = date == selectedDate,
                onClick = { onDateSelected(date) }
            )
            if (offset < 6) Spacer(modifier = Modifier.height(6.dp))
        }
    }
}

@Composable
private fun WeekHeader(
    start: LocalDate,
    end: LocalDate,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Semana anterior"
            )
        }
        Text(
            text = formatWeekRange(start, end),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Semana siguiente"
            )
        }
    }
}

@Composable
private fun WeekDayRow(
    date: LocalDate,
    workouts: List<Workout>,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    val isToday = date == LocalDate.now()

    val borderColor = when {
        isSelected -> MaterialTheme.colorScheme.primary
        isToday -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.outlineVariant
    }
    val borderWidth = if (isSelected || isToday) 1.5.dp else 1.dp
    val backgroundColor = if (isSelected) {
        MaterialTheme.colorScheme.primaryContainer
    } else Color.Transparent

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 60.dp)
            .clip(RoundedCornerShape(8.dp))
            .border(
                width = borderWidth,
                color = borderColor,
                shape = RoundedCornerShape(8.dp)
            )
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Bloque izquierda: dia + numero
        Column(
            modifier = Modifier.width(56.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = date.dayOfWeek.getDisplayName(TextStyle.SHORT, ES_LOCALE)
                    .uppercase(ES_LOCALE),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Bloque derecha: entrenos del dia
        Column(modifier = Modifier.weight(1f)) {
            if (workouts.isEmpty()) {
                Text(
                    text = "Sin entreno",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                workouts.forEachIndexed { index, workout ->
                    WeekWorkoutItem(workout = workout)
                    if (index < workouts.lastIndex) {
                        Spacer(modifier = Modifier.height(2.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun WeekWorkoutItem(workout: Workout) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = workout.tipoEnum.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary
        )
        if (workout.descripcion.isNotBlank()) {
            Text(
                text = workout.descripcion,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ---- Helpers ----

@Composable
private fun MonthHeader(
    visibleMonth: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onPrev) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                contentDescription = "Mes anterior"
            )
        }
        Text(
            text = formatYearMonth(visibleMonth),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )
        IconButton(onClick = onNext) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = "Mes siguiente"
            )
        }
    }
}

@Composable
private fun DaysOfWeekRow(daysOfWeek: List<DayOfWeek>) {
    Row(modifier = Modifier.fillMaxWidth()) {
        daysOfWeek.forEach { dow ->
            Text(
                text = dow.getDisplayName(TextStyle.SHORT, ES_LOCALE).uppercase(ES_LOCALE),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun startOfDayMillis(date: LocalDate): Long {
    return date.atStartOfDay(ZoneId.systemDefault())
        .toInstant()
        .toEpochMilli()
}

private val yearMonthFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("MMMM yyyy", ES_LOCALE)

private fun formatYearMonth(ym: YearMonth): String {
    return ym.atDay(1).format(yearMonthFormatter)
        .replaceFirstChar { it.uppercase(ES_LOCALE) }
}

private val weekDayFormatter: DateTimeFormatter =
    DateTimeFormatter.ofPattern("d MMM", ES_LOCALE)

private fun formatWeekRange(start: LocalDate, end: LocalDate): String {
    // CLAUDE CODE: "11 - 17 may 2026" o, si cruza mes/anyo,
    // "30 may - 5 jun 2026". Mantenemos un anyo solo al final.
    val startStr = if (start.year == end.year && start.month == end.month) {
        start.dayOfMonth.toString()
    } else {
        start.format(weekDayFormatter)
    }
    val endStr = end.format(weekDayFormatter)
    return "$startStr - $endStr ${end.year}"
}

private enum class CalendarViewMode(val label: String) {
    Mes("Mes"),
    Semana("Semana")
}
