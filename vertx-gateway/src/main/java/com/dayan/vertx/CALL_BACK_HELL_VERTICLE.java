package com.dayan.vertx;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

public class CALL_BACK_HELL_VERTICLE extends BaseVerticle {
    JDBCClient client;

    @Override
    public void start(Future<Void> startFuture) throws Exception {
        super.start(startFuture);
        client = JDBCClient.createShared(vertx, new JsonObject()
                .put("url", "jdbc:hsqldb:mem:test?shutdown=true")
                .put("driver_class", "org.hsqldb.jdbcDriver")
                .put("max_pool_size", 30)
                .put("user", "SA")
                .put("password", ""));
    }

    @Override
    protected void handle(Message<JsonObject> message) {
        client.getConnection(conn -> {
            if (conn.failed()) {
                throw new RuntimeException(conn.cause());
            }

            final SQLConnection connection = conn.result();
            connection.execute("create table if not exists test(id int, name varchar(255))", res -> {
                if (res.failed()) {
                    throw new RuntimeException(res.cause());
                }
                // insert some test data
                connection.execute("insert into test values(1, 'Hello')", insert -> {
                    connection.execute("insert into test values(2, 'Hello')", insert2 -> {
                        connection.execute("insert into test values(3, 'Hello')", insert3 -> {
                            // query some data
                            connection.query("select * from test", rs -> {
                                if (rs.failed())
                                    throw new RuntimeException(rs.cause());
                                else
                                    message.reply(new JsonObject().put("rows", rs.result().getRows()));
                            });
                        });
                    });
                });
            });
        });
    }
}
