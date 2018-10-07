package com.dayan.vertx;

import io.vertx.core.Future;
import io.vertx.core.eventbus.Message;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;

public class FUTURE_VERTICLE extends BaseVerticle {
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
        //获取数据库连接
        Future<SQLConnection> connFuture = Future.future(future -> client.getConnection(future));
        //创建表
        Future<Void> createTableFuture = connFuture.compose(conn -> Future.future(future -> conn.execute("create table if not exists test(id int, name varchar(255))", future)));
        //按顺序插入id为1、2、3的数据
        Future<Void> insertFuture1 = createTableFuture.compose(v -> Future.future(future -> connFuture.result().execute("insert into test values(1, 'Hello')", future)));
        Future<Void> insertFuture2 = insertFuture1.compose(v -> Future.future(future -> connFuture.result().execute("insert into test values(2, 'Hello')", future)));
        Future<Void> insertFuture3 = insertFuture2.compose(v -> Future.future(future -> connFuture.result().execute("insert into test values(3, 'Hello')", future)));
        Future<ResultSet> queryFuture = insertFuture3.compose(v -> Future.future(future -> connFuture.result().query("select * from test", future)));
        queryFuture.setHandler(res -> {
            if (res.failed())
                throw new RuntimeException(res.cause());
            message.reply(new JsonObject().put("rows", res.result().getRows()));
        });
    }
}
