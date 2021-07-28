package io.provenance.scope.sdk

import io.provenance.metadata.v1.Session
import io.provenance.scope.contract.proto.Specifications
import kotlin.contracts.contract

class SessionBuilder (
    val proposedSession: Session?,
    val participants: List<Specifications.PartyType>
) {
    private constructor(builder: Builder) : this(builder.proposedSession, builder.participants)

    class Builder {
        var proposedSession: Session? = null
        private set
        var participants: MutableList<Specifications.PartyType> = mutableListOf<Specifications.PartyType>()
        private set

        fun build() = SessionBuilder(this)

        fun addProposedSession(session: Session) = apply {
            this.proposedSession = session
        }

        fun addParticipant(participant: Specifications.PartyType) = apply {
            this.participants.add(participant)
        }

        fun addAllParticipants(participants: Iterable<Specifications.PartyType>) = apply {
            this.participants.addAll(participants)
        }
    }
}
