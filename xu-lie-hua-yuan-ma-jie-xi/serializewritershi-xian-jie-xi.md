## 概要

fastjson核心功能包括序列化和反序列化，序列化的含义是将java对象转换成跨语言的json字符串。我认为从这里作为分析入口相对比较简单，第二章会从反序列化角度切入，会包含词法分析等较为复杂点展开。

现在，我们正式开始咀嚼原汁原味的代码吧，我添加了详细的代码注释。

## SerializeWriter成员变量

com.alibaba.fastjson.serializer.SerializeWriter类非常重要，序列化输出都是通过转换底层操作，重要字段如下：

```java
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

```java
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

其中值得提一下的是IOUtils.getChars，里面利用了Integer.getChars\(int i, int index, char\[\] buf\),主要的思想是整数超过65536 进行除以100, 循环取出数字后两位，依次将个位和十位转换为单字符，如果整数小于等于65536，进行除以10，取出个位数字并转换单字符，getCharts中 q = \(i \* 52429\) &gt;&gt;&gt; \(16+3\)，可以理解为 \(i乘以0.1\), 但是精度更高。

### 序列化长整形数字

```java
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

```java
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

     public void writeFloat(float value, boolean checkWriteClassName) {
        /** 如果value不合法或者是无穷数，调用writeNull */
        if (Float.isNaN(value) //
                || Float.isInfinite(value)) {
            writeNull();
        } else {
            /** 将高精度float转换为字符串 */
            String floatText= Float.toString(value);
            /** 启动WriteNullNumberAsZero特性，会将结尾.0去除 */
            if (isEnabled(SerializerFeature.WriteNullNumberAsZero) && floatText.endsWith(".0")) {
                floatText = floatText.substring(0, floatText.length() - 2);
            }
            write(floatText);

            /** 如果开启序列化WriteClassName特性，输出float类型 */
            if (checkWriteClassName && isEnabled(SerializerFeature.WriteClassName)) {
                write('F');
            }
        }
    }
```

序列化浮点类型的基本思路是先转换为字符串，然后在输出到输出流中。

### 序列化枚举类型

```java
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

```java
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

### 序列化16进制字节数组

``` java
    public void writeHex(byte[] bytes) {
        /** 计算总共字符长度, 乘以2 代表一个字符要占用2字节, 3代表要添加 x 和 前后添加' */
        int newcount = count + bytes.length * 2 + 3;
        if (newcount > buf.length) {
            if (writer != null) {
                char[] chars = new char[bytes.length + 3];
                int pos = 0;
                chars[pos++] = 'x';
                chars[pos++] = '\'';

                for (int i = 0; i < bytes.length; ++i) {
                    byte b = bytes[i];

                    int a = b & 0xFF;
                    /** 取字节的高四位 1111 0000*/
                    int b0 = a >> 4;
                    /** 取字节的低四位 0000 1111*/
                    int b1 = a & 0xf;

                    /** 索引低索引存储字节高位
                     *  如果4位表示的数字是 0~9, 转换为ascii的 0~9
                     *  如果4位表示的不是数字, 转换为16进制ascii码字符
                     */
                    chars[pos++] = (char) (b0 + (b0 < 10 ? 48 : 55));
                    chars[pos++] = (char) (b1 + (b1 < 10 ? 48 : 55));
                }
                chars[pos++] = '\'';
                try {
                    writer.write(chars);
                } catch (IOException ex) {
                    throw new JSONException("writeBytes error.", ex);
                }
                return;
            }
            /** buffer容量不够并且输出器为空，触发扩容 */
            expandCapacity(newcount);
        }

        buf[count++] = 'x';
        buf[count++] = '\'';

        for (int i = 0; i < bytes.length; ++i) {
            byte b = bytes[i];

            int a = b & 0xFF;
            /** 取字节的高四位 */
            int b0 = a >> 4;
            /** 取字节的低四位 */
            int b1 = a & 0xf;

            /** 索引低索引存储字节高位
             *  如果4位表示的数字是 0~9, 转换为ascii的 0~9
             *  如果4位表示的不是数字, 转换为16进制ascii码字符
             */
            buf[count++] = (char) (b0 + (b0 < 10 ? 48 : 55));
            buf[count++] = (char) (b1 + (b1 < 10 ? 48 : 55));
        }
        buf[count++] = '\'';
    }
