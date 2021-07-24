package io.provenance.p8e.encryption.model

import com.fasterxml.jackson.databind.ObjectMapper
import io.provenance.p8e.encryption.aes.ProvenanceAESCrypt
import io.provenance.p8e.encryption.dime.ProvenanceDIME
import io.provenance.scope.encryption.Encryption.Audience
import io.provenance.scope.encryption.Encryption.DIME
import java.io.InputStream
import java.util.*
import javax.crypto.spec.SecretKeySpec

open class DIMEProcessingModel(open val dime: DIME, open val processingAudience: List<Audience>) {
    fun processingKeysTransient(objectMapper: ObjectMapper): Map<String, ByteArray> {
        return mapOf(ProvenanceDIME.PROCESSING_KEYS to objectMapper.writeValueAsString(this.processingAudience).toByteArray(Charsets.UTF_8))
    }
}

data class DIMEStreamProcessingModel(override val dime: DIME, override val processingAudience: List<Audience>, val encryptedPayload: InputStream):
    DIMEProcessingModel(dime, processingAudience)

data class DIMEAdditionalAuthenticationModel(val dekAdditionalAuthenticatedData: String = "", val payloadAdditionalAuthenticatedData: String = "")
data class DIMEDekPayloadModel(val dek: String, val decryptedPayload: String) {
    fun getSecretKeySpec(): SecretKeySpec = ProvenanceAESCrypt.secretKeySpecGenerate(Base64.getDecoder().decode(dek))
}
