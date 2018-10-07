import com.dayan.vertx.*;
import io.vertx.core.CompositeFuture;
import io.vertx.core.Future;
import io.vertx.core.Vertx;
import io.vertx.core.logging.Logger;
import io.vertx.core.logging.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class StartUp {

    public static void main(String[] args) {
        Logger log = LoggerFactory.getLogger(StartUp.class);

        Future<Vertx> vertxFuture = Future.succeededFuture(Vertx.vertx());
        try {

            Future<String> gateWayFuture = Future.future();
            vertxFuture.compose(vertx -> {
                vertx.deployVerticle(APIGatewayVerticle.class.getName(), gateWayFuture);
            }, gateWayFuture);
            List<String> verticles = new ArrayList<>();
            verticles.add(TEST_VERTICLE.class.getName());
            verticles.add(CALL_BACK_HELL_VERTICLE.class.getName());
            verticles.add(FUTURE_VERTICLE.class.getName());
            verticles.add(COROUTINE_VERTICLE.class.getName());
            gateWayFuture.compose(v ->
                    CompositeFuture.join(verticles.stream().map(it ->
                            Future.<String>future(future -> vertxFuture.result().deployVerticle(it, future))).collect(Collectors.toList())))
                    .setHandler(res -> {
                        if (res.succeeded())
                            log.info("all verticle deployed and project started!--with single!");
                        else {
                            log.error(res.cause());
                            System.exit(0);
                        }
                    });
        } catch (Exception e) {
            log.error(e);
            System.exit(0);
        }
    }
}
