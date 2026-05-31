package com.tepmex.ctxcalendar.data

/**
 * Display priority for photos on a day. Lower [priority] values appear first.
 * Camera shots rank above screenshots and other saved gallery images.
 */
enum class ImageSource(val priority: Int) {
    CAMERA(0),
    OTHER(1),
    SCREENSHOT(1),
}

fun classifyImageSource(relativePath: String?, bucketDisplayName: String?): ImageSource {
    val path = relativePath?.lowercase().orEmpty()
    val bucket = bucketDisplayName?.lowercase().orEmpty()

    if (
        path.contains("dcim/camera") ||
        bucket == "camera" ||
        path.endsWith("/camera/") ||
        path.contains("/camera/")
    ) {
        return ImageSource.CAMERA
    }

    if (
        path.contains("screenshot") ||
        bucket.contains("screenshot") ||
        path.contains("pictures/screenshots")
    ) {
        return ImageSource.SCREENSHOT
    }

    return ImageSource.OTHER
}

fun List<GalleryPhoto>.sortedForDisplay(): List<GalleryPhoto> =
    sortedWith(
        compareBy<GalleryPhoto> { it.source.priority }
            .thenByDescending { it.dateTakenMillis }
            .thenByDescending { it.id },
    )
