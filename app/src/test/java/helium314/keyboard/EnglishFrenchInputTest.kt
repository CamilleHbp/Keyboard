package helium314.keyboard

import helium314.keyboard.latin.LatinIME
import helium314.keyboard.latin.common.Constants.Subtype.ExtraValue
import helium314.keyboard.latin.settings.Settings
import helium314.keyboard.latin.settings.SettingsSubtype.Companion.toSettingsSubtype
import helium314.keyboard.latin.utils.*
import helium314.keyboard.latin.utils.SubtypeLocaleUtils.displayName
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale
import kotlin.test.*

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35], qualifiers = "en-rUS", shadows = [ShadowInputMethodManager2::class])
class EnglishFrenchInputTest {
    private val ime = Robolectric.setupService(LatinIME::class.java)
    private val prefs get() = ime.prefs()
    private fun base(locale: Locale) = SubtypeSettings.getResourceSubtypesForLocale(locale).first()
    private fun enabled() = SubtypeSettings.getEnabledSubtypes(true)
    private fun combined() = enabled().single { EnglishFrenchInput.isCombined(it) }

    @Before fun reset() {
        prefs.edit().clear().apply()
        SubtypeSettings.reloadEnabledSubtypes(ime)
    }

    @Test fun enablingBothLanguagesOffersThreeIndependentInputs() {
        SubtypeSettings.addEnabledSubtype(prefs, base(Locale.US))
        assertFalse(enabled().any { EnglishFrenchInput.isCombined(it) })
        SubtypeSettings.addEnabledSubtype(prefs, base(Locale.FRENCH))
        assertEquals(3, enabled().size)
        assertEquals("qwerty", combined().mainLayoutName())
        assertEquals("French + English", combined().displayName())
        assertEquals("Français + anglais", combined().displayName(Locale.FRENCH))
        assertEquals("azerty", base(Locale.FRENCH).mainLayoutName())
        assertEquals(setOf("en"), getSecondaryLocales(combined().extraValue).map { it.language }.toSet())
        enabled().filterNot { EnglishFrenchInput.isCombined(it) }.forEach {
            assertTrue(getSecondaryLocales(it.extraValue).isEmpty())
        }
        assertEquals(1, SubtypeSettings.getAllAvailableSubtypes().count { EnglishFrenchInput.isCombined(it) })
    }

    @Test fun addingFrenchRetainsImplicitEnglish() {
        SubtypeSettings.addEnabledSubtype(prefs, base(Locale.FRENCH))
        assertEquals(3, enabled().size)
        assertTrue(enabled().contains(base(Locale.US)))
        assertNotNull(combined())
    }

    @Test fun combinedLayoutEditsPersistWithoutChangingStandaloneInputs() {
        SubtypeSettings.addEnabledSubtype(prefs, base(Locale.US))
        SubtypeSettings.addEnabledSubtype(prefs, base(Locale.FRENCH))
        SubtypeSettings.setSelectedSubtype(prefs, combined())
        val from = combined().toSettingsSubtype()
        val to = from.withLayout(LayoutType.MAIN, "azerty")
        SubtypeUtilsAdditional.changeAdditionalSubtype(from, to, ime)
        assertEquals(to, combined().toSettingsSubtype())
        assertEquals(to, SubtypeSettings.getSelectedSubtype(prefs).toSettingsSubtype())
        assertEquals("qwerty", enabled().first { it.locale() == Locale.US }.mainLayoutNameOrQwerty())
        assertEquals("azerty", enabled().first { !EnglishFrenchInput.isCombined(it) && it.locale() == Locale.FRENCH }.mainLayoutName())
        assertEquals(2, SubtypeSettings.createSettingsSubtypes(prefs.getString(Settings.PREF_ENABLED_SUBTYPES, "")!!).size)
        SubtypeSettings.reloadEnabledSubtypes(ime)
        assertEquals(to, combined().toSettingsSubtype())
    }