```

writeHex 这个序列化方法主要对16进制的自己转换为占用2个ascii码字符，添加单引号和x前缀。

### 序列化byte字节数组

``` java
   public void writeByteArray(byte[] bytes) {
        if (isEnabled(SerializerFeature.WriteClassName.mask)) {
            /** 如果开启序列化特性WriteClassName，直接写16进制字符 */
            writeHex(bytes);
            return;
        }

        int bytesLen = bytes.length;
        final char quote = useSingleQuotes ? '\'' : '"';
        if (bytesLen == 0) {
            String emptyString = useSingleQuotes ? "''" : "\"\"";
            /** 如果字节数组长度为0，输出空白字符 */
            write(emptyString);
            return;
        }

        final char[] CA = IOUtils.CA;

        /** 验证长度是24bit位整数倍 */
        int eLen = (bytesLen / 3) * 3;
        /** base64 编码字符长度
         *
         *  base64 :
         *  第一步，将每三个字节作为一组，一共是24个二进制位。
         *  第二步，将这24个二进制位分为四组，每个组有6个二进制位。
         *  第三步，在每组前面加两个00，扩展成32个二进制位，即四个字节。
         *  第四步，根据下表，得到扩展后的每个字节的对应符号，这就是Base64的编码值。
         *
         *  ref: http://www.ruanyifeng.com/blog/2008/06/base64.html
         */
        int charsLen = ((bytesLen - 1) / 3 + 1) << 2;
        // char[] chars = new char[charsLen];
        int offset = count;
        int newcount = count + charsLen + 2;
        if (newcount > buf.length) {
            if (writer != null) {
                write(quote);

                for (int s = 0; s < eLen;) {
                    /** 三个字节为一组, 扩展为四个字节 */
                    int i = (bytes[s++] & 0xff) << 16 | (bytes[s++] & 0xff) << 8 | (bytes[s++] & 0xff);

                    write(CA[(i >>> 18) & 0x3f]);
                    write(CA[(i >>> 12) & 0x3f]);
                    write(CA[(i >>> 6) & 0x3f]);
                    /** 填充00 */
                    write(CA[i & 0x3f]);
                }

                /** 对齐并编码剩余不足3个字节为一组的数据 */
                // Pad and encode last bits if source isn't even 24 bits.
                int left = bytesLen - eLen; // 0 - 2.
                if (left > 0) {

                    /**
                     * a) 1个字节的情况：
                     * 将这1字节8位二进制，每6位分成2组，最后一组除了前面加00，后面加上0000，
                     * 这样得到 两位的Base64编码， 在末尾补上2个"="号
                     *
                     * b) 2个字节的情况：
                     * 将这2字节的一共16个二进制位，每6位分成3组，最后一组除了前面加00，后面也要加00，
                     * 这样得到 3位的Base64编码， 在末尾补上"="号
                     *
                     *
                     * 如果只有1个字节，按照前面规则a)
                     * 第1组是6位，第2组后面4个0， 因此应该左移 10 = 6 + 4
                     *
                     * 如果只有2个字节，按照前面规则b)
                     * 第1个字节左移 10 位 加上 第2个字节左移 2 位补0即可
                     */
                    int i = ((bytes[eLen] & 0xff) << 10) | (left == 2 ? ((bytes[bytesLen - 1] & 0xff) << 2) : 0);

                    /** 扩展为四个字节 */
                    write(CA[i >> 12]);
                    write(CA[(i >>> 6) & 0x3f]);
                    write(left == 2 ? CA[i & 0x3f] : '=');
                    write('=');
                }

                write(quote);
                return;
            }
            expandCapacity(newcount);
        }
        count = newcount;
        buf[offset++] = quote;

        // Encode even 24-bits
        for (int s = 0, d = offset; s < eLen;) {
            /** 三个字节为一组, 扩展为四个字节 */
            int i = (bytes[s++] & 0xff) << 16 | (bytes[s++] & 0xff) << 8 | (bytes[s++] & 0xff);

            // Encode the int into four chars
            buf[d++] = CA[(i >>> 18) & 0x3f];
            buf[d++] = CA[(i >>> 12) & 0x3f];
            buf[d++] = CA[(i >>> 6) & 0x3f];
            /** 填充00 */
            buf[d++] = CA[i & 0x3f];
        }

        /** 对齐并编码剩余不足3个字节为一组的数据 */
        int left = bytesLen - eLen; // 0 - 2.
        if (left > 0) {
            // Prepare the int
            int i = ((bytes[eLen] & 0xff) << 10) | (left == 2 ? ((bytes[bytesLen - 1] & 0xff) << 2) : 0);

            /** 扩展为四个字节 */
            buf[newcount - 5] = CA[i >> 12];
            buf[newcount - 4] = CA[(i >>> 6) & 0x3f];
            buf[newcount - 3] = left == 2 ? CA[i & 0x3f] : '=';
            buf[newcount - 2] = '=';
        }
        buf[newcount - 1] = quote;
    }
```

writeByteArray序列化字节数组实际上就是做了base64编码转换，代码添加了详尽的注释帮助理解。

### 序列化Null

```java
    public void writeNull() {
        /** 调用输出字符串null */
        write("null");
    }
```

### 序列化Boolean

```java
    public void write(boolean value) {
        if (value) {
            /** 输出true字符串 */
            write("true");
        } else {
            /** 输出false字符串 */
            write("false");
        }
    }
