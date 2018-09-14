package com.dayan.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;

public class HelloVertx extends AbstractVerticle {

    @Override
    public void start() {
        vertx.createHttpServer().requestHandler(req -> req.response()
                .putHeader("content-type", "text/plain")
                .end("Hello from Vert.x!!!")).listen(8080);
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(HelloVertx.class.getName());
        vertx.deployVerticle(HelloVertx2.class.getName());
    }
}
