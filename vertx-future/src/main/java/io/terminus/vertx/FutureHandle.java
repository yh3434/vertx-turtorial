package io.terminus.vertx;

import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.ResultSet;
import io.vertx.ext.sql.SQLConnection;

public class FutureHandle {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        final JDBCClient client = JDBCClient.createShared(vertx, new JsonObject()
                .put("url", "jdbc:hsqldb:mem:test?shutdown=true")
                .put("driver_class", "org.hsqldb.jdbcDriver")
                .put("max_pool_size", 30)
                .put("user", "SA")
                .put("password", ""));
        //获取数据库连接
        Future<SQLConnection> connFuture = Future.future(future -> client.getConnection(future));
        //创建表
        Future<Void> createTableFuture = connFuture.compose(conn -> Future.future(future -> conn.execute("create table test(id int primary key, name varchar(255))", future)));
        //按顺序插入id为1、2、3的数据
        Future<Void> insertFuture1 = createTableFuture.compose(v -> Future.future(future -> connFuture.result().execute("insert into test values(1, 'Hello')", future)));
        Future<Void> insertFuture2 = insertFuture1.compose(v -> Future.future(future -> connFuture.result().execute("insert into test values(2, 'Hello')", future)));
        Future<Void> insertFuture3 = insertFuture2.compose(v -> Future.future(future -> connFuture.result().execute("insert into test values(3, 'Hello')", future)));
        Future<ResultSet> queryFuture1 = insertFuture3.compose(v -> Future.future(future -> connFuture.result().query("select * from test where id = 1", future)));
        Future<ResultSet> queryFuture2 = queryFuture1.compose(v -> Future.future(future -> connFuture.result().query("select * from test where id = 2", future)));
        Future<ResultSet> queryFuture3 = queryFuture2.compose(v -> Future.future(future -> connFuture.result().query("select * from test where id = 3", future)));
        queryFuture3.setHandler(res -> {
            if (res.failed())
                throw new RuntimeException(res.cause());
            //id为1的结果
            System.out.println(queryFuture1.result().getResults());
            //id为2的结果
            System.out.println(queryFuture2.result().getResults());
            //id为4的结果
            System.out.println(res.result().getResults());
        });
    }
}
