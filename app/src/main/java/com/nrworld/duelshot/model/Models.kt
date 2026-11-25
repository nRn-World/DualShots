package com.nrworld.duelshot.model

// Motsvarar androidx.camera.core.AspectRatio värden (hårdkodade för att
// undvika import i modell-lagret).
private const val CX_RATIO_4_3 = 1
private const val CX_RATIO_16_9 = 2

/** De tre inspelningslägena. */
enum class RecordingMode(val title: String, val subtitle: String) {
    DUAL_VIEW("Dual View", "Both cameras in one video"),
    SEPARATE("Separate Videos", "Two files, one for each"),
    BOTH("Both Options", "Combined + two separate files")
}

/** Valbart format per kamera. */
enum class AspectOption(val label: String, val cxRatio: Int, val portrait: Boolean) {
    R_9_16("9:16 Portrait", CX_RATIO_16_9, true),
    R_16_9("16:9 Landscape", CX_RATIO_16_9, false),
    R_1_1("1:1 Square", CX_RATIO_4_3, true),
    R_4_3("4:3", CX_RATIO_4_3, false)
}

/** Upplösning/kvalitet. */
enum class QualityOption(val label: String) {
    HD720("720p"),
    FHD1080("1080p Full HD"),
    UHD4K("4K Ultra HD")
}

/** Behållare/format. */
enum class CodecOption(val label: String, val mime: String) {
    MP4("MP4", "video/mp4"),
    HEVC("HEVC", "video/hevc")
}
