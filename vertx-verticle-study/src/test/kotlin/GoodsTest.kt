import io.vertx.core.Future
import io.vertx.core.Vertx
import io.vertx.core.buffer.Buffer
import io.vertx.core.json.JsonObject
import io.vertx.ext.unit.TestContext
import io.vertx.ext.unit.junit.VertxUnitRunner
import io.vertx.ext.web.client.HttpResponse
import io.vertx.ext.web.client.WebClient
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDateTime

@RunWith(VertxUnitRunner::class)
class GoodsTest {
    private lateinit var vertx: Vertx
    private lateinit var webClient: WebClient

    @Before
    fun setUp() {
        vertx = Vertx.vertx()
        webClient = WebClient.create(vertx)
    }

    @After
    fun tearDown(context: TestContext) {
        vertx.close(context.asyncAssertSuccess<Void>())
    }

    @Test
    fun testApplication(context: TestContext) {
        val async = context.async()
        var loopCount = 10

        vertx.setPeriodic(1000) {
            //开始抢购
            val threads: Array<Thread> = Array(10) {
                Thread(Runnable {
                    val eachFuture = Future.future<HttpResponse<Buffer>> { future -> webClient.post(8081, "127.0.0.1", "/api/goods").sendJsonObject(JsonObject(), future) }
                    eachFuture.setHandler {
                        if (it.succeeded())
                            println(LocalDateTime.now().toString() + "秒杀结果：${it.result().bodyAsString()}")
                        else
                            println(it.cause())
                    }
                })
            }
            for (i in 0 until threads.size) {
                threads[i].start()
            }
        }

        vertx.setPeriodic(2000) {
            if (loopCount == 0)
                vertx.cancelTimer(it)
            loopCount--
            //增加2个库存
            val futures = mutableListOf<Future<HttpResponse<Buffer>>>()
            for (i in 0 until 2)
                futures.add(Future.future<HttpResponse<Buffer>> { future -> webClient.post(8082, "127.0.0.1", "/api/goods").sendJsonObject(JsonObject(), future) }.setHandler {
                    println("add goods:${it.result().bodyAsString()}")
                })
        }

        vertx.setTimer(20_000) {
            async.complete()
        }
    }
}