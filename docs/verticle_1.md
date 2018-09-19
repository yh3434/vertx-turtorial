# 什么是Verticle

对于刚接触Vert.x的小伙伴来说,总是会有两个疑问：

1. 什么是Future?

   这个已经在[上篇文章](./ImFuture.md)详细说明了Future的使用方法。

2. 什么是Verticle?

   我们现在就开始Verticle的详细介绍，先看下官网介绍。

   Verticle是Vert.x提供的一个简单，可扩展，类似于actor的部署和并发模型，您可以使用它来保存您自己编写的内容。此模型完全是可选的，如果您不想，Vert.x不会强制您以这种方式创建应用程序。该模型并未声称是严格的actor模型实现，但它确实具有相似性，特别是在并发，扩展和部署方面。

   要使用此模型，请将代码编写为Verticles。

   从第二篇[线程模型](./threadModel.md)可以看到，我们用Verticle编写出来的程序是并发安全、扩展性强、部署容易的：

   * 并发安全：一个verticle内的handler都是由一个线程处理的，因此是线程安全的。我们可以利用这个特性来处理很多事情：比如电商的秒杀、棋牌游戏的一个副本。我们可以忘记繁琐的加锁、去锁的操作，忘掉Concurrent包，所有的事情用高效的HashMap之类的来操作就行。甚至可以按需求实现一些高效的缓存，由于是内存操作，可以省掉连接缓存中间件（比如redis）的大量网络io。
   * 扩展性强：我们可以按需要把业务按Verticle做成边界，天然地就实现了微服务。Verticle组件间通过evetbus（后面会提到）通信，对于访问频繁地业务可以多部署几个实例，集群模式下甚至不需要在同一个进程、同一台机器。我们也无需关系别人是如何实现这个Verticle，用什么语言实现。
   * 部署容易：Vert.x只依赖jvm，不需要tomcat这样的容器，部署及其容易，你可以选择像[第一篇](./initialVertx.md)文章打成一个shade的jar包，可以借助docker。

# Verticle实战

我们开始实现一个Verticle的简单项目，有以下需求：

* 模拟秒杀系统，暴露出一个秒杀的http接口。
* 模拟管理系统，可以随时查看、添加库存。
* 提供测试类：每2秒模拟增加两个库存，每1秒10个用户同时去抢购商品。

结构如下图，源码请点击[链接](../vertx-verticle-study)获取：

