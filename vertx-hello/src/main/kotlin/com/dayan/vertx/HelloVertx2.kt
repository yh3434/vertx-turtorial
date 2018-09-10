package com.dayan.vertx

import io.vertx.core.AbstractVerticle

class HelloVertx2 : AbstractVerticle() {
    override fun start() {
        vertx.createHttpServer()
                .requestHandler { req ->
                    req.response()
                            .putHeader("content-type", "text/plain")
                            .end("Hello from Vert.x-kotlin")
                }.listen(8081)
    }
}