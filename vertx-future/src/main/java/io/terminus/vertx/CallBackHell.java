package io.terminus.vertx;

import io.vertx.core.Vertx;
import io.vertx.core.json.JsonArray;
import io.vertx.core.json.JsonObject;
import io.vertx.ext.jdbc.JDBCClient;
import io.vertx.ext.sql.SQLConnection;

public class CallBackHell {
    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        final JDBCClient client = JDBCClient.createShared(vertx, new JsonObject()
                .put("url", "jdbc:hsqldb:mem:test?shutdown=true")
                .put("driver_class", "org.hsqldb.jdbcDriver")
                .put("max_pool_size", 30)
                .put("user", "SA")
                .put("password", ""));

        client.getConnection(conn -> {
            if (conn.failed()) {
                System.err.println(conn.cause().getMessage());
                return;
            }

            final SQLConnection connection = conn.result();
            connection.execute("create table test(id int primary key, name varchar(255))", res -> {
                if (res.failed()) {
                    throw new RuntimeException(res.cause());
                }
                // insert some test data
                connection.execute("insert into test values(1, 'Hello')", insert -> {
                    connection.execute("insert into test values(2, 'Hello')", insert2 -> {
                        connection.execute("insert into test values(3, 'Hello')", insert3 -> {
                            // query some data
                            connection.query("select * from test where id = 1", rs -> {
                                for (JsonArray line : rs.result().getResults()) {
                                    System.out.println(line.encode());
                                }
                                connection.query("select * from test where id = 2", rs2 -> {
                                    for (JsonArray line : rs2.result().getResults()) {
                                        System.out.println(line.encode());
                                    }
                                    connection.query("select * from test where id = 3", rs3 -> {
                                        for (JsonArray line : rs3.result().getResults()) {
                                            System.out.println(line.encode());
                                        }
                                        // and close the connection
                                        connection.close(done -> {
                                            if (done.failed()) {
                                                throw new RuntimeException(done.cause());
                                            }
                                        });
                                    });
                                });
                            });
                        });
                    });
                });
            });
        });
    }
}
