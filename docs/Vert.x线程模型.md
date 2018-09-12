# 做出假设

通过第一篇文章我们已经成功的创建了一个web服务，现在我们改造一下上个工程，源码在[github](https://github.com/yh3434/vertx-turtorial/tree/master/vertx-thread-model)上。在处理请求的地方模拟处理业务的时间，加上：Thread.sleep(500);

```java
package vertx;

import io.vertx.core.AbstractVerticle;
import io.vertx.core.Vertx;

public class HelloVertx extends AbstractVerticle {

    public void start() {
        vertx.createHttpServer().requestHandler(req -> {
            try {
                //模拟业务代码，需要处理一定时间。可以尝试1000、2000、3000
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
```

点击main函数启动后，打开浏览器输入：http://localhost:8080，我们可以看到当sleep超过2000ms时，控制台开始有下面的警告输出：

```shell
vert.x-eventloop-thread-0
May 11, 2018 9:54:57 PM io.vertx.core.impl.BlockedThreadChecker
WARNING: Thread Thread[vert.x-eventloop-thread-0,5,main] has been blocked for 2272 ms, time limit is 2000
vert.x-eventloop-thread-0
vert.x-eventloop-thread-0
May 11, 2018 9:55:04 PM io.vertx.core.impl.BlockedThreadChecker
WARNING: Thread Thread[vert.x-eventloop-thread-0,5,main] has been blocked for 2262 ms, time limit is 2000
vert.x-eventloop-thread-0
vert.x-eventloop-thread-0
May 11, 2018 9:55:07 PM io.vertx.core.impl.BlockedThreadChecker
WARNING: Thread Thread[vert.x-eventloop-thread-0,5,main] has been blocked for 2270 ms, time limit is 2000
vert.x-eventloop-thread-0
vert.x-eventloop-thread-0
```

并且，等到上一个请求处理(sleep)完成时，下一个请求才会进来，即线程名才会被打印出来。我们通过程序的结果来做出以下假设：

1. 请求是按顺序处理的，即：requestHandler内的代码只能由一个线程处理，所以requestHandler内的代码是线程安全的，我们可以大胆的用HashMap、List之类的数据结构。
2. 请求处理超时会有警告，默认情况当sleep超过2000ms时警告必现，这个推断和第一条是相关的，所以我们的业务代码执行时间不能太长，否则会阻塞其他请求。

# 分析假设

我们分析一下通过上面的实验得出的两个结论：

	一定时间内能处理的请求数量=时间/每个请求处理时间的平均数

	因此当请求处理时间越小，系统能处理的请求数量越多，但是怎么减少请求处理时间呢？业务中有很多可能，比如做一个网络请求，调用一个远程rpc,去查询一下数据库，好像也避免不了。于是异步的概念便顺势提出来了，我们设想一下，如果网络请求或者查询数据库时将线程切换出去继续接受下一个请求，同时将网络请求或者数据库查询交出去（暂且不管交给谁），等到处理回调时再切换到当前线程。这样系统的吞吐量变大大提升了。

	事实上，vert.x就是这么做的。与传统servlet线程模型比较下：

|            | servlet | ver.,x |
| ---------- | ------- | ------ |
| 线程安全   | 否      | 是     |
| 吞吐量     | 高      | 低     |
| 需要线程数 | 多      | 低     |

	因此只需上面只要实现异步的带回调网络请求、数据库查询，我们变可以得到以下好处：

1. 极少的线程数量变可以获得极大的吞吐量，减少大量线程切换带来的系统损耗以及大量线程占用的内存。

2. 优雅的实现线程安全，无需加锁、同步之类的代码，因为总是线程安全的。

​	但是这样的设计不是银弹，同时也会带来一些比如异步代码回调嵌套回调等问题，后面的文章会一一举例vert.x怎么解决这些问题。

# 验证假设

以上都是通过代码的结果推论出来的，下面我们看看Vert.x的实现。

