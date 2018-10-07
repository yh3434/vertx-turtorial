package com.rbcloud.common

import io.vertx.core.CompositeFuture
import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.eventbus.Message
import io.vertx.core.eventbus.MessageConsumer
import io.vertx.core.json.JsonArray
import io.vertx.core.json.JsonObject
import io.vertx.core.logging.LoggerFactory
import io.vertx.ext.sql.ResultSet
import io.vertx.ext.sql.SQLConnection
import io.vertx.ext.sql.UpdateResult
import io.vertx.kotlin.coroutines.CoroutineVerticle
import io.vertx.kotlin.coroutines.await
import io.vertx.kotlin.coroutines.awaitResult
import io.vertx.kotlin.coroutines.dispatcher
import kotlinx.coroutines.experimental.launch
import kotlin.streams.toList

/**
 * Created by yuhang on 2017/10/24.
 */
abstract class BaseCoroutineVerticle : CoroutineVerticle() {
    private lateinit var dispatchers: JsonArray
    protected val logger = LoggerFactory.getLogger(this.javaClass.simpleName)
    override suspend fun start() {
        super.start()
        val eventBus = vertx.eventBus()
        val consumer = eventBus.consumer<JsonObject>(this.javaClass.simpleName)
        consumer.coroutineHandler(vertx, { handle(it) })
        dispatchers = JsonArray().add(this.javaClass.simpleName)

        //注册接口
        registerDispatcher(dispatchers)
        val joinConsumer = completeConsumer(listOf(consumer)).await()
        if (joinConsumer.failed())
            throw joinConsumer.cause()
        //额外的consumer请在子类调用completeConsumer方法
    }

    private suspend fun registerDispatcher(dispatchers: JsonArray): Message<JsonArray> {
        return awaitResult { vertx.eventBus().send("SERVICE_ADD_DISPATCHER_ADDRESS", dispatchers, it) }
    }

    fun <T : Any> completeConsumer(consumers: List<MessageConsumer<T>>): CompositeFuture {
        val list = consumers.stream().map { consumer -> Future.future<Void>({ consumer.completionHandler(it) }) }.toList()
        return CompositeFuture.join(list)
    }

    abstract suspend fun handle(message: Message<JsonObject>)

    fun <T> MessageConsumer<T>.coroutineHandler(vertx: Vertx, fn: suspend (Message<T>) -> Unit) {
        handler { ctx ->
            launch(vertx.dispatcher()) {
                fn(ctx)
            }
        }
    }

    suspend fun executeQuery(connection: SQLConnection, sql: String, params: JsonArray): ResultSet {
        return awaitResult { connection.queryWithParams(sql, params, it) }
    }

    suspend fun executeUpdate(connection: SQLConnection, sql: String, params: List<Any>): UpdateResult {
        return awaitResult { connection.updateWithParams(sql, JsonArray(params), it) }
    }
}