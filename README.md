# LaunchRedirector

Pre-main agent: intercepts the game launch arguments and relaunches the game with the modified ones.

## 想法

MixinLib的做法可以抽成一个透明的中间层：Agent在P1(JVM1/启动器)中加载SPI，让模组修改P2(JVM2/游戏本体)的JVM参数，基本是一个Wrapper。JVM参数、类路径、游戏特定参数被包装进`LaunchContext`由插件链式修改，最后用它启动P2。目标主类也能改。P1阶段预加载Mod Jar的类路径脏不脏无所谓，反正启动时就关了。

## 用途

在第三方启动器造好前引导NanoForge。

## 插件系统

经典的SPI+责任链，因为被LaunchWrapper腌入味了所以也提供`PreProcess`/`PostProcess`，外加一个排序系统。

```java
public interface ILaunchConfigPlugin {

    int DEFAULT_PRIORITY = 100;

    default void preProcess(Map<String, Integer> plugins) {
        //NOP
    };

    LaunchContext process(MutableLaunchContext ctx);

    default void postProcess(LaunchContext ctx) {
        //NOP
    };

    default int getPriority() {
        Order order = this.getClass().getAnnotation(Order.class);
        return order == null ? DEFAULT_PRIORITY : order.value();
    }

    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    @interface Order {
        int value();
    }
}
```

`process`拿到一个可变CTX，调整后用`build()`返回不可变CTX给LR；`postProcess`在所有插件跑完后拿到最终不可变CTX，它会被组装成启动命令行；`preProcess`拿到全部已知插件的全限定名与优先度，便于构建有状态插件。`Order`注解直接填优先度值即可。

## 责任链契约

插件可以依赖以下行为，改动它们属于破坏性变更。

**执行顺序**

`preProcess`（全部插件，此时还没有CTX）→ `process`（按优先级升序逐个传递CTX）→ `postProcess`（全部插件，拿到最终CTX）。

优先级**数值越大越晚执行**，因此能看到并覆盖更多已修改的上下文；没有`@Order`时取`DEFAULT_PRIORITY`（100）。同优先级的插件按**类名**排序，这是刻意的：`ServiceLoader`不保证迭代顺序。

**失败隔离**

单个插件失败不会中止重启流程：

| 情况 | 行为 |
| --- | --- |
| `getPriority()`抛异常 | 按`DEFAULT_PRIORITY`继续参与 |
| `preProcess`/`postProcess`抛异常 | 记日志，跳过该插件的这一阶段 |
| `process`抛异常或返回`null` | 丢弃该插件的可变CTX，沿用上一个CTX |

唯一的硬失败是拿不到`Instrumentation`：那时没有任何模组jar能被加载。

**上下文**

插件拿到的`MutableLaunchContext`是上一次结果的**独立副本**，插件之间不会互相看到对方正在改的对象。

- 集合型getter返回**不可变快照**，请用`add*`/`remove*`/`with*`方法修改。
- setter做null校验：集合参数为null抛`NullPointerException`，元素为null则丢弃并记日志。
- `build()`校验`javaHome`、`mainClass`、`workingDirectory`，缺失时抛指名道姓的`IllegalStateException`。
- `jvmArgs`与`programArgs`是有序`List`，重复项合法且位置有意义，**不会**去重；类路径有序但去重，因此是`Set`。
- 类路径条目传**目录**时展开为「目录 + `目录/*`」：目录当类根用，`/*`额外收进其中**直接**含有的jar。不递归，目录内无jar则不发`/*`。

> 破坏性变更：`getJvmArgs()`从`Set<String>`变成`List<String>`，集合型getter返回不可变快照。依赖`ctx.getJvmArgs().add(...)`或`instanceof Set`的插件需改用`addJvmArgs(...)`。

**子进程参数回写**

`startFS` / `startSound` / `startRes`既是P1读取配置的来源，也是回写给P2的通道：链跑完后LR把插件改过的值写回这三个属性，插件的修改才能穿过进程边界。同时把阶段标记置为`GAME`，让子进程里被注入的agent保持惰性。
