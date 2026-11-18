package com.jr12866.dartcoach.speech

/**
 * The voices the coach can speak with.
 *
 * Each one is a Piper VITS model: an ONNX file with a tokens file beside it,
 * in a directory of its own, which is the layout sherpa-onnx expects.
 *
 * The phoneme voice matters as much as the model does. Piper models are
 * trained on phoneme codes rather than on text, so the text is phonemised
 * before it reaches the model, and the phonemes have to come from the right
 * accent. A British voice given the American phonemes is pronounced with
 * American vowels, and "treble" comes out wrong.
 *
 * Each model says which espeak voice it was trained with, in the config
 * file beside it - "en-gb-x-rp" for the British one, "en-us" for the
 * American one - and that is where the phoneme voices below come from.
 *
 * The models are fetched by `tools/fetch-models.ps1` and ship in the asset
 * pack, so both of them are on the phone before the app is used. Every voice
 * here costs about 20 MB of the install.
 */
enum class CoachVoice(
    /** What is stored in the settings. */
    val id: String,
    /** What the player is shown. */
    val label: String,
    /** A line of the accent, for the settings screen. */
    val description: String,
    /** The directory the model and its tokens file live in. */
    val modelDir: String,
    /** The model file inside that directory. */
    val modelName: String,
    /** The espeak voice the text is phonemised with. */
    val phonemeVoice: String,
) {
    /** British, and the one the coach speaks with by default. */
    ALBA(
        id = "alba",
        label = "Alba",
        description = "British English",
        modelDir = "vits-piper-en_GB-alba-medium-int8",
        modelName = "en_GB-alba-medium.onnx",
        // The model's own config says "en-gb-x-rp", which is British
        // English with the received pronunciation. It is not the same as
        // "en-gb", and the model's file is where the answer comes from.
        phonemeVoice = "en-gb-x-rp",
    ),

    /** The voice the Windows coach uses, and the other one on offer. */
    LESSAC(
        id = "lessac",
        label = "Lessac",
        description = "American English",
        modelDir = "vits-piper-en_US-lessac-medium-int8",
        modelName = "en_US-lessac-medium.onnx",
        phonemeVoice = "en-us",
    );

    companion object {
        /** The voice the coach speaks with unless the player says otherwise. */
        val DEFAULT = ALBA

        /**
         * The voice with this id, or the default.
         *
         * A voice that is not on offer - one saved by a version of the app
         * that offered a different set, or a value that got mangled - falls
         * back to the default rather than leaving the coach without a voice.
         */
        fun byId(id: String?): CoachVoice =
            entries.firstOrNull { it.id == id } ?: DEFAULT
    }
}
