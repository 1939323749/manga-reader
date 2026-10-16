package mangareader.api

import android.app.Application
import androidx.preference.CheckBoxPreference
import androidx.preference.DialogPreference
import androidx.preference.EditTextPreference
import androidx.preference.ListPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceScreen
import androidx.preference.TwoStatePreference
import eu.kanade.tachiyomi.source.CatalogueSource
import eu.kanade.tachiyomi.source.ConfigurableSource
import eu.kanade.tachiyomi.source.model.Filter
import eu.kanade.tachiyomi.source.model.FilterList
import eu.kanade.tachiyomi.source.sourcePreferences
import io.github.oshai.kotlinlogging.KotlinLogging
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.boolean
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import uy.kohesive.injekt.Injekt
import uy.kohesive.injekt.api.get

/**
 * Search filters and source preferences. Filters are addressed by their position in
 * `getFilterList()` (and in the group for group children); a fresh list is built for every
 * request, so positions are stable as long as the source builds the same list.
 * Preferences follow Suwayomi-Server's approach: build a PreferenceScreen through the
 * AndroidCompat shims and read/write the source's SharedPreferences.
 */
object SourceSettings {
    private val logger = KotlinLogging.logger {}

    // ---- filters ----------------------------------------------------------------------

    fun filterList(source: CatalogueSource): FilterList =
        runCatching { source.getFilterList() }
            .onFailure { logger.warn(it) { "${source.name}: getFilterList failed" } }
            .getOrDefault(FilterList())

    fun filters(source: CatalogueSource): List<FilterDto> = filterList(source).mapIndexed { i, f -> f.toDto(i) }

    private fun Any?.toDto(index: Int): FilterDto {
        val f = this as? Filter<*> ?: return FilterDto(index, "unknown", this.toString())
        return when (f) {
            is Filter.Header -> FilterDto(index, "header", f.name)
            is Filter.Separator -> FilterDto(index, "separator", f.name)
            is Filter.Text -> FilterDto(index, "text", f.name, JsonPrimitive(f.state))
            is Filter.CheckBox -> FilterDto(index, "checkbox", f.name, JsonPrimitive(f.state))
            is Filter.TriState -> FilterDto(index, "tristate", f.name, JsonPrimitive(f.state))
            is Filter.Select<*> -> FilterDto(index, "select", f.name, JsonPrimitive(f.state), values = f.displayValues)
            is Filter.Sort ->
                FilterDto(
                    index,
                    "sort",
                    f.name,
                    f.state?.let { JsonObject(mapOf("index" to JsonPrimitive(it.index), "ascending" to JsonPrimitive(it.ascending))) }
                        ?: JsonNull,
                    values = f.values.toList(),
                )
            is Filter.Group<*> -> FilterDto(index, "group", f.name, filters = f.state.mapIndexed { i, c -> c.toDto(i) })
            else -> FilterDto(index, "unknown", f.name)
        }
    }

    /** Applies the client's changes to a freshly built list. */
    fun applyFilters(
        list: FilterList,
        changes: List<FilterChangeDto>,
    ) {
        for (change in changes) {
            val top = list.getOrNull(change.index) ?: throw IllegalArgumentException("No filter at ${change.index}")
            val target =
                if (change.childIndex == null) {
                    top
                } else {
                    (top as? Filter.Group<*>)?.state?.getOrNull(change.childIndex) as? Filter<*>
                        ?: throw IllegalArgumentException("No filter at ${change.index}/${change.childIndex}")
                }
            try {
                setState(target, change.state)
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: Exception) {
                throw IllegalArgumentException("Bad state for filter \"${target.name}\": ${e.message}")
            }
        }
    }

    @Suppress("UNCHECKED_CAST")
    private fun setState(
        filter: Filter<*>,
        state: JsonElement,
    ) {
        val f = filter as Filter<Any?>
        when (filter) {
            is Filter.Text -> f.state = state.jsonPrimitive.contentOrNull.orEmpty()
            is Filter.CheckBox -> f.state = state.jsonPrimitive.boolean
            is Filter.TriState -> f.state = state.jsonPrimitive.int.coerceIn(Filter.TriState.STATE_IGNORE, Filter.TriState.STATE_EXCLUDE)
            is Filter.Select<*> -> {
                val i = state.jsonPrimitive.int
                require(i in filter.values.indices) { "Option $i out of range for \"${filter.name}\"" }
                f.state = i
            }
            is Filter.Sort ->
                f.state =
                    if (state is JsonNull) {
                        null
                    } else {
                        val obj = state.jsonObject
                        val i = obj["index"]!!.jsonPrimitive.int
                        require(i in filter.values.indices) { "Option $i out of range for \"${filter.name}\"" }
                        Filter.Sort.Selection(i, obj["ascending"]?.jsonPrimitive?.booleanOrNull ?: false)
                    }
            else -> Unit // headers, separators, groups (their children are set individually)
        }
    }