    @Test fun disablingEitherLanguageHidesCombinedAndRestoresItsSettingsOnReenable() {
        SubtypeSettings.addEnabledSubtype(prefs, base(Locale.US))
        SubtypeSettings.addEnabledSubtype(prefs, base(Locale.FRENCH))
        val from = combined().toSettingsSubtype()
        SubtypeUtilsAdditional.changeAdditionalSubtype(from, from.withLayout(LayoutType.MAIN, "azerty"), ime)
        SubtypeSettings.setSelectedSubtype(prefs, combined())
        SubtypeSettings.removeEnabledSubtype(ime, base(Locale.FRENCH))
        assertFalse(enabled().any { EnglishFrenchInput.isCombined(it) })
        assertFalse(EnglishFrenchInput.isCombined(SubtypeSettings.getSelectedSubtype(prefs)))
        assertFalse(SubtypeSettings.getAllAvailableSubtypes().any { EnglishFrenchInput.isCombined(it) })
        SubtypeSettings.addEnabledSubtype(prefs, base(Locale.FRENCH))
        assertEquals("azerty", combined().mainLayoutName())
        SubtypeSettings.removeEnabledSubtype(ime, base(Locale.US))
        assertFalse(enabled().any { EnglishFrenchInput.isCombined(it) })
    }

    @Test fun migrationSeparatesOldBilingualInputAndKeepsSelectionAndAppPreference() {
        val legacy = base(Locale.US).toSettingsSubtype().with(ExtraValue.SECONDARY_LOCALES, "fr")
        val german = base(Locale.GERMAN).toSettingsSubtype()
        val savedAppKey = Settings.PREF_SAVED_APP_SUBTYPE_PREFIX + "example"
        prefs.edit().putBoolean("separate_english_french_input_migrated", false)
            .putString(Settings.PREF_ADDITIONAL_SUBTYPES, legacy.toPref())
            .putString(Settings.PREF_ENABLED_SUBTYPES, SubtypeSettings.createPrefSubtypes(listOf(legacy, german)))
            .putString(Settings.PREF_SELECTED_SUBTYPE, legacy.toPref())
            .putString(savedAppKey, legacy.toPref()).apply()
        SubtypeSettings.reloadEnabledSubtypes(ime)
        assertEquals(4, enabled().size)
        assertTrue(enabled().contains(base(Locale.GERMAN)))
        assertTrue(enabled().contains(base(Locale.US)))
        assertTrue(enabled().contains(base(Locale.FRENCH)))
        assertTrue(EnglishFrenchInput.isCombined(SubtypeSettings.getSelectedSubtype(prefs)))
        assertEquals(combined().toSettingsSubtype().toPref(), prefs.getString(savedAppKey, null))
        SubtypeSettings.reloadEnabledSubtypes(ime)
        assertEquals(4, enabled().size)
    }

    @Test fun migrationFromFrenchAzertyCreatesQwertyCombinedInput() {
        val legacy = base(Locale.FRENCH).toSettingsSubtype()
            .with(ExtraValue.SECONDARY_LOCALES, "en-US")
        prefs.edit().putBoolean("separate_english_french_input_migrated", false)
            .putString(Settings.PREF_ADDITIONAL_SUBTYPES, legacy.toPref())
            .putString(Settings.PREF_ENABLED_SUBTYPES, legacy.toPref())
            .putString(Settings.PREF_SELECTED_SUBTYPE, legacy.toPref()).apply()
        SubtypeSettings.reloadEnabledSubtypes(ime)
        assertEquals(3, enabled().size)
        assertEquals("qwerty", combined().mainLayoutName())
        assertEquals("azerty", enabled().first {
            it.locale().language == "fr" && !EnglishFrenchInput.isCombined(it)
        }.mainLayoutName())
        assertEquals(combined(), SubtypeSettings.getSelectedSubtype(prefs))
    }
}
