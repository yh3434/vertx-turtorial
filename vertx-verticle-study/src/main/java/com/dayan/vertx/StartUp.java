package com.dayan.vertx;

import com.dayan.vertx.verticle.AdminVerticle;
import com.dayan.vertx.verticle.SecKillVerticle;
import io.vertx.core.Vertx;

public class StartUp {

  public static void main(String[] args) {
    Vertx vertx = Vertx.vertx();
    vertx.deployVerticle(AdminVerticle.class.getName());
    vertx.deployVerticle(SecKillVerticle.class.getName());
  }

}
