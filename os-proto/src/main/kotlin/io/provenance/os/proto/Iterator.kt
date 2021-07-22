package io.provenance.os.proto

import com.google.protobuf.ByteString
import objectstore.Object.ChunkEnd
import objectstore.Object
import java.io.InputStream

class InputStreamChunkedIterator(
    private val inputStream: InputStream,
    private val name: String,
    private val contentLength: Long,
    private val chunkSize: Int = 2 * 1_024 * 1_024 // ~2MB
) : Iterator<Object.ChunkBidi> {
    private val header: Object.StreamHeader = Object.StreamHeader.newBuilder()
        .setName(this.name)
        .setContentLength(this.contentLength)
        .build()
    private var sentHeader = false
    private var sentEnd = false

    override fun hasNext(): Boolean = !sentEnd

    override fun next(): Object.ChunkBidi {
        val bytes = ByteArray(chunkSize)
        val bytesRead = inputStream.read(bytes)

        return Object.ChunkBidi.newBuilder()
            .setChunk(
                Object.Chunk.newBuilder()
                    .also { builder ->
                        if (bytesRead < 0) {
                            builder.end = ChunkEnd.getDefaultInstance()
                            sentEnd = true
                        } else {
                            if (!sentHeader) {
                                builder.header = header
                                sentHeader = true
                            }

                            builder.data = ByteString.copyFrom(bytes, 0, bytesRead)
                        }
                    }
                    .build()
            )
            .build()
    }
}
