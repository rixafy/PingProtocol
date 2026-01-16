package com.rixafy.pingprotocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.handler.codec.ByteToMessageDecoder
import io.netty.handler.codec.CorruptedFrameException

/**
 * Decodes VarInt length-prefixed frames from TCP stream.
 * Ensures complete packets are available before passing to the handler.
 */
class VarIntFrameDecoder : ByteToMessageDecoder() {

    override fun decode(ctx: ChannelHandlerContext, input: ByteBuf, out: MutableList<Any>) {
        input.markReaderIndex()

        // Try to read the VarInt length prefix
        val lengthData = readVarIntBytes(input)
        if (lengthData == null) {
            // Not enough bytes to read the VarInt length yet
            input.resetReaderIndex()
            return
        }

        val (length, varIntSize) = lengthData

        // Validate length
        if (length < 0) {
            throw CorruptedFrameException("Negative length: $length")
        }

        if (length > 2097151) { // 3-byte VarInt max for packet length
            throw CorruptedFrameException("Length too large: $length")
        }

        // Check if we have enough bytes for the full packet
        if (input.readableBytes() < length) {
            // Not enough bytes yet, wait for more data
            input.resetReaderIndex()
            return
        }

        // We have a complete packet, read it
        val frame = input.readRetainedSlice(length)

        // Create a buffer that includes both the length prefix and the packet data
        val fullPacket = Unpooled.buffer(varIntSize + length)
        writeVarInt(fullPacket, length)
        fullPacket.writeBytes(frame)
        frame.release()

        out.add(fullPacket)
    }

    /**
     * Tries to read a VarInt from the buffer without consuming bytes if incomplete.
     * Returns a pair of (value, bytesRead) or null if not enough bytes available.
     */
    private fun readVarIntBytes(buf: ByteBuf): Pair<Int, Int>? {
        var value = 0
        var position = 0
        var bytesRead = 0

        while (bytesRead < 5) { // VarInt is at most 5 bytes
            if (!buf.isReadable) {
                // Not enough bytes available
                return null
            }

            val currentByte = buf.readByte()
            bytesRead++

            value = value or ((currentByte.toInt() and 0x7F) shl position)

            if ((currentByte.toInt() and 0x80) != 0x80) {
                // This was the last byte
                return Pair(value, bytesRead)
            }

            position += 7
            if (position >= 32) {
                throw CorruptedFrameException("VarInt is too big")
            }
        }

        throw CorruptedFrameException("VarInt is too big")
    }

    /**
     * Writes a VarInt to the buffer
     */
    private fun writeVarInt(buf: ByteBuf, value: Int) {
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
}
