package io.provenance.scope.sdk

import com.google.protobuf.ByteString
import com.google.protobuf.Descriptors.*
import com.google.protobuf.Descriptors.FieldDescriptor.JavaType.*
import com.google.protobuf.MapEntry
import com.google.protobuf.Message
import io.provenance.metadata.v1.RecordWrapper
import io.provenance.metadata.v1.ScopeResponse
import io.provenance.metadata.v1.SessionWrapper
import io.provenance.scope.contract.proto.Specifications.ContractSpec
import io.provenance.scope.contract.proto.Commons.DefinitionSpec
import io.provenance.scope.contract.proto.IndexProto.Index
import io.provenance.scope.contract.proto.IndexProto.Index.Behavior
import io.provenance.scope.contract.proto.IndexProto.Index.Behavior.*
import io.provenance.scope.encryption.crypto.SignerImpl
import io.provenance.scope.encryption.model.KeyRef
import io.provenance.scope.encryption.util.getAddress
import io.provenance.scope.objectstore.client.CachedOsClient
import io.provenance.scope.objectstore.util.base64Decode
import io.provenance.scope.sdk.extensions.resultType
import io.provenance.scope.util.MetadataAddress
import org.slf4j.LoggerFactory
import java.io.ByteArrayInputStream

private fun FieldDescriptor.getIndex(
    extensionDescriptor: FieldDescriptor
): Index? {
    // return options.getField(extensionDescriptor) as? Index
    // Pierce Trey - 03/10/2021 replaced the above line with the below to handle old versions of p8e-contract
    // that use the io.provenance.util namespaced Index class (while we only have access to the new io.p8e.util namespace)
    // which caused all fields to get skipped for indexing.
    // todo: change back to the old line once p8e-contract is converted to use the new index extension and
    // the old Index message/extension have been removed from provenance-corelib
    return options.getField(extensionDescriptor)?.let {
        try {
            Index.parseFrom((it as Message).toByteArray())
        } catch (t: Throwable) {
            LoggerFactory.getLogger(this::class.java)
                .error("FieldDescriptor.getIndex failed to parse index extension with error", t)
            null
        }
    }
}

private fun Descriptor.getIndex(
    extensionDescriptor: FieldDescriptor
): Index? {
    // return options.getField(extensionDescriptor) as? Index
    // Pierce Trey - 03/10/2021 replaced the above line with the below to handle old versions of p8e-contract
    // that use the io.provenance.util namespaced Index class (while we only have access to the new io.p8e.util namespace)
    // which caused all fields to get skipped for indexing.
    // todo: change back to the old line once p8e-contract is converted to use the new index extension and
    // the old Index message/extension have been removed from provenance-corelib
    return options.getField(extensionDescriptor)?.let {
        try {
            Index.parseFrom((it as Message).toByteArray())
        } catch (t: Throwable) {
            LoggerFactory.getLogger(this::class.java)
                .error("Descriptor.getIndex failed to parse index extension with error", t)
            null
        }
    }
}