```

### 序列化字符串

```java
    public void write(String str, int off, int len) {
        /** 计算总共字符串长度 */
        int newcount = count + len;
        /** 如果当前存储空间不够 */
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                /**
                 * 如果字符串str超过缓冲区大小, 进行循环拷贝
                 */
                do {
                    /** 计算当前buffer剩余容纳字符数 */
                    int rest = buf.length - count;
                    /** 将字符串str[off, off + rest) 拷贝到buf[count, ...]中*/
                    str.getChars(off, off + rest, buf, count);
                    count = buf.length;
                    /** 强制刷新输出流，会重置count = 0 */
                    flush();
                    /** 计算剩余需要拷贝的字符数量 */
                    len -= rest;
                    /** 剩余要拷贝字符在str中偏移量(索引) */
                    off += rest;
                } while (len > buf.length);
                newcount = len;
            }
        }
        /** 存储空间充足，直接将str[off, off + len) 拷贝到buf[count, ...]中*/
        str.getChars(off, off + len, buf, count);
        count = newcount;
    }
```

序列化字符串write\(string\),最终都会转化为上面形式write\(string, 0, string.length\)。

### 序列化字符数组

```java
    public void write(char c[], int off, int len) {
        if (off < 0 //
            || off > c.length //
            || len < 0 //
            || off + len > c.length //
            || off + len < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        /** 计算总共字符串长度 */
        int newcount = count + len;
        /** 如果当前存储空间不够 */
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                /**
                 * 如果字符数组c超过缓冲区大小, 进行循环拷贝
                 */
                do {
                    /** 计算当前buffer剩余容纳字符数 */
                    int rest = buf.length - count;
                    /** c[off, off + rest) 拷贝到buf[count, ...]中*/
                    System.arraycopy(c, off, buf, count, rest);
                    count = buf.length;
                    /** 强制刷新输出流，会重置count = 0 */
                    flush();
                    /** 计算剩余需要拷贝的字符数量 */
                    len -= rest;
                    /** 剩余要拷贝字符在c中偏移量(索引) */
                    off += rest;
                } while (len > buf.length);
                newcount = len;
            }
        }
        System.arraycopy(c, off, buf, count, len);
        count = newcount;
    }
```

### 序列化列表字符串

```java
    public void write(List<String> list) {
        if (list.isEmpty()) {
            /** 空字符列表，输出[]字符串 */
            write("[]");
            return;
        }

        int offset = count;
        final int initOffset = offset;
        for (int i = 0, list_size = list.size(); i < list_size; ++i) {
            /** 循环获取列表中包含的字符串 */
            String text = list.get(i);

            boolean hasSpecial = false;
            if (text == null) {
                /** list包含特殊的null值 */
                hasSpecial = true;
            } else {
                for (int j = 0, len = text.length(); j < len; ++j) {
                    char ch = text.charAt(j);
                    /** 包含指定特殊字符 */
                    if (hasSpecial = (ch < ' ' //
                                      || ch > '~' //
                                      || ch == '"' //
                                      || ch == '\\')) {
                        break;
                    }
                }
            }

            if (hasSpecial) {
                count = initOffset;
                write('[');
                for (int j = 0; j < list.size(); ++j) {
                    text = list.get(j);
                    /** 每个字符用,隔开输出 */
                    if (j != 0) {
                        write(',');
                    }

                    if (text == null) {
                        /** 字符串为空，直接输出null字符串 */
                        write("null");
                    } else {
                        /** 使用双引号输出，并且处理特殊字符, 下文有分析 */
                        writeStringWithDoubleQuote(text, (char) 0);
                    }
                }
                write(']');
                return;
            }

            /** 计算新的字符占用空间，额外3个字符用于存储 "," */
            int newcount = offset + text.length() + 3;
            if (i == list.size() - 1) {
                newcount++;
            }
            /** 如果当前存储空间不够*/
            if (newcount > buf.length) {
                count = offset;
                /** 扩容到为原有buf容量1.5倍+1, copy原有buf的字符*/
                expandCapacity(newcount);
            }

            if (i == 0) {
                buf[offset++] = '[';
            } else {
                buf[offset++] = ',';
            }
            buf[offset++] = '"';
            /** 拷贝text字符串到buffer数组中 */
            text.getChars(0, text.length(), buf, offset);
            offset += text.length();
            buf[offset++] = '"';
        }
        /** 最终构造列表形式 ["element", "element", ...] */
        buf[offset++] = ']';
        count = offset;
    }
