package com.dayan.vertx

import com.rbcloud.common.BaseCoroutineVerticle
import io.vertx.core.eventbus.Message
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.ext.jdbc.JDBCClient
import io.vertx.ext.sql.SQLConnection
import io.vertx.kotlin.coroutines.awaitResult

class COROUTINE_VERTICLE : BaseCoroutineVerticle() {
    lateinit var client: JDBCClient

    override suspend fun start() {
        super.start()
        client = JDBCClient.createShared(vertx, JsonObject()
                .put("url", "jdbc:hsqldb:mem:test?shutdown=true")
                .put("driver_class", "org.hsqldb.jdbcDriver")
                .put("max_pool_size", 30)
                .put("user", "SA")
                .put("password", ""))
    }

    override suspend fun handle(message: Message<JsonObject>) {
        var connection: SQLConnection? = null
        try {
            connection = awaitResult { client.getConnection(it) }
            connection.use {
                executeUpdate(connection!!, "create table if not exists test(id int, name varchar(255))", emptyList())
                executeUpdate(connection!!, "insert into test values(?, ?)", listOf("1", "hello"))
                executeUpdate(connection!!, "insert into test values(?, ?)", listOf("2", "hello2"))
                executeUpdate(connection!!, "insert into test values(?, ?)", listOf("3", "hello3"))
                val rows = executeQuery(connection!!, "select * from test", JsonArray()).rows
                message.reply(JsonObject().put("rows", rows))
            }
        } finally {
            if (connection != null)
                connection.close()
        }
    }
}