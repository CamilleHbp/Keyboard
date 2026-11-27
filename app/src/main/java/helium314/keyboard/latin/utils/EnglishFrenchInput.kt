package helium314.keyboard.latin.utils

import android.content.Context
import android.content.SharedPreferences
import android.view.inputmethod.InputMethodSubtype
import androidx.core.content.edit
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.settings.Defaults
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import java.util.Locale

/** A distinct input, available while both of its standalone languages are enabled. */
object EnglishFrenchInput {
    const val PREF_SUBTYPE = "english_french_input"
    private const val PREF_MIGRATED = "separate_english_french_input_migrated"

    val defaultSubtype: SettingsSubtype get() = SettingsSubtype(Locale.FRENCH, "")
        .withLayout(LayoutType.MAIN, "qwerty")
        .with(ExtraValue.SECONDARY_LOCALES, Locale.US.toLanguageTag())
        .with(ExtraValue.ENGLISH_FRENCH_INPUT)

    fun isCombined(subtype: InputMethodSubtype) = subtype.containsExtraValueKey(ExtraValue.ENGLISH_FRENCH_INPUT)
    fun isCombined(subtype: SettingsSubtype) = subtype.hasExtraValueOf(ExtraValue.ENGLISH_FRENCH_INPUT)

    fun isAvailable(subtypes: List<InputMethodSubtype>): Boolean =
        subtypes.any { !isCombined(it) && it.locale().language == "en" } &&
            subtypes.any { !isCombined(it) && it.locale().language == "fr" }

    fun read(prefs: SharedPreferences): SettingsSubtype =
        (prefs.getString(PREF_SUBTYPE, null)?.toSettingsSubtype() ?: defaultSubtype)
            .copy(locale = Locale.FRENCH)
            .with(ExtraValue.ENGLISH_FRENCH_INPUT)
            .with(ExtraValue.SECONDARY_LOCALES, Locale.US.toLanguageTag())

    fun update(context: Context, from: SettingsSubtype, to: SettingsSubtype) {
        val prefs = context.prefs()
        prefs.edit {
            putString(PREF_SUBTYPE, to.toPref())
            if (isCombined(SubtypeSettings.getSelectedSubtype(prefs)))
                putString(Settings.PREF_SELECTED_SUBTYPE, to.toPref())
            prefs.all.filterKeys { it.startsWith(Settings.PREF_SAVED_APP_SUBTYPE_PREFIX) }
                .filterValues { it == from.toPref() }.forEach { putString(it.key, to.toPref()) }
        }
        SubtypeSettings.reloadEnabledSubtypes(context)
    }

    /** Split the old per-layout bilingual option without losing the user's active input. */
    fun migrate(prefs: SharedPreferences) {
        if (prefs.getBoolean(PREF_MIGRATED, false)) return
        fun legacy(subtype: SettingsSubtype): Boolean {
            if (isCombined(subtype)) return false
            val secondary = getSecondaryLocales(subtype.extraValues)
            return secondary.size == 1 && when (subtype.locale.language) {
                "en" -> secondary.single().language == "fr"
                "fr" -> secondary.single().language == "en"
                else -> false
            }
        }
        fun standalone(subtype: SettingsSubtype) =
            if (legacy(subtype)) subtype.without(ExtraValue.SECONDARY_LOCALES) else subtype
        val enabled = SubtypeSettings.createSettingsSubtypes(
            prefs.getString(Settings.PREF_ENABLED_SUBTYPES, Defaults.PREF_ENABLED_SUBTYPES)!!)
        val oldBilingual = enabled.filter(::legacy)
        val updated = enabled.map(::standalone).toMutableList()
        for (subtype in oldBilingual) {
            val other = if (subtype.locale.language == "en") Locale.FRENCH else Locale.US
            if (updated.none { it.locale.language == other.language })
                SubtypeSettings.getResourceSubtypesForLocale(other).firstOrNull()
                    ?.let { updated.add(it.toSettingsSubtype()) }
        }
        val additional = SubtypeSettings.createSettingsSubtypes(
            prefs.getString(Settings.PREF_ADDITIONAL_SUBTYPES, Defaults.PREF_ADDITIONAL_SUBTYPES)!!)
            .mapNotNull { old ->
                val replacement = standalone(old)
                if (replacement != old && replacement.isSameAsDefault()) null else replacement
            }
        prefs.edit {
            putBoolean(PREF_MIGRATED, true)
            if (updated != enabled)
                putString(Settings.PREF_ENABLED_SUBTYPES, SubtypeSettings.createPrefSubtypes(updated))
            putString(Settings.PREF_ADDITIONAL_SUBTYPES, SubtypeSettings.createPrefSubtypes(additional))
            val selected = prefs.getString(Settings.PREF_SELECTED_SUBTYPE, null)
            if (selected != null && legacy(selected.toSettingsSubtype()))
                putString(Settings.PREF_SELECTED_SUBTYPE, read(prefs).toPref())
            prefs.all.filterKeys { it.startsWith(Settings.PREF_SAVED_APP_SUBTYPE_PREFIX) }
                .filterValues { it is String && legacy(it.toSettingsSubtype()) }
                .forEach { putString(it.key, read(prefs).toPref()) }
        }
    }
}
