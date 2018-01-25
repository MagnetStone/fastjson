
## macOS High Sierra 编译openjdk 8

本次编译使用的系统是 `macOS High Sierra`，版本为 `10.13.2`。使用的 jdk 是 openjdk 8 。

### 概述

openjdk 的模块，部分使用 C/C++ 编写实现，部分使用 Java 实现。因此除了需要 C/C++ 相关编译工具外，还需要有一个 JDK (Bootstrap JDK)。编译 openjdk8 时可使用 jdk1.7 作为 Bootstrap JDK 。

我当前系统已经安装了jdk1.7 ：

```
$ java -version
java version "1.7.0_79"
Java(TM) SE Runtime Environment (build 1.7.0_79-b15)
Java HotSpot(TM) 64-Bit Server VM (build 24.79-b02, mixed mode)
```