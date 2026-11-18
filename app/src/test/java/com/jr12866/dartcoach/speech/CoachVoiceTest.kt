package com.jr12866.dartcoach.speech

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The voices the coach can speak with.
 *
 * The thing worth testing here is the pairing of a model with the phonemes
 * it is spoken with. A model and the wrong phonemes still make a sound, and
 * it sounds like English, so nothing about it looks broken - it just says
 * the words with the wrong vowels.
 */
class CoachVoiceTest {

    @Test
    fun `the coach speaks with the British voice by default`() {
        assertEquals(CoachVoice.ALBA, CoachVoice.DEFAULT)
    }

    @Test
    fun `a voice that is on offer comes back by its id`() {
        for (voice in CoachVoice.entries) {
            assertEquals(voice, CoachVoice.byId(voice.id))
        }
    }

    @Test
    fun `a voice that is not on offer falls back to the default`() {
        // A settings file written by a version of the app that offered a
        // different set of voices must not leave the coach without one.
        assertEquals(CoachVoice.DEFAULT, CoachVoice.byId("nonsense"))
        assertEquals(CoachVoice.DEFAULT, CoachVoice.byId(null))
        assertEquals(CoachVoice.DEFAULT, CoachVoice.byId(""))
    }

    @Test
    fun `every voice is phonemised in its own accent`() {
        for (voice in CoachVoice.entries) {
            // The model directory names the language the model was trained
            // on - "en_GB-alba" - and the phoneme voice has to be the same
            // language: "en-gb-x-rp" for that one, "en-us" for the American
            // one. A voice from another language would be pronounced with
            // vowels it was never trained on.
            val language = voice.phonemeVoice.substringBefore('-').replace('-', '_')
            assertTrue(
                "expected ${voice.modelDir} to be a $language voice, " +
                    "but it is phonemised with ${voice.phonemeVoice}",
                voice.modelDir.contains(language),
            )
        }
    }

    @Test
    fun `every voice has a model of its own`() {
        // Two voices sharing a directory would mean the second one spoke
        // with the first one's voice.
        val directories = CoachVoice.entries.map { it.modelDir }
        assertEquals(directories.size, directories.distinct().size)

        val models = CoachVoice.entries.map { it.modelName }
        assertEquals(models.size, models.distinct().size)
    }
}
