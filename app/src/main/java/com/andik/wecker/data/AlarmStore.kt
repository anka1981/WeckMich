package com.andik.wecker.data

import android.content.Context
import android.content.SharedPreferences
import com.andik.wecker.model.Alarm
import com.andik.wecker.model.PauseUnit
import com.andik.wecker.model.Recurrence
import com.andik.wecker.model.RepeatType
import com.andik.wecker.model.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.time.DayOfWeek
import java.time.LocalDate

/** Hält Wecker und Einstellungen im Speicher und schreibt sie als JSON in die SharedPreferences. */
object AlarmStore {
    private lateinit var prefs: SharedPreferences
    private val _alarms = MutableStateFlow<List<Alarm>>(emptyList())
    private val _settings = MutableStateFlow(Settings())
    val alarms: StateFlow<List<Alarm>> = _alarms.asStateFlow()
    val settings: StateFlow<Settings> = _settings.asStateFlow()

    @Synchronized
    fun init(context: Context) {
        com.andik.wecker.model.Format.init(context)
        if (::prefs.isInitialized) return
        // Geräteverschlüsselter Speicher: lesbar schon vor dem ersten Entsperren nach einem Neustart,
        // damit Wecker auch nach einem nächtlichen System-Update klingeln.
        prefs = context.applicationContext.createDeviceProtectedStorageContext()
            .getSharedPreferences("wecker", Context.MODE_PRIVATE)
        val raw = prefs.getString("alarms", "[]") ?: "[]"
        unreadableIds = prefs.getStringSet("unreadableIds", emptySet()).orEmpty().mapNotNull { it.toIntOrNull() }.toSet()
        val arr = runCatching { JSONArray(raw) }.getOrNull()
        // Fehlerhafte Daten werden gesichert und in der App gemeldet (siehe [loadProblem]); geplante
        // Auslösungen klingeln dann trotzdem (AlarmReceiver), statt still verworfen zu werden.
        if (arr == null) loadProblem = true
        if (arr == null) {
            // Unlesbare Daten nicht beim nächsten Speichern stillschweigend überschreiben, sondern sichern.
            prefs.edit().putString("alarms_unlesbar_${System.currentTimeMillis()}", raw).commit()
        }
        val lostIds = mutableSetOf<Int>()
        _alarms.value = arr?.let { a ->
            // Einzelne kaputte Einträge überspringen statt alle Wecker zu verlieren – ihre IDs werden
            // gemerkt, damit noch geplante Auslösungen weiter klingeln (siehe AlarmReceiver).
            (0 until a.length()).mapNotNull { i ->
                runCatching { alarmFromJson(a.getJSONObject(i)) }.getOrNull().also {
                    if (it == null) {
                        loadProblem = true
                        runCatching { a.getJSONObject(i).optInt("id", -1) }.getOrNull()?.takeIf { id -> id >= 0 }?.let(lostIds::add)
                    }
                }
            }
        } ?: emptyList()
        if (loadProblem) {
            if (arr != null) prefs.edit().putString("alarms_unlesbar_${System.currentTimeMillis()}", raw).commit()
            unreadableIds = unreadableIds + lostIds
            prefs.edit().putStringSet("unreadableIds", unreadableIds.map { it.toString() }.toSet()).commit()
        }
        maxIdEver = maxOf(prefs.getInt("maxIdEver", 0), _alarms.value.maxOfOrNull { it.id } ?: 0, unreadableIds.maxOrNull() ?: 0)
        _settings.value = Settings(
            snoozeMinutes = prefs.getInt("snoozeMinutes", 10),
            ringMinutes = prefs.getInt("ringMinutes", 10),
            rampSeconds = prefs.getInt("rampSeconds", 0),
            flipToSnooze = prefs.getBoolean("flipToSnooze", true),
        )
    }

    /** Gespeicherte Wecker waren (teilweise) unlesbar. */
    @Volatile var loadProblem = false
        private set

    /** IDs von Weckern, die beim Laden unlesbar waren (dauerhaft gemerkt). */
    @Volatile var unreadableIds: Set<Int> = emptySet()
        private set

    /** Höchste je vergebene ID – neue Wecker bekommen nie die ID eines alten, noch geplanten. */
    private var maxIdEver = 0

    /** Letzter Schreibversuch fehlgeschlagen (z. B. Speicher voll) – die App zeigt einen Hinweis. */
    private val _writeFailed = MutableStateFlow(false)
    val writeFailed: StateFlow<Boolean> = _writeFailed.asStateFlow()

    fun get(id: Int): Alarm? = _alarms.value.firstOrNull { it.id == id }

    @Synchronized
    fun nextId(): Int {
        maxIdEver = maxOf(maxIdEver, _alarms.value.maxOfOrNull { it.id } ?: 0) + 1
        prefs.edit().putInt("maxIdEver", maxIdEver).commit()
        return maxIdEver
    }

    @Synchronized
    fun upsert(alarm: Alarm) {
        val list = _alarms.value.toMutableList()
        val i = list.indexOfFirst { it.id == alarm.id }
        if (i >= 0) list[i] = alarm else list += alarm
        persist(list)
    }

    @Synchronized
    fun replaceAll(list: List<Alarm>) = persist(list)

