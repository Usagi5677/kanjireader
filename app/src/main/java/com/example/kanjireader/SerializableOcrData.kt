package com.example.kanjireader

import com.google.gson.annotations.SerializedName

// Serializable data classes for passing OCR data between activities via Intent
data class SerializableOcrData(
    @SerializedName("text")
    val text: String,
    @SerializedName("textBlocks")
    val textBlocks: List<SerializableTextBlock>
)

data class SerializableTextBlock(
    @SerializedName("text")
    val text: String,
    @SerializedName("boundingBox")
    val boundingBox: SerializableRect?,
    @SerializedName("lines")
    val lines: List<SerializableTextLine>
)

data class SerializableTextLine(
    @SerializedName("text")
    val text: String,
    @SerializedName("boundingBox")
    val boundingBox: SerializableRect?
)

data class SerializableRect(
    @SerializedName("left")
    val left: Int,
    @SerializedName("top")
    val top: Int,
    @SerializedName("right")
    val right: Int,
    @SerializedName("bottom")
    val bottom: Int
)