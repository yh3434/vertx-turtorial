package com.dayan.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Future;
import io.vertx.core.eventbus.DeliveryOptions;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.http.HttpMethod;
import io.vertx.core.http.HttpServer;
import io.vertx.core.http.HttpServerOptions;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;
import io.vertx.ext.web.handler.BodyHandler;
import io.vertx.ext.web.handler.CorsHandler;

import java.util.HashSet;
import java.util.Set;

public class APIGatewayVerticle extends AbstractVerticle {
    private final Logger log = LoggerFactory.getLogger(APIGatewayVerticle.class);

    private Router router;

    //所有服务启动时，注册到dispatchers上
    private Set<String> dispatchers = new HashSet<>();

    private JsonObject requestBody;

    private EventBus eventBus;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        eventBus = vertx.eventBus();
        int port = 8080;
        router = Router.router(vertx);
        //创建服务的注册consumer
        MessageConsumer<JsonArray> consumer = eventBus.consumer("SERVICE_ADD_DISPATCHER_ADDRESS");
        consumer.handler(this::addDisPatcher);
        enableCorsSupport(router);
        // body handler
        router.route().handler(BodyHandler.create().setBodyLimit(1000000));

        // api dispatcher:所有路由都交由网关来处理
        router.route("/httpserver/api/v").handler(routingContext -> dispatchRequests(routingContext));

        // create http server
        HttpServerOptions options = new HttpServerOptions().setIdleTimeout(10);
        Future.<HttpServer>future(httpServerFuture -> vertx.createHttpServer(options)
                .requestHandler(router::accept)
                .listen(port, httpServerFuture))
                .compose(consume -> Future.future(consumer::completionHandler))
                .setHandler(startFuture.completer());
    }

    private void addDisPatcher(Message<JsonArray> dispatcher) {
        dispatcher.body().forEach(api -> this.dispatchers.add(api.toString()));
        log.info("addDisPatcher:" + dispatchers.toString());
        dispatcher.reply(new JsonObject().put("dispatcher", "add dispatcher succeed!"));
    }

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
                log.error(reply.cause());
                badJsonRequest(routingContext);
            } else {
                successJsonRequest(routingContext, reply.result().body());
            }
        });
    }

    /**
     * Enable CORS support.
     *
     * @param router router instance
     */
    private void enableCorsSupport(Router router) {
        Set<String> allowHeaders = new HashSet<>();
        allowHeaders.add("x-requested-with");
        allowHeaders.add("Access-Control-Allow-Origin");
        allowHeaders.add("Access-Control-Allow-Credentials");
        allowHeaders.add("origin");
        allowHeaders.add("Cookie");
        allowHeaders.add("Content-Type");
        allowHeaders.add("accept");
        Set<HttpMethod> allowMethods = new HashSet<>();
        allowMethods.add(HttpMethod.POST);

        router.route().handler(CorsHandler.create("*")
                .allowedHeaders(allowHeaders)
                .allowedMethods(allowMethods));
    }

    protected void badJsonRequest(RoutingContext context) {
        JsonObject responseBody = new JsonObject().put("success", false).put("error", "bad request");
        context.response().setStatusCode(200)
                .putHeader("content-type", "application/json;charset=utf-8")
                .end(responseBody.toString());
    }

    protected void successJsonRequest(RoutingContext context, JsonObject data) {
        JsonObject responseBody = new JsonObject().put("success", true).put("data", data);
        context.response().setStatusCode(200)
                .putHeader("content-type", "application/json;charset=utf-8")
                .end(responseBody.toString());
    }
}
