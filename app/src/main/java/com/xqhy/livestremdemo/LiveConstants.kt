package com.xqhy.livestremdemo

import io.agora.rtc.Constants
import io.agora.rtc.video.VideoEncoderConfiguration
import io.agora.rtc.video.VideoEncoderConfiguration.VideoDimensions

object LiveConstants {
    const val APPID = "35222ddcd4db496d8d2292b871bd878d"
    const val TOKEN ="00635222ddcd4db496d8d2292b871bd878dIACt78AMYvjHnDXczgo8F+NF3GD4cmHEEjmvUm2iiH//Xi4fxRUAAAAAEADGEkMQp3zBYAEAAQCmfMFg"

    const val ROLE = "role"
    const val BROADCASTER = Constants.CLIENT_ROLE_BROADCASTER
    const val AUDIENCE = Constants.CLIENT_ROLE_AUDIENCE

    const val CHANNEL_NAME = "CHANNEL_NAME"

    var VIDEO_DIMENSIONS = arrayOf(
        VideoEncoderConfiguration.VD_320x240,
        VideoEncoderConfiguration.VD_480x360,
        VideoEncoderConfiguration.VD_640x360,
        VideoEncoderConfiguration.VD_640x480,
        VideoDimensions(960, 540),
        VideoEncoderConfiguration.VD_1280x720
    )

    var VIDEO_MIRROR_MODES = intArrayOf(
        Constants.VIDEO_MIRROR_MODE_AUTO,
        Constants.VIDEO_MIRROR_MODE_ENABLED,
        Constants.VIDEO_MIRROR_MODE_DISABLED
    )
}