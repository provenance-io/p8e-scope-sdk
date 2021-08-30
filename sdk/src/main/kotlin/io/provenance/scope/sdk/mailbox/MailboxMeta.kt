package io.provenance.scope.sdk.mailbox

object MailboxMeta {
    const val KEY = "P8EAPI::TYPE"
    const val FRAGMENT_REQUEST = "FRAGMENT_REQUEST"
    const val FRAGMENT_RESPONSE = "FRAGMENT_RESPONSE"
    const val ERROR_RESPONSE = "ERROR_RESPONSE"

    val MAILBOX_REQUEST = mapOf(KEY to FRAGMENT_REQUEST)
    val MAILBOX_RESPONSE = mapOf(KEY to FRAGMENT_RESPONSE)
    val MAILBOX_ERROR = mapOf(KEY to ERROR_RESPONSE)
}
