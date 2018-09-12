# 最好的文档

​	接触到Vert.x已经有两年了，从3.2到现在的3.5.3。平时经常看到有人在社区群或者网上提问怎么vert.x怎么入门，或者哪里有入门教程,其实Vert.x的[官放文档](https://vertx.io/docs/#explore)无论是界面的赏心悦目还是例子的覆盖程度，都算是比较清楚明了。无疑这是学习Vert.x最好的地方，如果你的英文很差可以选择社区翻译的[中文版本](https://vertxchina.github.io/vertx-translation-chinese/)。

![documents](https://ws4.sinaimg.cn/large/0069RVTdgy1fv4qm574elj31kw1037dd.jpg)

# 定位

​	Eclipse **Vert.x** is a tool-kit for building **reactive** applications on the **JVM**.

​	上面是官网对Vert.x的定位，首先定位是tool-kit而不是framework。框架总是给人笨重的感觉，vert.x的定位不是框架还是一个工具包。你可以拿它去写一个Web服务、TCP服务，也可以用它的异步htppClient去高效的完成网络请求，甚至你可以只用它去做一个定时任务。这就是工具的定位，取你想用的。

​	第二关键字是reactive，vert.x的世界里到处都是异步代码，这意味着你的应用程序可以使用少量内核线程处理大量并发。

​	第三个关键字是JVM，你可以用任何jvm语言来完成你的程序，比如**Java**, **Groovy**, **Ruby**, **Ceylon**, **Scala** 和 **Kotlin**，不同于以前的多语言只是简单的互相调用，依托于eventbus(后面会讲解)，甚至可以一个语言完成一个“服务”。

## Hello Vert.x

​	上面说了一些vert.x的关键字，可能看起来比较抽象，下面我们开始用代码感受vert.x带来的异步编程的趣味。这个系列的文章示例代码都在这个[链接](https://github.com/yh3434/tutorial)里。

### 启动

1. 拉取示例工程

   ```shell
   git clone https://github.com/yh3434/vertx-turtorial.git
   ```

2. 打包

   ```shell
    mvn clean package -DskipTests=true
   ```

3. 启动工程

   ```
    cd vertx-hello/target
   ```

   ```shell
   java -jar vertx-shade.jar
   ```

此时打开浏览器分别输入：

* http://localhost:8080
* http://localhost:8081

可以看到两个hello vert.x。下面我们来分析下怎么实现这两个个Hello的。

## 实现

我们打开github上的代码看下实现。

1. 打开vertx-hello模块，可以看到工程如下：

   ![vertx-hello](https://ws1.sinaimg.cn/large/0069RVTdgy1fv5qz2wtn6j31kw0kd12f.jpg)

2. 如果你之前没有写过“fluent”或者异步代码，可以直观的感受到和之前同步的代码有些区别。


恭喜你到这里你就用java和kotlin两种语言监听了8080和8081两个端口，并开启了web服务！后面的文章我会详细介绍vert.x的异步思想和线程模型等知识，并搭建一个微服务工程。