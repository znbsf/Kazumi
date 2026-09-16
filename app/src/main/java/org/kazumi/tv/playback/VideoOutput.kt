package org.kazumi.tv.playback

enum class VideoOutput(val label: String) {
    AUTO("自动"), SURFACE("SurfaceView"), TEXTURE("TextureView（兼容）");
    fun usesTexture(device: String, sdk: Int) = this == TEXTURE || (this == AUTO && device == "mulan" && sdk == 28)
    companion object { fun fromStored(value: String?) = entries.firstOrNull { it.name == value } ?: AUTO }
}
