# LaunchRedirector
Pre-Main Agent to Intercept Game launch Arguments and provide it, Standard ReLauncher component imlp

## 想法
MixinLib的做法可以抽成一个透明的中间层，Agent通过在P1(JVM1/启动器)中加载SPI以允许模组修改P2(JVM2/游戏本体)阶段的JVM参数，基本上是一个Wrapper。

JVM参数，类路径，游戏特定参数将被包装进一个LaunchContext中由插件链式修改，最后LaunchContext将用于启动P2(JVM2/游戏本体)。

这一思路可以视为对游戏启动器的中间人攻击，或者说代理，你可以以程序化方式修改参数，并且是的，目标主类也能改。

顺带一提，P1阶段预加载Mod Jar并使用SPI应该不会造成太多问题，P1的类路径多脏都没问题，反正到了启动阶段是要关的。

## 用途
在第三方启动器造好前这玩意可以拿来引导NanoForge

## 代码呢
滚木中，，，
