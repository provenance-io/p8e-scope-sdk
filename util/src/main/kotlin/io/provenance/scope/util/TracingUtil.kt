//package io.provenance.scope.util
//
//import io.opentracing.Span
//import io.opentracing.util.GlobalTracer;
//
//class TracingUtil() {
//
//    val spanMap: MutableMap<String, Span> = mutableMapOf()
//
//    fun startSpan(operationName: String) {
//        try {
//            if (GlobalTracer.isRegistered()) {
//                val tracer = GlobalTracer.get()
//                spanMap[operationName] = tracer.buildSpan(operationName).start()
//            }
//        } catch (e: Exception) {
//            return
//        }
//    }
//
//    fun finishSpan(operationName: String) {
//        try {
//            spanMap[operationName]?.finish()
//            spanMap.remove(operationName)
//        } catch (e: java.lang.Exception) {
//            return
//        }
//    }
//
//}