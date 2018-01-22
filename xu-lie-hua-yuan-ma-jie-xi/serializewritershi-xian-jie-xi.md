## 概要

fastjson核心功能包括序列化和反序列化，序列化的含义是将java对象转换成跨语言的json字符串。我认为从这里作为分析入口相对比较简单，第二章会从反序列化角度切入，会包含词法分析等较为复杂点展开。

现在，我们正式开始咀嚼原汁原味的代码吧。

## SerializeWriter成员变量

com.alibaba.fastjson.serializer.SerializeWriter类非常重要，序列化输出都是通过转换底层操作，重要字段如下：

```
    /** 字符类型buffer */
    private final static ThreadLocal<char[]> bufLocal      = new ThreadLocal<char[]>();
    /** 字节类型buffer */
    private final static ThreadLocal<byte[]> bytesBufLocal = new ThreadLocal<byte[]>();

    /** 存储序列化结果buffer */
    protected char                           buf[];

    /** buffer中包含的字符数 */
    protected int                            count;

    /** 序列化的特性，比如写枚举按照名字还是枚举值 */
    protected int                            features;

    /** 序列化输出器 */
    private final Writer                     writer;

    /** 是否使用单引号输出json */
    protected boolean                        useSingleQuotes;
    /** 输出字段是否追加 "和：字符 */
    protected boolean                        quoteFieldNames;
    /** 是否对字段排序 */
    protected boolean                        sortField;
    /** 禁用字段循环引用探测 */
    protected boolean                        disableCircularReferenceDetect;
    protected boolean                        beanToArray;
    /** 按照toString方式获取对象字面值 */
    protected boolean                        writeNonStringValueAsString;
    /** 如果字段默认值不输出，比如原型int，默认值0不输出 */
    protected boolean                        notWriteDefaultValue;
    /** 序列化枚举时使用枚举name */
    protected boolean                        writeEnumUsingName;
    /** 序列化枚举时使用枚举toString值 */
    protected boolean                        writeEnumUsingToString;
    protected boolean                        writeDirect;
    /** key分隔符，默认单引号是'，双引号是" */
    protected char                           keySeperator;

    protected int                            maxBufSize = -1;

    protected boolean                        browserSecure;
    protected long                           sepcialBits;
```

## SerializeWriter成员函数

### 序列化整形数字

        public void writeInt(int i) {
            /** 如果是整数最小值，调用字符串函数输出到缓冲区*/
            if (i == Integer.MIN_VALUE) {
                write("-2147483648");
                return;
            }

            /** 根据数字判断占用的位数，负数会多一位用于存储字符`-` */
            int size = (i < 0) ? IOUtils.stringSize(-i) + 1 : IOUtils.stringSize(i);

            int newcount = count + size;
            /** 如果当前存储空间不够 */
            if (newcount > buf.length) {
                if (writer == null) {
                    /** 扩容到为原有buf容量1.5倍+1, copy原有buf的字符*/
                    expandCapacity(newcount);
                } else {
                    char[] chars = new char[size];
                    /** 将整数i转换成单字符并存储到chars数组 */
                    IOUtils.getChars(i, size, chars);
                    /** 将chars字符数组内容写到buffer中*/
                    write(chars, 0, chars.length);
                    return;
                }
            }

            /** 如果buffer空间够，直接将字符写到buffer中 */
            IOUtils.getChars(i, newcount, buf);
            /** 重新计数buffer中字符数 */
            count = newcount;
        }