![source](https://ws2.sinaimg.cn/large/006tNbRwgy1fven27qb7fj30em0eygmr.jpg)

## SecKillVerticle

秒杀Verticle有下面的功能：

* 暴露秒杀的http接口：抢到商品后，在库存中删除这个商品。
* 暴露增加和查询库存的内部eventbus接口。

代码如下：

```java
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
```

## AdminVerticle

管理的verticle具有下面的功能：

* 暴露查询和增加库存的http接口
* 通过eventbus去调用SecKillVerticle的内部接口，来完成查询和增加库存的功能。

代码如下：

```java
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
```

## Test

点击test包下的GoodsTest.testApplication，我们会看到以下结果，永远不会出现“超卖”的情况。

```
2018-05-19T13:15:41.372秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.373秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.372秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.371秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.375秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.375秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.376秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.377秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.379秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.379秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.788秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.789秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.789秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.790秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.791秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.791秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.793秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:41.794秒杀结果：{"error":"Inventory shortage"}
add goods:{"id":23,"name":"23"}
2018-05-19T13:15:41.795秒杀结果：{"error":"Inventory shortage"}
add goods:{"id":24,"name":"24"}
2018-05-19T13:15:42.791秒杀结果：{"id":23,"name":"23"}
2018-05-19T13:15:42.791秒杀结果：{"id":24,"name":"24"}
2018-05-19T13:15:42.791秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:42.791秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:42.793秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:42.795秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:42.795秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:42.795秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:42.795秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:42.796秒杀结果：{"error":"Inventory shortage"}
add goods:{"id":25,"name":"25"}
add goods:{"id":26,"name":"26"}
2018-05-19T13:15:43.791秒杀结果：{"id":26,"name":"26"}
2018-05-19T13:15:43.791秒杀结果：{"id":25,"name":"25"}
2018-05-19T13:15:43.792秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:43.792秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:43.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:43.795秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:43.796秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:43.797秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:43.798秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:43.798秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:44.791秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:44.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:44.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:44.796秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:44.797秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:44.798秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:44.798秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:44.798秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:44.799秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:44.799秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:45.808秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:45.809秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:45.812秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:45.813秒杀结果：{"id":27,"name":"27"}
add goods:{"id":27,"name":"27"}
add goods:{"id":28,"name":"28"}
2018-05-19T13:15:45.814秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:45.814秒杀结果：{"id":28,"name":"28"}
2018-05-19T13:15:45.815秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:45.816秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:45.817秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:45.817秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:46.807秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:46.807秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:46.807秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:46.808秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:46.813秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:46.813秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:46.815秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:46.815秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:46.816秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:46.816秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:47.789秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:47.789秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:47.790秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:47.792秒杀结果：{"id":29,"name":"29"}
2018-05-19T13:15:47.792秒杀结果：{"id":30,"name":"30"}
add goods:{"id":29,"name":"29"}
add goods:{"id":30,"name":"30"}
2018-05-19T13:15:47.793秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:47.795秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:47.796秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:47.797秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:47.797秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:48.790秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:48.791秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:48.792秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:48.797秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:48.797秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:48.798秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:48.800秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:48.800秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:48.801秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:48.801秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:49.789秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:49.790秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:49.791秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:49.793秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:49.794秒杀结果：{"id":31,"name":"31"}
2018-05-19T13:15:49.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:49.795秒杀结果：{"id":32,"name":"32"}
2018-05-19T13:15:49.796秒杀结果：{"error":"Inventory shortage"}
add goods:{"id":31,"name":"31"}
add goods:{"id":32,"name":"32"}
2018-05-19T13:15:49.797秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:49.797秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:50.790秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:50.792秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:50.795秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:50.796秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:50.797秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:50.797秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:50.797秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:50.799秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:50.801秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:50.801秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:51.789秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:51.790秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:51.790秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:51.792秒杀结果：{"id":33,"name":"33"}
2018-05-19T13:15:51.791秒杀结果：{"error":"Inventory shortage"}
add goods:{"id":33,"name":"33"}
2018-05-19T13:15:51.791秒杀结果：{"error":"Inventory shortage"}
add goods:{"id":34,"name":"34"}
2018-05-19T13:15:51.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:51.795秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:51.795秒杀结果：{"id":34,"name":"34"}
2018-05-19T13:15:51.795秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:52.791秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:52.792秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:52.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:52.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:52.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:52.795秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:52.796秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:52.796秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:52.796秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:52.797秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:53.789秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:53.790秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:53.790秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:53.790秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:53.791秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:53.792秒杀结果：{"id":35,"name":"35"}
add goods:{"id":35,"name":"35"}
add goods:{"id":36,"name":"36"}
2018-05-19T13:15:53.792秒杀结果：{"id":36,"name":"36"}
2018-05-19T13:15:53.793秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:53.793秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:53.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:54.788秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:54.789秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:54.791秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:54.791秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:54.793秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:54.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:54.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:54.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:54.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:54.795秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:55.792秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:55.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:55.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:55.794秒杀结果：{"id":37,"name":"37"}
2018-05-19T13:15:55.795秒杀结果：{"id":38,"name":"38"}
2018-05-19T13:15:55.795秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:55.796秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:55.796秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:55.796秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:55.797秒杀结果：{"error":"Inventory shortage"}
add goods:{"id":37,"name":"37"}
add goods:{"id":38,"name":"38"}
2018-05-19T13:15:56.789秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:56.789秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:56.790秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:56.791秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:56.791秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:56.792秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:56.792秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:56.792秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:56.793秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:56.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:57.789秒杀结果：{"error":"Inventory shortage"}
add goods:{"id":39,"name":"39"}
add goods:{"id":40,"name":"40"}
2018-05-19T13:15:57.789秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:57.791秒杀结果：{"id":39,"name":"39"}
2018-05-19T13:15:57.791秒杀结果：{"id":40,"name":"40"}
2018-05-19T13:15:57.792秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:57.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:57.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:57.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:57.796秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:57.796秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:58.789秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:58.789秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:58.790秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:58.790秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:58.791秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:58.792秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:58.793秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:58.793秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:58.793秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:58.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:59.787秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:59.787秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:59.788秒杀结果：{"error":"Inventory shortage"}
add goods:{"id":41,"name":"41"}
add goods:{"id":42,"name":"42"}
2018-05-19T13:15:59.790秒杀结果：{"id":41,"name":"41"}
2018-05-19T13:15:59.791秒杀结果：{"id":42,"name":"42"}
2018-05-19T13:15:59.791秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:59.793秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:59.794秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:59.795秒杀结果：{"error":"Inventory shortage"}
2018-05-19T13:15:59.796秒杀结果：{"error":"Inventory shortage"}

Process finished with exit code 0
```

到这里我们已经通过verticle完成了秒杀这个场景的微服务工程，后面我们开始一个简单微服务工程的创建，并进一步熟悉vert.x。