
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

### 安装准备

#### 源码下载

因为代码比较大，国内采用镜像下载：

```
git clone https://gitee.com/gorden5566/jdk8u.git
cd jdk8u/
git checkout --track origin/fix
sh ./getModules.sh
```

### 安装依赖

1. 安装freetype

```
brew install freetype
```

2. 安装xcode

直接从 `App Store` 中下载安装 或命令行安装 `xcode-select --install` 

3. 安装gcc编译器

不要安装编译器版本高于5的，因为默认启用c++14 导致编译中断

```
brew install gcc@4.9
```

4. 链接gcc编译器

```
sudo ln -s /usr/local/Cellar/gcc@4.9/4.9.4/bin/gcc-4.9 /usr/bin/gcc
sudo ln -s /usr/local/Cellar/gcc@4.9/4.9.4/bin/g++-4.9 /usr/bin/g++
```

