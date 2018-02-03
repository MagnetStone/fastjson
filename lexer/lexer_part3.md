
### JSON Token解析

这个章节主要讨论关于对象字段相关词法解析的api。

### JSONLexerBase成员函数

这里讲解主要挑选具有代表性的api进行讲解，同时对于极其相似的api不冗余分析，可以参考代码阅读。

#### Int类型字段解析

当反序列化`java`对象遇到整型`int.class`字段会调用该方法解析：

``` java
    public int scanInt(char expectNext) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));

        /** 取整数第一个字符判断是否是引号 */
        final boolean quote = chLocal == '"';
        if (quote) {
            /** 如果是双引号，取第一个数字字符 */
            chLocal = charAt(bp + (offset++));
        }

        final boolean negative = chLocal == '-';
        if (negative) {
            /** 如果是负数，继续取下一个字符 */
            chLocal = charAt(bp + (offset++));
        }

        int value;
        /** 是数字类型 */
        if (chLocal >= '0' && chLocal <= '9') {
            value = chLocal - '0';
            for (;;) {
                /** 循环将字符转换成数字 */
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    value = value * 10 + (chLocal - '0');
                } else if (chLocal == '.') {
                    matchStat = NOT_MATCH;
                    return 0;
                } else {
                    break;
                }
            }
            if (value < 0) {
                matchStat = NOT_MATCH;
                return 0;
            }
        } else if (chLocal == 'n' && charAt(bp + offset) == 'u' && charAt(bp + offset + 1) == 'l' && charAt(bp + offset + 2) == 'l') {
            /** 匹配到null */
            matchStat = VALUE_NULL;
            value = 0;
            offset += 3;
            /** 读取null后面的一个字符 */
            chLocal = charAt(bp + offset++);

            if (quote && chLocal == '"') {
                chLocal = charAt(bp + offset++);
            }

            for (;;) {
                /** 如果读取null后面有逗号，认为结束 */
                if (chLocal == ',') {
                    bp += offset;
                    this.ch = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.COMMA;
                    return value;
                } else if (chLocal == ']') {
                    bp += offset;
                    this.ch = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.RBRACKET;
                    return value;
                    /** 忽略空白字符 */
                } else if (isWhitespace(chLocal)) {
                    chLocal = charAt(bp + offset++);
                    continue;
                }
                break;
            }
            matchStat = NOT_MATCH;
            return 0;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        for (;;) {
            /** 根据期望字符用于结束匹配 */
            if (chLocal == expectNext) {
                bp += offset;
                this.ch = this.charAt(bp);
                matchStat = VALUE;
                token = JSONToken.COMMA;
                return negative ? -value : value;
            } else {
                /** 忽略空白字符 */
                if (isWhitespace(chLocal)) {
                    chLocal = charAt(bp + (offset++));
                    continue;
                }
                matchStat = NOT_MATCH;
                return negative ? -value : value;
            }
        }
    }
```

`com.alibaba.fastjson.parser.JSONLexerBase#scanInt(char)`方法考虑了数字加引号的情况，当遇到下列情况认为匹配失败：

1. 扫描遇到的数字遇到标点符号
2. 扫描的数字范围溢出
3. 扫描到的非数字并且不是null
4. 忽略空白字符的情况下，读取数字后结束符和期望expectNext不一致

``` java
    public final Number integerValue() throws NumberFormatException {
        long result = 0;
        boolean negative = false;
        if (np == -1) {
            np = 0;
        }
        /** np是token开始索引, sp是buffer索引，也代表buffer字符个数 */
        int i = np, max = np + sp;
        long limit;
        long multmin;
        int digit;

        char type = ' ';

        /** 探测数字类型最后一位是否带类型 */
        switch (charAt(max - 1)) {
            case 'L':
                max--;
                type = 'L';
                break;
            case 'S':
                max--;
                type = 'S';
                break;
            case 'B':
                max--;
                type = 'B';
                break;
            default:
                break;
        }

        /** 探测数字首字符是否是符号 */
        if (charAt(np) == '-') {
            negative = true;
            limit = Long.MIN_VALUE;
            i++;
        } else {
            limit = -Long.MAX_VALUE;
        }
        multmin = MULTMIN_RADIX_TEN;
        if (i < max) {
            /** 数字第一个字母转换成数字 */
            digit = charAt(i++) - '0';
            result = -digit;
        }

        /** 快速处理高精度整数，因为整数最大是10^9次方 */
        while (i < max) {
            // Accumulating negatively avoids surprises near MAX_VALUE
            digit = charAt(i++) - '0';
            /** multmin 大概10^17 */
            if (result < multmin) {
                /** numberString获取到的不包含数字后缀类型，但是包括负数符号(如果有) */
                return new BigInteger(numberString());
            }
            result *= 10;
            if (result < limit + digit) {
                return new BigInteger(numberString());
            }
            result -= digit;
        }

        if (negative) {
            /** 处理完数字 i 是指向数字最后一个字符的下一个字符,
             *  这里判断 i > np + 1 , 代表在 有效数字字符范围
             */
            if (i > np + 1) {
                /** 这里根据类型具体后缀类型做一次转换 */
                if (result >= Integer.MIN_VALUE && type != 'L') {
                    if (type == 'S') {
                        return (short) result;
                    }

                    if (type == 'B') {
                        return (byte) result;
                    }

                    return (int) result;
                }
                return result;
            } else { /* Only got "-" */
                throw new NumberFormatException(numberString());
            }
        } else {
            /** 这里是整数， 因为前面处理成负数，取反就可以了 */
            result = -result;
            /** 这里根据类型具体后缀类型做一次转换 */
            if (result <= Integer.MAX_VALUE && type != 'L') {
                if (type == 'S') {
                    return (short) result;
                }

                if (type == 'B') {
                    return (byte) result;
                }

                return (int) result;
            }
            return result;
        }
    }
```