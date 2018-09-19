package com.dayan.vertx.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;

public class AdminVerticle extends AbstractVerticle {

  private Router router;

  private EventBus eventBus;

  private int index = 0;

  @Override
  public void start(Future<Void> startFuture) throws Exception {
    eventBus = vertx.eventBus();
    router = Router.router(vertx);
    router.route().handler(BodyHandler.create().setBodyLimit(1000000));
    router.route("/api/goods").method(HttpMethod.POST)
        .handler(routingContext -> addGoods(routingContext));

    router.route("/api/goods").method(HttpMethod.GET)
        .handler(routingContext -> queryGoods(routingContext));

    // create http server
    HttpServerOptions options = new HttpServerOptions().setIdleTimeout(10);
    Future.<HttpServer>future(httpServerFuture -> vertx.createHttpServer(options)
        .requestHandler(router::accept)
        .listen(8082, httpServerFuture))
        .setHandler(res -> {
          if (res.failed()) {
            startFuture.fail(res.cause());
          } else {
            startFuture.complete();
          }
        });
  }

  private void queryGoods(RoutingContext routingContext) {
    //通过eventbus去查询库存
    eventBus.send("query_goods", new JsonObject(), res -> {
      routingContext.response().setStatusCode(200)
          .putHeader("content-type", "application/json;charset=utf-8");
      if (res.succeeded()) {
        routingContext.response().end(res.result().body().toString());
      } else {
        routingContext.response()
            .end(new JsonObject().put("error", res.cause().getMessage()).toString());
      }
    });
  }

  private void addGoods(RoutingContext routingContext) {
    ++index;
    final JsonObject newGood = new JsonObject().put("id", index).put("name", index + "");
    //通过eventbus去增加一个商品到库存
    eventBus.send("add_goods", newGood, res -> {
      routingContext.response().setStatusCode(200)
          .putHeader("content-type", "application/json;charset=utf-8");
      if (res.succeeded()) {
        routingContext.response().end(newGood.toString());
      } else {
        routingContext.response()
            .end(new JsonObject().put("error", res.cause().getMessage()).toString());
      }
    });
  }

}
