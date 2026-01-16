package com.rixafy.pingprotocol

import com.google.gson.annotations.SerializedName

data class StatusResponse(
    val version: Version,
    val players: Players,
    val description: Description,
    val favicon: String? = null
) {
    data class Version(
        val name: String,
        val protocol: Int
    )

    data class Players(
        val max: Int,
        val online: Int,
        val sample: List<PlayerSample>? = null
    )

    data class PlayerSample(
        val name: String,
        val id: String
    )

    data class Description(
        val text: String
    )
}
