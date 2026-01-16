package com.rixafy.pingprotocol

import io.netty.buffer.ByteBuf
import java.nio.charset.StandardCharsets

object MinecraftProtocol {

    /**
     * Read a VarInt from the buffer
     */
    fun readVarInt(buf: ByteBuf): Int {
        var value = 0
        var position = 0
        var currentByte: Byte

        while (true) {
            if (!buf.isReadable) {
                throw RuntimeException("VarInt is too big")
            }

            currentByte = buf.readByte()
            value = value or ((currentByte.toInt() and 0x7F) shl position)

            if ((currentByte.toInt() and 0x80) != 0x80) break

            position += 7
            if (position >= 32) throw RuntimeException("VarInt is too big")
        }

        return value
    }

    /**
     * Write a VarInt to the buffer
     */
    fun writeVarInt(buf: ByteBuf, value: Int) {
        var v = value
        while (true) {
            if ((v and 0x7F.inv()) == 0) {
                buf.writeByte(v)
                return
            }

            buf.writeByte((v and 0x7F) or 0x80)
            v = v ushr 7
        }
    }

    /**
     * Read a string from the buffer (VarInt length + UTF-8 bytes)
     */
    fun readString(buf: ByteBuf): String {
        val length = readVarInt(buf)
        if (length > 32767) throw RuntimeException("String is too long")

        val bytes = ByteArray(length)
        buf.readBytes(bytes)
        return String(bytes, StandardCharsets.UTF_8)
    }

    /**
     * Write a string to the buffer (VarInt length + UTF-8 bytes)
     */
    fun writeString(buf: ByteBuf, value: String) {
        val bytes = value.toByteArray(StandardCharsets.UTF_8)
        if (bytes.size > 32767) throw RuntimeException("String is too long")

        writeVarInt(buf, bytes.size)
        buf.writeBytes(bytes)
    }

    /**
     * Read a UTF-16BE string (for legacy protocol)
     */
    fun readStringUTF16BE(buf: ByteBuf, length: Int): String {
        val bytes = ByteArray(length * 2)
        buf.readBytes(bytes)
        return String(bytes, Charsets.UTF_16BE)
    }

    /**
     * Write a UTF-16BE string (for legacy protocol)
     */
    fun writeStringUTF16BE(buf: ByteBuf, value: String) {
        val bytes = value.toByteArray(Charsets.UTF_16BE)
        buf.writeShort(bytes.size / 2) // Write length in code units
        buf.writeBytes(bytes)
    }
}