```

序列化字符串会转化成\[“element”, "element", ...\]格式。如果列表字符串中包含特殊字符，调用特化版本writeStringWithDoubleQuote\(text, \(char\) 0\)。

### 序列化包含特殊字符字符串

```java
    public void writeStringWithDoubleQuote(String text, final char seperator) {
        if (text == null) {
            /** 如果字符换为空，输出null字符串 */
            writeNull();
            if (seperator != 0) {
                /** 如果分隔符不为空白字符' '，输出分隔符 */
                write(seperator);
            }
            return;
        }

        int len = text.length();
        int newcount = count + len + 2;
        if (seperator != 0) {
            newcount++;
        }

        /** 如果当前存储空间不够 */
        if (newcount > buf.length) {
            if (writer != null) {
                /** 写双引号字符 */
                write('"');

                for (int i = 0; i < text.length(); ++i) {
                    /** 循环提取字符串中字符 */
                    char ch = text.charAt(i);

                    if (isEnabled(SerializerFeature.BrowserSecure)) {
                       if (ch == '(' || ch == ')' || ch == '<' || ch == '>') {
                            /** ascii转换成native编码 */
                            write('\\');
                            write('u');
                            write(IOUtils.DIGITS[(ch >>> 12) & 15]);
                            write(IOUtils.DIGITS[(ch >>> 8) & 15]);
                            write(IOUtils.DIGITS[(ch >>> 4) & 15]);
                            write(IOUtils.DIGITS[ch & 15]);
                            continue;
                        }
                    }

                    if (isEnabled(SerializerFeature.BrowserCompatible)) {
                        if (ch == '\b'      //  退格
                            || ch == '\f'   //  分页
                            || ch == '\n'   //  换行
                            || ch == '\r'   //  回车
                            || ch == '\t'   //  tab
                            || ch == '"'    //  双引号
                            || ch == '/'    //  左反斜杠
                            || ch == '\\') {//  单引号
                            /** 输出转义字符 + 字符ascii码 */
                            write('\\'); //  右反斜杠
                            write(replaceChars[(int) ch]);
                            continue;
                        }

                        if (ch < 32) {
                            /** ascii转换成native编码 */
                            write('\\');
                            write('u');
                            write('0');
                            write('0');
                            write(IOUtils.ASCII_CHARS[ch * 2]);
                            write(IOUtils.ASCII_CHARS[ch * 2 + 1]);
                            continue;
                        }

                        if (ch >= 127) {
                            /** ascii转换成native编码 */
                            write('\\');
                            write('u');
                            write(IOUtils.DIGITS[(ch >>> 12) & 15]);
                            write(IOUtils.DIGITS[(ch >>> 8) & 15]);
                            write(IOUtils.DIGITS[(ch >>> 4) & 15]);
                            write(IOUtils.DIGITS[ch & 15]);
                            continue;
                        }
                    } else {
                        /** ascii转换成native编码 */
                        if (ch < IOUtils.specicalFlags_doubleQuotes.length
                            && IOUtils.specicalFlags_doubleQuotes[ch] != 0 //
                            || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                            write('\\');
                            if (IOUtils.specicalFlags_doubleQuotes[ch] == 4) {
                                write('u');
                                write(IOUtils.DIGITS[ch >>> 12 & 15]);
                                write(IOUtils.DIGITS[ch >>> 8 & 15]);
                                write(IOUtils.DIGITS[ch >>> 4 & 15]);
                                write(IOUtils.DIGITS[ch & 15]);
                            } else {
                                write(IOUtils.replaceChars[ch]);
                            }
                            continue;
                        }
                    }

                    /** 非特殊字符，直接输出 */
                    write(ch);
                }

                /** 字符串结束 */
                write('"');
                if (seperator != 0) {
                    write(seperator);
                }
                return;
            }
            /** buffer容量不够并且输出器为空，触发扩容 */
            expandCapacity(newcount);
        }

        int start = count + 1;
        int end = start + len;

        buf[count] = '\"';
        /** buffer能够容纳字符串，直接拷贝text到buf缓冲数组 */
        text.getChars(0, len, buf, start);

        count = newcount;

        if (isEnabled(SerializerFeature.BrowserCompatible)) {
            int lastSpecialIndex = -1;

            for (int i = start; i < end; ++i) {
                /** 循环提取字符串中字符 */
                char ch = buf[i];

                if (ch == '"' //
                    || ch == '/' //
                    || ch == '\\') {
                    /** 记录指定字符最后出现的位置 */
                    lastSpecialIndex = i;
                    newcount += 1;
                    continue;
                }

                if (ch == '\b' //
                    || ch == '\f' //
                    || ch == '\n' //
                    || ch == '\r' //
                    || ch == '\t') {
                    /** 记录指定字符最后出现的位置 */
                    lastSpecialIndex = i;
                    newcount += 1;
                    continue;
                }

                if (ch < 32) {
                    lastSpecialIndex = i;
                    newcount += 5;
                    continue;
                }

                if (ch >= 127) {
                    lastSpecialIndex = i;
                    newcount += 5;
                    continue;
                }
            }

            /** 如果存储空间不足，触发到(1.5倍buffer大小+1) */
            if (newcount > buf.length) {
                expandCapacity(newcount);
            }
            count = newcount;

            /** 逆向从指定特殊字符开始遍历 */
            for (int i = lastSpecialIndex; i >= start; --i) {
                char ch = buf[i];

                if (ch == '\b' //
                    || ch == '\f'//
                    || ch == '\n' //
                    || ch == '\r' //
                    || ch == '\t') {
                    /** 将字符后移一位，插入转译字符\ */
                    System.arraycopy(buf, i + 1, buf, i + 2, end - i - 1);
                    buf[i] = '\\';
                    /** 将特殊字符转换成普通单字符 */
                    buf[i + 1] = replaceChars[(int) ch];
                    end += 1;
                    continue;
                }

                if (ch == '"' //
                    || ch == '/' //
                    || ch == '\\') {
                    /** 和上面处理一致，不需要单独替换成普通字符 */
                    System.arraycopy(buf, i + 1, buf, i + 2, end - i - 1);
                    buf[i] = '\\';
                    buf[i + 1] = ch;
                    end += 1;
                    continue;
                }

                if (ch < 32) {
                    System.arraycopy(buf, i + 1, buf, i + 6, end - i - 1);
                    /** ascii转换成native编码 */
                    buf[i] = '\\';
                    buf[i + 1] = 'u';
                    buf[i + 2] = '0';
                    buf[i + 3] = '0';
                    buf[i + 4] = IOUtils.ASCII_CHARS[ch * 2];
                    buf[i + 5] = IOUtils.ASCII_CHARS[ch * 2 + 1];
                    end += 5;
                    continue;
                }

                if (ch >= 127) {
                    System.arraycopy(buf, i + 1, buf, i + 6, end - i - 1);
                    /** ascii转换成native编码 */
                    buf[i] = '\\';
                    buf[i + 1] = 'u';
                    buf[i + 2] = IOUtils.DIGITS[(ch >>> 12) & 15];
                    buf[i + 3] = IOUtils.DIGITS[(ch >>> 8) & 15];
                    buf[i + 4] = IOUtils.DIGITS[(ch >>> 4) & 15];
                    buf[i + 5] = IOUtils.DIGITS[ch & 15];
                    end += 5;
                }
            }

            /** 追加引用符号 */
            if (seperator != 0) {
                buf[count - 2] = '\"';
                buf[count - 1] = seperator;
            } else {
                buf[count - 1] = '\"';
            }

            return;
        }

        int specialCount = 0;
        int lastSpecialIndex = -1;
        int firstSpecialIndex = -1;
        char lastSpecial = '\0';

        for (int i = start; i < end; ++i) {
            char ch = buf[i];

            if (ch >= ']') { //   93
                /** 特殊字符参考：http://www.mokuge.com/tool/asciito16/ */
                if (ch >= 0x7F // 127
                        && (ch == '\u2028' //
                        || ch == '\u2029'  //
                        || ch < 0xA0)) {   // 160 [空格]
                    if (firstSpecialIndex == -1) {
                        firstSpecialIndex = i;
                    }

                    specialCount++;
                    lastSpecialIndex = i;
                    lastSpecial = ch;
                    newcount += 4;
                }
                continue;
            }

            boolean special = (ch < 64 && (sepcialBits & (1L << ch)) != 0) || ch == '\\';
            if (special) {
                specialCount++;
                lastSpecialIndex = i;
                lastSpecial = ch;

                if (ch == '('
                        || ch == ')'
                        || ch == '<'
                        || ch == '>'
                        || (ch < IOUtils.specicalFlags_doubleQuotes.length //
                    && IOUtils.specicalFlags_doubleQuotes[ch] == 4) //
                ) {
                    newcount += 4;
                }

                if (firstSpecialIndex == -1) {
                    firstSpecialIndex = i;
                }
            }
        }

        if (specialCount > 0) {
            newcount += specialCount;
            /** 包含特殊字符并且buffer空间不够，触发扩容 */
            if (newcount > buf.length) {
                expandCapacity(newcount);
            }
            count = newcount;

            /** 将特殊字符转换成native编码，目的是节省存储空间*/
            if (specialCount == 1) {
                // 行分隔符
                if (lastSpecial == '\u2028') {
                    int srcPos = lastSpecialIndex + 1;
                    int destPos = lastSpecialIndex + 6;
                    int LengthOfCopy = end - lastSpecialIndex - 1;
                    System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                    buf[lastSpecialIndex] = '\\';
                    buf[++lastSpecialIndex] = 'u';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '0';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '8';
                }
                // 段落分隔符
                else if (lastSpecial == '\u2029') {
                    int srcPos = lastSpecialIndex + 1;
                    int destPos = lastSpecialIndex + 6;
                    int LengthOfCopy = end - lastSpecialIndex - 1;
                    System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                    buf[lastSpecialIndex] = '\\';
                    buf[++lastSpecialIndex] = 'u';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '0';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '9';
                } else if (lastSpecial == '(' || lastSpecial == ')' || lastSpecial == '<' || lastSpecial == '>') {
                    int srcPos = lastSpecialIndex + 1;
                    int destPos = lastSpecialIndex + 6;
                    int LengthOfCopy = end - lastSpecialIndex - 1;
                    System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                    buf[lastSpecialIndex] = '\\';
                    buf[++lastSpecialIndex] = 'u';

                    final char ch = lastSpecial;
                    buf[++lastSpecialIndex] = IOUtils.DIGITS[(ch >>> 12) & 15];
                    buf[++lastSpecialIndex] = IOUtils.DIGITS[(ch >>> 8) & 15];
                    buf[++lastSpecialIndex] = IOUtils.DIGITS[(ch >>> 4) & 15];
                    buf[++lastSpecialIndex] = IOUtils.DIGITS[ch & 15];
                } else {
                    final char ch = lastSpecial;
                    if (ch < IOUtils.specicalFlags_doubleQuotes.length //
                        && IOUtils.specicalFlags_doubleQuotes[ch] == 4) {
                        int srcPos = lastSpecialIndex + 1;
                        int destPos = lastSpecialIndex + 6;
                        int LengthOfCopy = end - lastSpecialIndex - 1;
                        System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);

                        int bufIndex = lastSpecialIndex;
                        buf[bufIndex++] = '\\';
                        buf[bufIndex++] = 'u';
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                    } else {
                        int srcPos = lastSpecialIndex + 1;
                        int destPos = lastSpecialIndex + 2;
                        int LengthOfCopy = end - lastSpecialIndex - 1;
                        System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                        buf[lastSpecialIndex] = '\\';
                        buf[++lastSpecialIndex] = replaceChars[(int) ch];
                    }
                }
            } else if (specialCount > 1) {
                int textIndex = firstSpecialIndex - start;
                int bufIndex = firstSpecialIndex;
                for (int i = textIndex; i < text.length(); ++i) {
                    char ch = text.charAt(i);

                    /** 将特殊字符转换成native编码，目的是节省存储空间*/ 
                    if (browserSecure && (ch == '('
                            || ch == ')'
                            || ch == '<'
                            || ch == '>')) {
                        buf[bufIndex++] = '\\';
                        buf[bufIndex++] = 'u';
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                        end += 5;
                    } else if (ch < IOUtils.specicalFlags_doubleQuotes.length //
                        && IOUtils.specicalFlags_doubleQuotes[ch] != 0 //
                        || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                        buf[bufIndex++] = '\\';
                        if (IOUtils.specicalFlags_doubleQuotes[ch] == 4) {
                            buf[bufIndex++] = 'u';
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                            end += 5;
                        } else {
                            buf[bufIndex++] = replaceChars[(int) ch];
                            end++;
                        }
                    } else {
                        // 行分隔符 、段落分隔符
                        if (ch == '\u2028' || ch == '\u2029') {
                            buf[bufIndex++] = '\\';
                            buf[bufIndex++] = 'u';
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                            end += 5;
                        } else {
                            buf[bufIndex++] = ch;
                        }
                    }
                }
            }
        }

        if (seperator != 0) {
            buf[count - 2] = '\"';
            buf[count - 1] = seperator;
        } else {
            buf[count - 1] = '\"';
        }
    }
