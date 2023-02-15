package io.provenance.scope

import com.google.gson.FieldNamingPolicy
import com.google.gson.GsonBuilder

class ContractWasm(contractBytes: ByteArray): WasmInstance(contractBytes) {
    val structure = callFunByName("__p8e_entrypoint", *emptyArray()).decodeToString().let {
        GsonBuilder()
            .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
            .create()
            .fromJson(it, P8eContractDetails::class.java)
    }

    init {
        println("value = ${GsonBuilder().setPrettyPrinting().create().toJson(structure)}")
    }
}
