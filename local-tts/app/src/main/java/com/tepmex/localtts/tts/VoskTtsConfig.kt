package com.tepmex.localtts.tts

import org.json.JSONObject
import java.io.File

data class VoskTtsConfig(
    val modelType: String,
    val sampleRate: Int,
    val numSpeakers: Int,
    val phonemeIdMap: Map<String, Int>,
    val inference: InferenceConfig,
) {
    data class InferenceConfig(
        val noiseLevel: Float,
        val speechRate: Float,
        val durationNoiseLevel: Float,
        val scale: Float,
    )

    companion object {
        fun load(configFile: File): VoskTtsConfig {
            val json = JSONObject(configFile.readText())
            val phonemeMap = mutableMapOf<String, Int>()
            val phonemeObj = json.getJSONObject("phoneme_id_map")
            for (key in phonemeObj.keys()) {
                phonemeMap[key] = phonemeObj.getInt(key)
            }
            val inferenceJson = json.optJSONObject("inference") ?: JSONObject()
            val inference = InferenceConfig(
                noiseLevel = inferenceJson.optDouble("noise_level", 0.8).toFloat(),
                speechRate = inferenceJson.optDouble("speech_rate", 1.0).toFloat(),
                durationNoiseLevel = inferenceJson.optDouble("duration_noise_level", 0.8).toFloat(),
                scale = inferenceJson.optDouble("scale", 1.0).toFloat(),
            )
            return VoskTtsConfig(
                modelType = json.optString("model_type", ""),
                sampleRate = json.getJSONObject("audio").optInt("sample_rate", 22050),
                numSpeakers = json.optInt("num_speakers", 1),
                phonemeIdMap = phonemeMap,
                inference = inference,
            )
        }
    }
}