```

writeStringWithDoubleQuote方法实现实在是太长了，这个方法主要做了以下几件事情：

1. 如果开启序列化BrowserCompatible特性，执行ascii转换成native编码，节省空间。
2. 如果输出器writer不为空，会自动触发buffer扩容\(原有容量1.5倍+1\)。

另外一个针对特殊字符的字符串序列化方法writeStringWithDoubleQuote(char[] text, final char seperator)，因为和writeStringWithDoubleQuote(String text, final char seperator)版本极其类似，所以不再冗余分析。

序列化字符串的方法包括添加单引号的版本，详细请参考 writeStringWithSingleQuote ：

``` java
    protected void writeStringWithSingleQuote(String text) {
        if (text == null) {
            int newcount = count + 4;
            if (newcount > buf.length) {
                expandCapacity(newcount);
            }
            /** 如果字符串为null，输出"null"字符串 */
            "null".getChars(0, 4, buf, count);
            count = newcount;
            return;
        }

        int len = text.length();
        int newcount = count + len + 2;
        if (newcount > buf.length) {
            if (writer != null) {
                /** 使用单引号输出字符串值 */
                write('\'');
                for (int i = 0; i < text.length(); ++i) {
                    char ch = text.charAt(i);
                    if (ch <= 13 || ch == '\\' || ch == '\'' //
                        || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                        /** 如果包含特殊字符 或者 单字符'\' ''' ，添加转译并且替换为普通字符*/
                        write('\\');
                        write(replaceChars[(int) ch]);
                    } else {
                        write(ch);
                    }
                }
                write('\'');
                return;
            }
            /** buffer容量不够并且输出器为空，触发扩容 */
            expandCapacity(newcount);
        }

        int start = count + 1;
        int end = start + len;

        buf[count] = '\'';
        /** buffer能够容纳字符串，直接拷贝text到buf缓冲数组 */
        text.getChars(0, len, buf, start);
        count = newcount;

        int specialCount = 0;
        int lastSpecialIndex = -1;
        char lastSpecial = '\0';
        for (int i = start; i < end; ++i) {
            char ch = buf[i];
            if (ch <= 13 || ch == '\\' || ch == '\'' //
                || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                /** 记录特殊字符个数和最后一个特殊字符索引 */
                specialCount++;
                lastSpecialIndex = i;
                lastSpecial = ch;
            }
        }

        newcount += specialCount;
        if (newcount > buf.length) {
            expandCapacity(newcount);
        }
        count = newcount;

        if (specialCount == 1) {
            /** 将字符后移一位，插入转译字符\ 并替换特殊字符为普通字符*/
            System.arraycopy(buf, lastSpecialIndex + 1, buf, lastSpecialIndex + 2, end - lastSpecialIndex - 1);
            buf[lastSpecialIndex] = '\\';
            buf[++lastSpecialIndex] = replaceChars[(int) lastSpecial];
        } else if (specialCount > 1) {
            System.arraycopy(buf, lastSpecialIndex + 1, buf, lastSpecialIndex + 2, end - lastSpecialIndex - 1);
            buf[lastSpecialIndex] = '\\';
            buf[++lastSpecialIndex] = replaceChars[(int) lastSpecial];
            end++;
            for (int i = lastSpecialIndex - 2; i >= start; --i) {
                char ch = buf[i];

                if (ch <= 13 || ch == '\\' || ch == '\'' //
                    || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                    /** 将字符后移一位，插入转译字符\ 并替换特殊字符为普通字符*/
                    System.arraycopy(buf, i + 1, buf, i + 2, end - i - 1);
                    buf[i] = '\\';
                    buf[i + 1] = replaceChars[(int) ch];
                    end++;
                }
            }
        }

        /** 字符串结尾添加单引号引用 */
        buf[count - 1] = '\'';
    }
