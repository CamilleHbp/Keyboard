package helium314.keyboard.latin.gesture

import android.content.Context
import com.android.inputmethod.latin.BinaryDictionary
import helium314.keyboard.keyboard.Keyboard
import helium314.keyboard.latin.common.ComposedData
import helium314.keyboard.latin.dictionary.Dictionary
import helium314.keyboard.latin.utils.Log
import java.io.File
import java.util.Locale
import java.util.concurrent.Executors
import java.util.concurrent.Future

class NativeSwipe {
    companion object {
        @JvmStatic fun available() = true
        private val executor = Executors.newSingleThreadExecutor()
        private val dictionaries = mutableMapOf<String, Future<List<SwipeWord>>>()

        @Synchronized
        fun prepare(context: Context, languages: List<String>) {
            for (language in languages.filter { it == "en" || it == "fr" }) {
                dictionaries.getOrPut(language) { executor.submit<List<SwipeWord>> { read(context.applicationContext,language) } }
            }
        }

        private fun read(context: Context, language: String): List<SwipeWord> {
            val name = if (language == "fr") "main_fr.dict" else "main_en-US.dict"
            val file = File.createTempFile("keyboard-lexicon", ".dict", context.cacheDir)
            try {
                context.assets.open("dicts/$name").use { input -> file.outputStream().use { input.copyTo(it) } }
                val dict = BinaryDictionary(file.absolutePath, 0, file.length(), false, Locale.forLanguageTag(language), Dictionary.TYPE_MAIN, false)
                try {
                    check(dict.isValidDictionary)
                    val result = ArrayList<SwipeWord>()
                    var token = 0
                    do {
                        val next = dict.getNextWordProperty(token)
                        val entry = next.mWordProperty
                        val word = entry.mWord
                        if (!entry.mIsNotAWord && !entry.mIsBeginningOfSentence && word.length in 2..40 && entry.probability > 2) {
                            val keys = SwipeDecoder.spelling(word)
                            if (keys.length in 2..64) result.add(SwipeWord(word,keys,entry.probability,entry.mIsPossiblyOffensive))
                        }
                        token = next.mNextToken
                    } while (token != 0)
                    return result
                } finally { dict.close() }
            } finally { file.delete() }
        }
    }

    fun suggest(context: Context, languages: List<String>, keyboard: Keyboard, data: ComposedData,
                blockOffensive: Boolean, isFinal: Boolean): List<String>? {
        if (!data.mIsBatchMode || languages.isEmpty() || languages.any { it != "en" && it != "fr" }) return null
        val unit = keyboard.mMostCommonKeyWidth.toFloat()
        if (unit <= 0f) return null
        val pointers = data.mInputPointers
        if (pointers.pointerSize < 2 || (1 until pointers.pointerSize).any { pointers.pointerIds[it] != pointers.pointerIds[0] }) return null
        prepare(context,languages)
        val loads = synchronized(Companion) { languages.map { dictionaries.getValue(it) } }
        if (!isFinal && loads.any { !it.isDone }) return emptyList()
        val words = try { loads.map { it.get() } } catch (e: java.util.concurrent.ExecutionException) {
            Log.w("SwipeDecoder","Could not load keyboard dictionaries",e)
            synchronized(Companion) { languages.forEach { dictionaries.remove(it) } }
            return null
        }
        val layout = mutableMapOf<Char,SwipePoint>()
        for (key in keyboard.sortedKeys) {
            val code = Character.toLowerCase(key.code)
            if (code in 'a'.code..'z'.code)
                layout[code.toChar()] = SwipePoint((key.x + key.width/2f)/unit,(key.y + key.height/2f)/unit)
        }
        val points = (0 until pointers.pointerSize).map { SwipePoint(pointers.xCoordinates[it]/unit,pointers.yCoordinates[it]/unit) }
        return SwipeDecoder.suggest(points,layout,words,blockOffensive,isFinal)
    }
}
