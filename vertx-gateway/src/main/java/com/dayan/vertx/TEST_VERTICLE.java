package com.dayan.vertx;

import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;

public class TEST_VERTICLE extends BaseVerticle {

    @Override
    protected void handle(Message<JsonObject> message) {
        message.reply(new JsonObject().put("test", "test"));
    }
}
