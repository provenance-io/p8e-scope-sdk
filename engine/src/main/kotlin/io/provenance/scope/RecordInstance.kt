package io.provenance.scope

import arrow.core.Either
import com.google.protobuf.Message

data class RecordInstance(
    val name: String,
    val clazz: Class<out Message>,
    val messageOrCollection: Either<Message, List<Message>>
)
