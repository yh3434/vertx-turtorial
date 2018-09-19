package com.dayan.vertx.verticle;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import java.util.LinkedList;

public class SecKillVerticle extends AbstractVerticle {

  private Router router;

  private EventBus eventBus;

  //可以秒杀的商品列表
  private LinkedList<JsonObject> goods = new LinkedList<>();

  @Override
  public void start(Future<Void> startFuture) {
    eventBus = vertx.eventBus();
    router = Router.router(vertx);
    //增加库存的内部接口
    MessageConsumer<JsonObject> consumer = eventBus.consumer("add_goods");
    consumer.handler(this::addGoods);
    //查询库存的内部接口
    MessageConsumer<JsonArray> queryConsumer = eventBus.consumer("query_goods");
    queryConsumer.handler(this::queryGoods);
    router.route().handler(BodyHandler.create().setBodyLimit(1000000));
    router.route("/api/goods").method(HttpMethod.POST)
        .handler(routingContext -> kill(routingContext));

    // create http server
    HttpServerOptions options = new HttpServerOptions().setIdleTimeout(10);
    Future.<HttpServer>future(httpServerFuture -> vertx.createHttpServer(options)
        .requestHandler(router::accept)
        .listen(8081, httpServerFuture))
        .compose(consume -> Future.future(consumer::completionHandler))
        .setHandler(startFuture.completer());
  }

  private void kill(RoutingContext routingContext) {
    HttpServerResponse response = routingContext.response();
    routingContext.response().setStatusCode(200)
        .putHeader("content-type", "application/json;charset=utf-8");
    if (goods.size() == 0) {
      //库存不足
      routingContext.response().end(new JsonObject().put("error", "Inventory shortage").toString());
      return;
    } else {
      //从库存中取出一个商品
      JsonObject entries = goods.get(0);
      //从库存中删除商品
      this.goods.remove(0);
      response.end(entries.toString());
    }
  }

  private void queryGoods(Message<JsonArray> message) {
    message.reply(new JsonArray(goods));
  }

  private void addGoods(Message<JsonObject> message) {
    goods.add(message.body());
    message.reply(new JsonObject());
  }
}