```

writeStringWithSingleQuote这个方法主要做了以下几件事情：

1. 针对特殊字符，添加转译字符并且替换特殊字符为普通字符
2. 如果输出器writer不为空，会自动触发buffer扩容\(原有容量1.5倍+1\)。

另外一个针对特殊字符的字符串序列化方法writeStringWithSingleQuote(char[])，因为和writeStringWithSingleQuote(String)版本极其类似，所以不再冗余分析。

### 序列化字段名称

```java
    public void writeFieldName(String key, boolean checkSpecial) {
        if (key == null) {
            /** 如果字段key为null， 输出 "null:" */
            write("null:");
            return;
        }

        if (useSingleQuotes) {
            if (quoteFieldNames) {
                /** 使用单引号并且在字段后面加'：'输出 标准的json key*/
                writeStringWithSingleQuote(key);
                write(':');
            } else {
                /** 输出key，如果有特殊字符会自动添加单引号 */
                writeKeyWithSingleQuoteIfHasSpecial(key);
            }
        } else {
            if (quoteFieldNames) {
                /** 使用双引号输出json key 并添加 ： */
                writeStringWithDoubleQuote(key, ':');
            } else {
                boolean hashSpecial = key.length() == 0;
                for (int i = 0; i < key.length(); ++i) {
                    char ch = key.charAt(i);
                    boolean special = (ch < 64 && (sepcialBits & (1L << ch)) != 0) || ch == '\\';
                    if (special) {
                        hashSpecial = true;
                        break;
                    }
                }
                if (hashSpecial) {
                    /** 如果包含特殊字符，会进行特殊字符转换输出，eg: 使用转换后的native编码输出 */
                    writeStringWithDoubleQuote(key, ':');
                } else {
                    /** 输出字段不加引号 */
                    write(key);
                    write(':');
                }
            }
        }
    }
