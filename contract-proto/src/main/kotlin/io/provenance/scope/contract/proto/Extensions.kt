 package io.provenance.scope.contract.proto

 import io.provenance.scope.proto.Util

 // import com.google.protobuf.DescriptorProtos
// import com.google.protobuf.GeneratedMessage
// import com.google.protobuf.ProtocolMessageEnum
// import io.provenance.scope.contract.proto.ContractScope.Envelope.Status

// /**
//  * Get enum description for a status value.
//  */
// fun Status.getDescription(): String {
//     return getExtension(ContractScope.description)
// }

// /**
//  * com.google.protobuf.ProtocolMessageEnum -> String Description
//  */
// fun <T> ProtocolMessageEnum.getExtension(extension: GeneratedMessage.GeneratedExtension<DescriptorProtos.EnumValueOptions, T>): T {
//     try {
//         return valueDescriptor.options.getExtension(extension)
//     } catch (e: ArrayIndexOutOfBoundsException) {
//         throw IllegalArgumentException("${javaClass.name}.$this missing extension [(${extension.descriptor.name}) = ???]. Try filtering it out")
//     }
// }

 val Envelopes.EnvelopeError.executionUuid: Util.UUID
    get() = envelope.executionUuid

 val Envelopes.EnvelopeError.sessionUuid: Util.UUID
    get() = envelope.ref.sessionUuid

 val Envelopes.EnvelopeError.scopeUuid: Util.UUID
    get() = envelope.ref.scopeUuid
