package vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;

public class HelloVertx extends AbstractVerticle {

    public void start() {
        vertx.createHttpServer().requestHandler(req -> {
            try {
                //模拟业务代码，需要处理一定时间
                Thread.sleep(500);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println(Thread.currentThread().getName());
            req.response()
                    .putHeader("content-type", "text/plain")
                    .end("Hello from Vert.x!!!");
        }).listen(8080);
    }

    public static void main(String[] args) {
        Vertx vertx = Vertx.vertx();
        vertx.deployVerticle(HelloVertx.class.getName());
    }
}