    // ---- preferences ------------------------------------------------------------------

    private fun screen(source: CatalogueSource): PreferenceScreen? {
        if (source !is ConfigurableSource) return null
        val screen = PreferenceScreen(Injekt.get<Application>())
        screen.sharedPreferences = source.sourcePreferences()
        source.setupPreferenceScreen(screen)
        return screen
    }

    fun preferences(source: CatalogueSource): SourcePreferencesDto {
        val screen = screen(source) ?: return SourcePreferencesDto(configurable = false, preferences = emptyList())
        return SourcePreferencesDto(configurable = true, preferences = screen.preferences.map { it.toDto() })
    }

    private fun Preference.toDto(): PreferenceDto {
        val type =
            when (this) {
                is ListPreference -> "list"
                is MultiSelectListPreference -> "multiselect"
                is EditTextPreference -> "text"
                is CheckBoxPreference -> "checkbox"
                is TwoStatePreference -> "switch"
                else -> "other"
            }
        val (entries, entryValues) =
            when (this) {
                is ListPreference -> entries to entryValues
                is MultiSelectListPreference -> entries to entryValues
                else -> null to null
            }
        val dialog = this as? DialogPreference
        return PreferenceDto(
            key = key,
            type = type,
            title = title?.toString(),
            summary = runCatching { summary?.toString() }.getOrNull(),
            enabled = isEnabled,
            visible = visible,
            entries = entries?.map { it.toString() },
            entryValues = entryValues?.map { it.toString() },
            dialogTitle = dialog?.dialogTitle?.toString(),
            dialogMessage = dialog?.dialogMessage?.toString(),
            value = runCatching { currentValue.toJson() }.getOrDefault(JsonNull),
            defaultValue = defaultValue.toJson(),
        )
    }

    private fun Any?.toJson(): JsonElement =
        when (this) {
            null -> JsonNull
            is Boolean -> JsonPrimitive(this)
            is Number -> JsonPrimitive(this)
            is Collection<*> -> JsonArray(map { JsonPrimitive(it?.toString()) })
            is Array<*> -> JsonArray(map { JsonPrimitive(it?.toString()) })
            else -> JsonPrimitive(toString())
        }

    /** Saves one value. The source must be re-created afterwards for it to take effect. */
    fun setPreference(
        source: CatalogueSource,
        change: PreferenceChangeDto,
    ) {
        val screen = screen(source) ?: throw IllegalArgumentException("${source.name} has no settings")
        val pref =
            screen.preferences.firstOrNull { it.key == change.key }
                ?: throw NoSuchElementException("${source.name} has no setting \"${change.key}\"")
        val value: Any? =
            try {
                when (pref.defaultValueType) {
                    "String" -> change.value.takeUnless { it is JsonNull }?.jsonPrimitive?.content
                    "Boolean" -> change.value.jsonPrimitive.boolean
                    "Set<String>" -> change.value.jsonArray.map { it.jsonPrimitive.content }.toSet()
                    else -> throw IllegalArgumentException("Unsupported setting type ${pref.defaultValueType}")
                }
            } catch (e: IllegalArgumentException) {
                throw e
            } catch (e: Exception) {
                throw IllegalArgumentException("Bad value for \"${change.key}\": ${e.message}")
            }
        if (pref is ListPreference && value != null && pref.entryValues != null) {
            require(pref.findIndexOfValue(value as String) >= 0) { "\"$value\" is not an option of \"${change.key}\"" }
        }
        // Like Android: the listener may veto the change. Listeners that only show a Toast
        // or persist the value themselves are fine; one that crashes shouldn't block saving.
        val accepted =
            runCatching { pref.callChangeListener(value) }
                .onFailure { logger.warn(it) { "${source.name}: change listener for ${change.key} failed" } }
                .getOrDefault(true)
        if (!accepted) throw IllegalArgumentException("${source.name} rejected the value for \"${change.key}\"")
        pref.saveNewValue(value)
    }
}