    @Synchronized
    fun delete(id: Int) = persist(_alarms.value.filterNot { it.id == id })

    fun snoozeMinutesFor(alarm: Alarm?): Int = alarm?.snoozeMinutes ?: _settings.value.snoozeMinutes

    fun rampSecondsFor(alarm: Alarm): Int = alarm.rampSeconds ?: _settings.value.rampSeconds

    fun updateSettings(s: Settings) {
        _settings.value = s
        prefs.edit()
            .putInt("snoozeMinutes", s.snoozeMinutes)
            .putInt("ringMinutes", s.ringMinutes)
            .putInt("rampSeconds", s.rampSeconds)
            .putBoolean("flipToSnooze", s.flipToSnooze)
            .apply()
    }

    private fun persist(list: List<Alarm>) {
        val sorted = list.sortedWith(compareBy({ it.hour }, { it.minute }, { it.id }))
        if (sorted == _alarms.value && !_writeFailed.value) return // nichts geändert – nicht schreiben
        _alarms.value = sorted
        val arr = JSONArray()
        sorted.forEach { arr.put(alarmToJson(it)) }
        // commit statt apply: Empfänger laufen nur kurz, die Änderung muss sicher auf der Platte landen.
        _writeFailed.value = !prefs.edit().putString("alarms", arr.toString()).commit()
    }

    private fun alarmToJson(a: Alarm) = JSONObject().apply {
        put("id", a.id)
        put("hour", a.hour)
        put("minute", a.minute)
        put("enabled", a.enabled)
        put("label", a.label)
        put("ringtone", a.ringtone)
        put("ringtoneName", a.ringtoneName)
        put("snoozeMinutes", a.snoozeMinutes)
        put("rampSeconds", a.rampSeconds)
        put("vibrate", a.vibrate)
        put("snoozeUntil", a.snoozeUntil)
        put("temporary", a.temporary)
        put("scheduledAt", a.scheduledAt)
        put("pausedUntil", a.pausedUntil?.toString())
        put("lastPauseAmount", a.lastPauseAmount)
        put("lastPauseUnit", a.lastPauseUnit.name)
        put("lastRung", a.lastRung?.toString())
        put("lastRungZone", a.lastRungZone)
        val r = a.recurrence
        put("recurrence", JSONObject().apply {
            put("type", r.type.name)
            put("interval", r.interval)
            put("start", r.startDate?.toString())
            put("end", r.endDate?.toString())
            put("weekdays", JSONArray(r.weekdays.map { it.value }))
            put("dayOfMonth", r.dayOfMonth)
            put("ordinals", JSONArray(r.ordinals.toList()))
        })
    }

    private fun JSONObject.stringOrNull(key: String): String? = if (has(key) && !isNull(key)) getString(key) else null
    private fun JSONArray.ints(): List<Int> = (0 until length()).map { getInt(it) }

    private fun alarmFromJson(o: JSONObject): Alarm {
        val r = o.optJSONObject("recurrence") ?: JSONObject()
        return Alarm(
            id = o.getInt("id"),
            hour = o.getInt("hour"),
            minute = o.getInt("minute"),
            enabled = o.optBoolean("enabled", true),
            label = o.optString("label", ""),
            ringtone = o.stringOrNull("ringtone"),
            ringtoneName = o.stringOrNull("ringtoneName"),
            rampSeconds = if (o.has("rampSeconds") && !o.isNull("rampSeconds")) o.getInt("rampSeconds") else null,
            snoozeMinutes = if (o.has("snoozeMinutes") && !o.isNull("snoozeMinutes")) o.getInt("snoozeMinutes") else null,
            vibrate = o.optBoolean("vibrate", true),
            temporary = o.optBoolean("temporary", false),
            scheduledAt = if (o.has("scheduledAt") && !o.isNull("scheduledAt")) o.getLong("scheduledAt") else null,
            pausedUntil = o.stringOrNull("pausedUntil")?.let(LocalDate::parse),
            lastPauseAmount = o.optInt("lastPauseAmount", 2),
            lastRung = o.stringOrNull("lastRung")?.let(java.time.LocalDateTime::parse),
            lastRungZone = o.stringOrNull("lastRungZone"),
            lastPauseUnit = runCatching { PauseUnit.valueOf(o.optString("lastPauseUnit")) }.getOrDefault(PauseUnit.WEEKS),
            snoozeUntil = if (o.has("snoozeUntil") && !o.isNull("snoozeUntil")) o.getLong("snoozeUntil") else null,
            recurrence = Recurrence(
                type = runCatching { RepeatType.valueOf(r.optString("type")) }.getOrDefault(RepeatType.ONCE),
                interval = r.optInt("interval", 1),
                startDate = r.stringOrNull("start")?.let(LocalDate::parse),
                endDate = r.stringOrNull("end")?.let(LocalDate::parse),
                weekdays = (r.optJSONArray("weekdays")?.ints() ?: emptyList()).map(DayOfWeek::of).toSet(),
                dayOfMonth = r.optInt("dayOfMonth", 1),
                ordinals = (r.optJSONArray("ordinals")?.ints() ?: listOf(1)).toSet(),
            ),
        )
    }
}
