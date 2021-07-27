package io.provenance.scope.sdk.extensions

import io.provenance.metadata.v1.Record
import io.provenance.metadata.v1.ScopeResponse

fun ScopeResponse.uuid(): String = this.scope.scopeIdInfo.scopeUuid

fun Record.resultType(): String = this.process.name
// TODO see what we should do when there's more than one output
fun Record.resultHash(): String = this.outputsList.first().hash
