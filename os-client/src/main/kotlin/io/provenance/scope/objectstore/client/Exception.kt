package io.provenance.scope.objectstore.client

// TODO fix duplicate protos across encryption and contract-proto

class MalformedStreamException(msg: String) : Exception(msg)
class TimeoutException(msg: String) : Exception(msg)
