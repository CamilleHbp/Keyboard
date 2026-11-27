package helium314.keyboard.latin.gesture

import java.text.Normalizer
import java.util.Locale
import java.util.PriorityQueue
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

internal data class SwipeWord(val text: String, val keys: String, val frequency: Int, val offensive: Boolean)
internal data class SwipePoint(val x: Float, val y: Float)

/** Uses the point retention, key emissions and string alignment from the independent
 * spatial model in reconstruction/spatial/model.py. Positions are measured in key widths. */
internal object SwipeDecoder {
    private val marks = Regex("\\p{M}+")
    fun spelling(word: String): String {
        val normalized = Normalizer.normalize(word.lowercase(Locale.ROOT), Normalizer.Form.NFD)
            .replace(marks, "").replace("œ", "oe").replace("æ", "ae")
            .replace("'", "").replace("’", "").replace("-", "")
        return if (normalized.all { it in 'a'..'z' }) normalized else ""
    }

    private fun distance(a: SwipePoint, b: SwipePoint) = hypot(a.x - b.x, a.y - b.y)
    private fun retain(points: List<SwipePoint>): List<SwipePoint> {
        val result = arrayListOf(points.first())
        for (i in 1 until points.lastIndex) {
            if (distance(result.last(), points[i]) >= 0.45f) result.add(points[i])
        }
        result.add(points.last())
        return result
    }

    private fun align(keys: String, rows: Array<FloatArray>, partial: Boolean): Float {
        val size = rows.size
        var older = FloatArray(size)
        var prev = FloatArray(size)
        var curr = FloatArray(size)
        var bestPrefix = Float.NEGATIVE_INFINITY
        for (j in keys.indices) {
            val key = keys[j] - 'a'
            for (i in rows.indices) {
                val emission = rows[i][key]
                curr[i] = when {
                    i == 0 && j == 0 -> emission
                    i == 0 -> max(emission - 2.6f*j, prev[0] - 2.6f)
                    j == 0 -> max(emission - 0.55f*i, curr[i-1] - 0.55f)
                    else -> {
                        val swapBase = when {
                            i == 1 -> -2.6f*(j-1)
                            j == 1 -> -0.55f*(i-1)
                            else -> older[i-2]
                        }
                        maxOf(prev[i-1] + emission, prev[i] - 2.6f, curr[i-1] - 0.55f,
                            swapBase + rows[i-1][key] + rows[i][keys[j-1]-'a'] - 3f)
                    }
                }
            }
            bestPrefix = max(bestPrefix, curr.last() - (keys.length-j-1)*0.4f)
            val recycle = older; older = prev; prev = curr; curr = recycle
        }
        return if (partial) bestPrefix else prev.last()
    }

    fun suggest(raw: List<SwipePoint>, layout: Map<Char, SwipePoint>, words: List<List<SwipeWord>>,
                blockOffensive: Boolean, final: Boolean): List<String> {
        if (raw.size < 2) return emptyList()
        val stroke = retain(raw)
        val rows = Array(stroke.size) { i -> FloatArray(26) { k ->
            val center = layout['a' + k]
            if (center == null) -12f else {
                val d = distance(stroke[i], center) / 0.9f
                max(-12f, -d*d)
            }
        } }
        // Preselect trajectories using the whole stroke, including intervening letters
        // users pass over on the way to the next intended key.
        val travelled = FloatArray(stroke.size)
        for (i in 1 until stroke.size) travelled[i] = travelled[i-1] + distance(stroke[i-1], stroke[i])
        val length = travelled.last()
        if (length < 0.3f) return emptyList()
        data class Match(val word: SwipeWord, val score: Float)
        val best = PriorityQueue<Match>(compareBy { it.score })
        val segments = FloatArray(65)
        for (dictionary in words) for (word in dictionary) {
            val keys = word.keys
            if (blockOffensive && word.offensive) continue
            val start = layout[keys.first()] ?: continue
            val end = layout[keys.last()] ?: continue
            if (distance(start,stroke.first()) > 1.4f || final && distance(end,stroke.last()) > 1.4f) continue
            var previous = start
            var complete = true
            for (i in 1 until keys.length) {
                val center = layout[keys[i]]
                if (center == null) { complete = false; break }
                segments[i] = segments[i-1] + distance(previous, center)
                previous = center
            }
            if (!complete) continue
            val pathLength = segments[keys.lastIndex]
            if (pathLength < 0.01f || final && abs(pathLength-length) > max(3.5f,length*0.65f)) continue
            var error = 0f
            var segment = 1
            val scale = (if (final) pathLength else min(length,pathLength)) / length
            for (i in stroke.indices) {
                val target = travelled[i]*scale
                while (segment < keys.lastIndex && segments[segment] < target) segment++
                val a = layout.getValue(keys[segment-1]); val b = layout.getValue(keys[segment])
                val t = ((target-segments[segment-1])/max(0.001f,segments[segment]-segments[segment-1])).coerceIn(0f,1f)
                val dx = a.x+t*(b.x-a.x)-stroke[i].x; val dy=a.y+t*(b.y-a.y)-stroke[i].y
                error += dx*dx+dy*dy
            }
            val score = -error/stroke.size*6f - (if (final) abs(pathLength-length)*0.32f else 0f) + word.frequency*0.004f
            if (best.size < 48 || score > best.peek().score) {
                if (best.size == 48) best.remove()
                best.add(Match(word, score))
            }
        }
        return best.sortedByDescending { it.score + align(it.word.keys,rows,!final)/stroke.size }
            .map { it.word.text }.distinct().take(12)
    }
}
