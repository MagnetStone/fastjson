## 概要

fastjson核心功能包括序列化和反序列化，序列化的含义是将java对象转换成跨语言的json字符串。我认为从这里作为分析入口相对比较简单，第二章会从反序列化角度切入，会包含词法分析等较为复杂点展开。

现在，我们正式开始咀嚼原汁原味的代码吧，我添加了详细的代码注释。

## SerializeWriter成员变量

com.alibaba.fastjson.serializer.SerializeWriter类非常重要，序列化输出都是通过转换底层操作，重要字段如下：

``` java
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

``` java
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
```
其中值得提一下的是IOUtils.getChars，里面利用了Integer.getChars(int i, int index, char[] buf),主要的思想是整数超过65536 进行除以100, 循环取出数字后两位，依次将个位和十位转换为单字符，如果整数小于等于65536，进行除以10，取出个位数字并转换单字符，getCharts中 q = (i * 52429) >>> (16+3)，可以理解为 (i乘以0.1), 但是精度更高。

### 序列化长整形数字

``` java
    public void writeLong(long i) {
        boolean needQuotationMark = isEnabled(SerializerFeature.BrowserCompatible) //
                                    && (!isEnabled(SerializerFeature.WriteClassName)) //
                                    && (i > 9007199254740991L || i < -9007199254740991L);

        if (i == Long.MIN_VALUE) {
            if (needQuotationMark) write("\"-9223372036854775808\"");
            /** 如果是长整数最小值，调用字符串函数输出到缓冲区*/
            else write("-9223372036854775808");
            return;
        }

        /** 根据数字判断占用的位数，负数会多一位用于存储字符`-` */
        int size = (i < 0) ? IOUtils.stringSize(-i) + 1 : IOUtils.stringSize(i);

        int newcount = count + size;
        if (needQuotationMark) newcount += 2;
        /** 如果当前存储空间不够 */
        if (newcount > buf.length) {
            if (writer == null) {
                /** 扩容到为原有buf容量1.5倍+1, copy原有buf的字符*/
                expandCapacity(newcount);
            } else {
                char[] chars = new char[size];
                /** 将长整数i转换成单字符并存储到chars数组 */
                IOUtils.getChars(i, size, chars);
                if (needQuotationMark) {
                    write('"');
                    write(chars, 0, chars.length);
                    write('"');
                } else {
                    write(chars, 0, chars.length);
                }
                return;
            }
        }

        /** 添加引号 */
        if (needQuotationMark) {
            buf[count] = '"';
            IOUtils.getChars(i, newcount - 1, buf);
            buf[newcount - 1] = '"';
        } else {
            IOUtils.getChars(i, newcount, buf);
        }

        count = newcount;
    }
```
序列化长整型和整型非常类似，增加了双引号判断，采用用了和Integer转换为单字符同样的技巧。

### 序列化浮点类型数字

``` java
    public void writeDouble(double doubleValue, boolean checkWriteClassName) {
        /** 如果doubleValue不合法或者是无穷数，调用writeNull */
        if (Double.isNaN(doubleValue)
                || Double.isInfinite(doubleValue)) {
            writeNull();
        } else {
            /** 将高精度double转换为字符串 */
            String doubleText = Double.toString(doubleValue);
            /** 启动WriteNullNumberAsZero特性，会将结尾.0去除 */
            if (isEnabled(SerializerFeature.WriteNullNumberAsZero) && doubleText.endsWith(".0")) {
                doubleText = doubleText.substring(0, doubleText.length() - 2);
            }

            /** 调用字符串输出方法 */
            write(doubleText);

            /** 如果开启序列化WriteClassName特性，输出Double类型 */
            if (checkWriteClassName && isEnabled(SerializerFeature.WriteClassName)) {
                write('D');
            }
        }
    }
```

### 序列化枚举类型

``` java
    public void writeEnum(Enum<?> value) {
        if (value == null) {
            /** 如果枚举value为空，调用writeNull输出 */
            writeNull();
            return;
        }
        
        String strVal = null;
        /** 如果开启序列化输出枚举名字作为属性值 */
        if (writeEnumUsingName && !writeEnumUsingToString) {
            strVal = value.name();
        } else if (writeEnumUsingToString) {
            /** 采用枚举默认toString方法作为属性值 */
            strVal = value.toString();;
        }

        if (strVal != null) {
            /** 如果开启引号特性，输出json包含引号的字符串 */
            char quote = isEnabled(SerializerFeature.UseSingleQuotes) ? '\'' : '"';
            write(quote);
            write(strVal);
            write(quote);
        } else {
            /** 输出枚举所在的索引号 */
            writeInt(value.ordinal());
        }
    }
```

### 序列化单字符

``` java
    public void write(int c) {
        int newcount = count + 1;
        /** 如果当前存储空间不够 */
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                /** 强制流输出并刷新缓冲区 */
                flush();
                newcount = 1;
            }
        }
        /** 存储单字符到buffer并更新计数 */
        buf[count] = (char) c;
        count = newcount;
    }
```


