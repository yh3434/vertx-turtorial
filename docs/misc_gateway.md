# 微服务

在上一篇的简答的秒杀系统中我们已经实现了一个简单的微服务，但是细细思考一下是有很多问题的。

* 所有http接口都在不同的路由中，接口调用方（eg.前端）需要请求不同的端口，非常混乱。
* 没有办法做限流、服务发现、降级等操作。
* 接口返回数据不统一，后端服务难以专注于业务开发。

现在我们来利用vert.x高效的reactor模型来实现一个网关,由它统一接受http请求，并转发到各个service，最后再交由网关来返回给调用方，[源码](../vertx-gateway)请点击链接查看。

## 网关设置

* 路由：统一路由为“/httpserver/api/v”，所有路由都交由网关处理

* 入参协议：

  ```json
  {
  	"method":"",//method为方法,为了简单我们约定服务的类名作为method，统一post到网关
  	"params":{
  	}
  }
  ```

* 出参：

  请求成功为：

  ```json
  {
      "success": true,
      "data": {
      }
  }
  ```

  请求失败为：

  ```json
  {
      "success": false,
      "error": "bad request"
  }
  ```

## 分析

路由代码为：

```java
// body handler
router.route().handler(BodyHandler.create().setBodyLimit(1000000));

// api dispatcher:所有路由都交由网关来处理
router.route("/httpserver/api/v").handler(routingContext -> dispatchRequests(routingContext));
```

dispatcherRequests来分发请求，解析method后通过eventbus发送到service的地址

```java
private void dispatchRequests(RoutingContext routingContext) {
        switch (routingContext.request().method()) {
            //只允许post协议
            case POST:
                String bodyAsString = routingContext.getBodyAsString();
                try {
                    requestBody = new JsonObject(bodyAsString);
                } catch (Exception e) {
                    badJsonRequest(routingContext);
                    return;
                }
                break;
            default:
                badJsonRequest(routingContext);
                return;
        }
        //根据method来转发
        String method = requestBody.getString("method");

        if (!this.dispatchers.contains(method)) {
            badJsonRequest(routingContext);
            return;
        }
        DeliveryOptions options = new DeliveryOptions();
        options.setSendTimeout(5000);
        eventBus.<JsonObject>send(method, requestBody.getJsonObject("params"), options, reply -> {
            if (reply.failed()) {
                reply.cause().printStackTrace();
                badJsonRequest(routingContext);
            } else {
                successJsonRequest(routingContext, reply.result().body());
            }
        });
    }
```

创建BaseVerticle，服务启动时将以类名为地址的consumer注册到eventbus

```java
public abstract class BaseVerticle extends AbstractVerticle {

    protected List<MessageConsumer<JsonObject>> consumers = new ArrayList<>();

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        super.start();
        EventBus eventBus = vertx.eventBus();
        MessageConsumer<JsonObject> consumer = eventBus.consumer(this.getClass().getSimpleName());
        consumer.handler(this::handle);
        eventBus = vertx.eventBus();
        JsonArray dispatchers = new JsonArray().add(this.getClass().getSimpleName());
        consumers.add(consumer);
        //保证consumer完全注册成功
        registerDispatcher(dispatchers, eventBus)
                .compose(v -> completeConsumer(consumers))
                .setHandler(startFuture.completer());
    }
    ...
```

这就是整个核心的流程，详细解析请看源码注释。现在我们启动StartUp的main函数，打印出以下信息代表启动成功：

```java
May 26, 2018 7:46:02 AM com.dayan.vertx.APIGatewayVerticle
INFO: addDisPatcher:[TEST_VERTICLE]
May 26, 2018 7:46:02 AM StartUp
INFO: all verticle deployed and project started!--with single!
```

接下来我们在postman中模拟请求通过网关发送到TEST_VERTICLE

![post](https://ws3.sinaimg.cn/large/006tNbRwgy1fvmnr0dt0hj31kw10pgpm.jpg)

我们的网关就编码完成了，并且模拟了一个简单的TEST服务。

## kotlin CoroutineVerticle

在之前我们已经看到过由回调过度到future的写法，我们先用之前的例子简单的分写两个servie，一个是“回调地狱”写的[服务](../vertx-gateway/src/main/java/com/dayan/vertx/CALL_BACK_HELL_VERTICLE.java)，一个是future模式的[服务](../vertx-gateway/src/main/java/com/dayan/vertx/FUTURE_VERTICLE.java)。启动后和上面TEST_VERTICLE一样测试一下，可以见到结果，这里省略请求过程，请查看源码。

现在我们用koltin的协程写一个服务，源码点击[查看](../vertx-gateway/src/main/kotlin/com/dayan/vertx/COROUTINE_VERTICLE.kt)

![coroutine](https://ws2.sinaimg.cn/large/006tNbRwgy1fw006r4gaqj31kw0x3nin.jpg)

纵向比较一下，我们看到协程和vert.x异步编程的完美结合：

* 可读性强
* 回到同步模式写代码，编码友好
* 由于是用户态线程，不存在阻塞的问题

到这里我们已经完整的实现了网关和三种不同编码方式的服务。