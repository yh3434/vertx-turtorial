从这里开始，我们会开始Vert.x的编码学习，直到能搭建出一个完整的微服务项目。

# 回调地狱

使用Future之前我们先开始一段回调地狱的体验，来一张图体验一下：![image-20180913090336183](https://ws3.sinaimg.cn/large/006tNbRwgy1fv8x3z55cgj312e0pwaq3.jpg)

没有写过异步代码得同学可能没有这样的体验，我们来实战（[代码地址](https://github.com/yh3434/vertx-turtorial/tree/master/vertx-future)）一下。

需求：

1. 创建一张表，有id、name两个字段。
2. 按顺序插入id从1到3的数据。
3. 再按顺序查询出id从1到3的数据。

于是乎写出来的代码就成了这样，截取一部分操作数据库的代码：

```java
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
```



这样的编程体验是灾难且不可控的，我们在享受异步编程带来的高效的同时必须要解决这种回调的,Vert.x给我们提供了自己的Monad，就是之前提到的Future。

现在我们用Future来改造上面的回调地狱，源码请看上面地址：

```java
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
```

可以看到同样的结果，我们用了赏心悦目的实现，告别了回调地狱，这就是函数式编程的魅力。有了这个法宝我们就能写出高效且舒服的异步代码。后面我们会实现kotlin的协程版的改造，阅读性比Future这种还要高一点。