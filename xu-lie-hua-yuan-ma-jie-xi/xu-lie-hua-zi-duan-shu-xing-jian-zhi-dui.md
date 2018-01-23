## SerializeWriter成员函数

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

序列化boolean类型的键值对属性，因为不涉及特殊字符，主要就是把原型序列化为字面量值。

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

序列化int类型的键值对属性，因为不涉及特殊字符，主要就是把原型序列化为字面量值。截止到现在，已经把核心SerializWriter类讲完了，剩余字段键值对极其类似writeFieldValue boolean和int等，因此无需冗余分析。因为序列化真正开始之前，这个类极其基础并且非常重要，因此花的时间较多。