首先感谢高铁同学同意我撰写[fastjson](https://github.com/alibaba/fastjson)源码解析，fastjson是一个高效的json与java对象序列化框架，很多公司和开源框架都从fastjson中受益。

目前网上公开的fastjson源码解析太少或者缺少深度，因此我计划通过研究源码的方式并记录下来，让更多想了解底层实现的同学受益。如果在阅读过程中发现错误，欢迎与我沟通 。

```
邮箱：jason.shang@hotmail.com。
微信：skyingshang
```

欢迎点击[fastjson源码分析](https://zonghaishang.gitbooks.io/fastjson-source-code-analysis/content/)在线阅读，已经添加注释的源码请参考[fastjson](https://github.com/zonghaishang/fastjson)。

#### 我在工作之余编写源码解析的目的：

1. 做技术应该追求极致和细节，让更多的人拥抱开源并从中受益
2. 深入理解fastjson作者的设计思想
3. 掌握基本的词法和语法分析实现
4. 源码是最好的教材，降低阅读开源代码的成本
5. 巩固技术基础
6. 分享是一种美德

#### 为了尊重作者的劳动，如果您转载请保留以下内容：

```
文章作者 ： 诣极(商宗海)
框架作者 ： 高铁
文章地址 ： https://zonghaishang.gitbooks.io/fastjson-source-code-analysis/content/
代码地址 ： https://github.com/zonghaishang/fastjson
框架地址 ： https://github.com/alibaba/fastjson
```



