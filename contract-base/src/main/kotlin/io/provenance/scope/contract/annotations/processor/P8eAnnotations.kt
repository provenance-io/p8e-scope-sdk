package io.provenance.scope.contract.annotations.processor

import io.provenance.scope.contract.annotations.Function
import io.provenance.scope.contract.annotations.Record
import io.provenance.scope.contract.annotations.Input
import io.provenance.scope.contract.annotations.Participants

enum class P8eAnnotations(val clazz: Class<out Annotation>) {
    PARTICIPANT(Participants::class.java),
    FUNCTION(Function::class.java),
    FACT(Record::class.java),
    INPUT(Input::class.java)
}
