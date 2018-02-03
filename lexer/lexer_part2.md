
### JSON Token解析

`JSONLexerBase`定义并实现了`json`串实现解析机制的基础，在理解后面反序列化之前，我们先来看看并理解重要的属性：

``` java
    /** 当前token含义 */
    protected int                            token;
    /** 记录当前扫描字符位置 */
    protected int                            pos;
    protected int                            features;

    /** 当前有效字符 */
    protected char                           ch;
    /** 流(或者json字符串)中当前的位置，每次读取字符会递增 */
    protected int                            bp;

    protected int                            eofPos;

    /** 字符缓冲区 */
    protected char[]                         sbuf;

    /** 字符缓冲区的索引，指向下一个可写
     *  字符的位置，也代表字符缓冲区字符数量
     */
    protected int                            sp;

    /**
     * number start position
     * 可以理解为 找到token时 token的首字符位置
     * 和bp不一样，这个不会递增，会在开始token前记录一次
     */
    protected int                            np;
```

### JSONLexerBase成员函数

在开始分析词法分析实现过程中，我发现中解析存在大量重复代码实现或极其类似实现，重复代码主要解决类似c++内联调用，极其相似代码实现我会挑选有代表性的来说明（一般实现较为复杂），没有说明的成员函数可以参考代码注释。