```

序列化字段名称方法writeFieldName主要的任务：

1. 完成字段特殊字符的转译
2. 添加字段的引号

处理输出key的特殊字符方法writeStringWithDoubleQuote前面已经分析过了，序列化字段名称是否需要添加引号和特殊字符处理参考writeKeyWithSingleQuoteIfHasSpecial：

``` java
    private void writeKeyWithSingleQuoteIfHasSpecial(String text) {
        final byte[] specicalFlags_singleQuotes = IOUtils.specicalFlags_singleQuotes;

        int len = text.length();
        int newcount = count + len + 1;
        if (newcount > buf.length) {
            if (writer != null) {
                if (len == 0) {
                    /** 如果字段为null， 输出空白字符('':)作为key */
                    write('\'');
                    write('\'');
                    write(':');
                    return;
                }

                boolean hasSpecial = false;
                for (int i = 0; i < len; ++i) {
                    char ch = text.charAt(i);
                    if (ch < specicalFlags_singleQuotes.length && specicalFlags_singleQuotes[ch] != 0) {
                        hasSpecial = true;
                        break;
                    }
                }

                /** 如果有特殊字符，给字段key添加单引号 */
                if (hasSpecial) {
                    write('\'');
                }
                for (int i = 0; i < len; ++i) {
                    char ch = text.charAt(i);
                    if (ch < specicalFlags_singleQuotes.length && specicalFlags_singleQuotes[ch] != 0) {
                        /** 如果输出key中包含特殊字符，添加转译字符并将特殊字符替换成普通字符 */
                        write('\\');
                        write(replaceChars[(int) ch]);
                    } else {
                        write(ch);
                    }
                }

                /** 如果有特殊字符，给字段key添加单引号 */
                if (hasSpecial) {
                    write('\'');
                }
                write(':');
                return;
            }
            /** 输出器writer为null触发扩容，扩容到为原有buf容量1.5倍+1, copy原有buf的字符*/
            expandCapacity(newcount);
        }

        if (len == 0) {
            int newCount = count + 3;
            if (newCount > buf.length) {
                expandCapacity(count + 3);
            }
            buf[count++] = '\'';
            buf[count++] = '\'';
            buf[count++] = ':';
            return;
        }

        int start = count;
        int end = start + len;

        /** buffer能够容纳字符串，直接拷贝text到buf缓冲数组 */
        text.getChars(0, len, buf, start);
        count = newcount;

        boolean hasSpecial = false;

        for (int i = start; i < end; ++i) {
            char ch = buf[i];
            if (ch < specicalFlags_singleQuotes.length && specicalFlags_singleQuotes[ch] != 0) {
                if (!hasSpecial) {
                    newcount += 3;
                    if (newcount > buf.length) {
                        expandCapacity(newcount);
                    }
                    count = newcount;

                    /** 将字符后移两位，插入字符'\ 并替换特殊字符为普通字符 */
                    System.arraycopy(buf, i + 1, buf, i + 3, end - i - 1);
                    /** 将字符后移一位 */
                    System.arraycopy(buf, 0, buf, 1, i);
                    buf[start] = '\'';
                    buf[++i] = '\\';
                    buf[++i] = replaceChars[(int) ch];
                    end += 2;
                    buf[count - 2] = '\'';

                    hasSpecial = true;
                } else {
                    newcount++;
                    if (newcount > buf.length) {
                        expandCapacity(newcount);
                    }
                    count = newcount;

                    /** 包含特殊字符，将字符后移一位，插入转译字符\ 并替换特殊字符为普通字符 */
                    System.arraycopy(buf, i + 1, buf, i + 2, end - i);
                    buf[i] = '\\';
                    buf[++i] = replaceChars[(int) ch];
                    end++;
                }
            }
        }

        buf[newcount - 1] = ':';
    }
