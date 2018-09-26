package com.dayan.vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.eventbus.EventBus;
import io.vertx.core.eventbus.Message;
import io.vertx.core.eventbus.MessageConsumer;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

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

    protected abstract void handle(Message<JsonObject> message);

    protected Future<Message<JsonObject>> registerDispatcher(JsonArray dispatchers, EventBus eventBus) {
        return Future.future(future -> eventBus.send("SERVICE_ADD_DISPATCHER_ADDRESS", dispatchers, future));
    }

    protected <T> Future<Void> completeConsumer(List<MessageConsumer<T>> consumers) {
        List<Future> list = consumers.stream().map(consumer -> Future.future(consumer::completionHandler)).collect(Collectors.toList());
        return compositeFutureToVoidFuture(CompositeFuture.join(list));
    }

    private static Future<Void> compositeFutureToVoidFuture(CompositeFuture future) {
        Future<Void> voidFuture = Future.future();
        future.setHandler(res -> {
            if (res.succeeded())
                voidFuture.complete();
            else
                voidFuture.fail(res.cause());
        });
        return voidFuture;
    }

}
