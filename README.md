# LaunchRedirector
Pre-Main Agent to Intercept Game launch Arguments and provide it, Standard ReLauncher component imlp

## 想法
MixinLib的做法可以抽成一个透明的中间层，Agent通过在P1(JVM1/启动器)中加载SPI以允许模组修改P2(JVM2/游戏本体)阶段的JVM参数，基本上是一个Wrapper。

JVM参数，类路径，游戏特定参数将被包装进一个LaunchContext中由插件链式修改，最后LaunchContext将用于启动P2(JVM2/游戏本体)。

这一思路可以视为对游戏启动器的中间人攻击，或者说代理，你可以以程序化方式修改参数，并且是的，目标主类也能改。

顺带一提，P1阶段预加载Mod Jar并使用SPI应该不会造成太多问题，P1的类路径多脏都没问题，反正到了启动阶段是要关的。

## 用途
在第三方启动器造好前这玩意可以拿来引导NanoForge  


LR的插件系统采用经典的SPI+责任链，  
由于我被LaunchWrapper的设计腌入味了所以同时还提供`PreProcess`和`PostProcess`方法，  
以及一个简单的排序系统。


```java
public interface ILaunchConfigPlugin {


    default void preProcess(Map<String, Integer> plugins) {
        //NOP
    };

    LaunchContext process(MutableLaunchContext ctx);

    default void postProcess(LaunchContext ctx) {
        //NOP
    };
    
    default int getPriority() {
        try {
            return this.getClass().getAnnotation(Order.class).value();
        } catch (Exception e) {
            return 100;
        }
    }


    @Target(ElementType.TYPE)
    @Retention(RetentionPolicy.RUNTIME)
    public static @interface Order {
        int value();
    }
}
```

如你所见，process会拿到一个可变的CTX，并对其进行调整，然后使用build方法返回一个不可变CTX给LR。 
而Post Process方法会在所有的插件应用完process方法后拿到最终的不可变CTX，这个CTX将会被组装成启动命令行。

Pre Process方法则会拿到一个带有所以已知插件全限定名称以及其优先度的Map，这将有助于构建有状态插件以及避免潜在的兼容性问题。  
而Order的使用是最简单的，你直接打注解往里面填字就行了。