```

### 序列化Boolean类型字段键值对

``` java
    public void writeFieldValue(char seperator, String name, boolean value) {
        if (!quoteFieldNames) {
            /** 如果不需要输出双引号，则一次输出字段分隔符，字段名字，字段值 */
            write(seperator);
            writeFieldName(name);
            write(value);
            return;
        }
        /** true 占用4位， false 占用5位 */
        int intSize = value ? 4 : 5;

        int nameLen = name.length();
        /** 输出总长度， 中间的4  代表 key 和 value 总共占用4个引号 */
        int newcount = count + nameLen + 4 + intSize;
        if (newcount > buf.length) {
            if (writer != null) {
                /** 依次输出字段分隔符，字段：字段值 */
                write(seperator);
                writeString(name);
                write(':');
                write(value);
                return;
            }
            /** 输出器writer为null触发扩容，扩容到为原有buf容量1.5倍+1, copy原有buf的字符*/
            expandCapacity(newcount);
        }

        int start = count;
        count = newcount;

        /** 输出字段分隔符，一般是, */
        buf[start] = seperator;

        int nameEnd = start + nameLen + 1;

        /** 输出字段属性分隔符，一般是单引号或双引号 */
        buf[start + 1] = keySeperator;

        /** 输出字段名称 */
        name.getChars(0, nameLen, buf, start + 2);

        /** 字段名称添加分隔符，一般是单引号或双引号 */
        buf[nameEnd + 1] = keySeperator;

        /** 输出boolean类型字符串值 */
        if (value) {
            System.arraycopy(":true".toCharArray(), 0, buf, nameEnd + 2, 5);
        } else {
            System.arraycopy(":false".toCharArray(), 0, buf, nameEnd + 2, 6);
        }
    }
```

### 序列化Int类型字段键值对

``` java
    public void writeFieldValue(char seperator, String name, int value) {
        if (value == Integer.MIN_VALUE || !quoteFieldNames) {
            /** 如果是整数最小值或不需要输出双引号，则一次输出字段分隔符，字段名字，字段值 */
            write(seperator);
            writeFieldName(name);
            writeInt(value);
            return;
        }

        /** 根据数字判断占用的位数，负数会多一位用于存储字符`-` */
        int intSize = (value < 0) ? IOUtils.stringSize(-value) + 1 : IOUtils.stringSize(value);

        int nameLen = name.length();
        int newcount = count + nameLen + 4 + intSize;
        if (newcount > buf.length) {
            if (writer != null) {
                write(seperator);
                writeFieldName(name);
                writeInt(value);
                return;
            }
            /** 扩容到为原有buf容量1.5倍+1, copy原有buf的字符*/
            expandCapacity(newcount);
        }

        int start = count;
        count = newcount;

        /** 输出字段分隔符，一般是, */
        buf[start] = seperator;

        int nameEnd = start + nameLen + 1;

        /** 输出字段属性分隔符，一般是单引号或双引号 */
        buf[start + 1] = keySeperator;

        /** 输出字段名称 */
        name.getChars(0, nameLen, buf, start + 2);

        buf[nameEnd + 1] = keySeperator;
        buf[nameEnd + 2] = ':';

        /** 输出整数值，对整数转化成单字符 */
        IOUtils.getChars(value, count, buf);
    }
```