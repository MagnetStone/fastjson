
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

`fastjson` 还提供第二种接口，根据token识别数字：

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

`fastjson` 还提供第三种接口，这个接口严格根据字段名进行匹配`json`字符串，字段名会自动加上双引号和冒号，格式`"key":` :

``` java
    public int scanFieldInt(char[] fieldName) {
        matchStat = UNKNOWN;

        /** 属性不匹配，忽略 */
        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        final boolean negative = chLocal == '-';
        if (negative) {
            /** 如果是负数，读取第一个数字字符 */
            chLocal = charAt(bp + (offset++));
        }

        int value;
        if (chLocal >= '0' && chLocal <= '9') {
            /** 转换成数字 */
            value = chLocal - '0';
            for (;;) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    value = value * 10 + (chLocal - '0');
                } else if (chLocal == '.') {
                    /** 数字后面有点，不符合整数，标记不匹配 */
                    matchStat = NOT_MATCH;
                    return 0;
                } else {
                    break;
                }
            }
            /** value < 0 代表整数值溢出了,
             *  11 + 3 代表了最小负数加了引号(占用2), 剩余
             *  占用1 是因为读完最后一位数字，offset++ 递增了1
             */
            if (value < 0
                    || offset > 11 + 3 + fieldName.length) {
                if (value != Integer.MIN_VALUE
                        || offset != 17
                        || !negative) {
                    matchStat = NOT_MATCH;
                    return 0;
                }
            }
        } else {
            /** 非数字代表不匹配 */
            matchStat = NOT_MATCH;
            return 0;
        }

        /** 如果遇到逗号，认为结束 */
        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return negative ? -value : value;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.ch = this.charAt(bp);
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.ch = this.charAt(bp);
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.ch = this.charAt(bp);
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                ch = EOI;
            } else {
                matchStat = NOT_MATCH;
                return 0;
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        return negative ? -value : value;
    }
```

#### Long类型字段解析

`Long`字段解析和`Int`一样提供3中接口，先看第一种基于字段类型解析：

``` java
    public long scanLong(char expectNextChar) {
        matchStat = UNKNOWN;

        int offset = 0;
        char chLocal = charAt(bp + (offset++));
        final boolean quote = chLocal == '"';
        if (quote) {
            /** 有引号，继续读下一个字符 */
            chLocal = charAt(bp + (offset++));
        }

        final boolean negative = chLocal == '-';
        if (negative) {
            /** 有符号，标识是负数 */
            chLocal = charAt(bp + (offset++));
        }

        long value;
        /** 循环将字符转换成数字 */
        if (chLocal >= '0' && chLocal <= '9') {
            value = chLocal - '0';
            for (;;) {
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
            /** 如果偏移量超过最大long的21位，是无效数字 */
            boolean valid = value >= 0 || (value == -9223372036854775808L && negative);
            if (!valid) {
                String val = subString(bp, offset - 1);
                throw new NumberFormatException(val);
            }
        } else if (chLocal == 'n' && charAt(bp + offset) == 'u' && charAt(bp + offset + 1) == 'l' && charAt(bp + offset + 2) == 'l') {
            matchStat = VALUE_NULL;
            value = 0;
            offset += 3;
            chLocal = charAt(bp + offset++);

            if (quote && chLocal == '"') {
                chLocal = charAt(bp + offset++);
            }

            for (;;) {
                if (chLocal == ',') {
                    /** 如果是null, 紧跟着逗号，认为结束匹配 */
                    bp += offset;
                    this.ch = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.COMMA;
                    return value;
                } else if (chLocal == ']') {
                    /** 如果是null, 紧跟着逗号], 认为结束匹配 */
                    bp += offset;
                    this.ch = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.RBRACKET;
                    return value;
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

        if (quote) {
            if (chLocal != '"') {
                matchStat = NOT_MATCH;
                return 0;
            } else {
                chLocal = charAt(bp + (offset++));
            }
        }

        /**
         *  忽略和Int一致的根据期望字符判断逻辑
         */
    }
```

因为和`Int`比较相似，这里提供第三个基于字段名字匹配实现：

``` java
    public long scanFieldLong(char[] fieldName) {
        matchStat = UNKNOWN;

        /**
         *  从当前json串bp位置开始逐字符比较字段 是否匹配
         *
         *  fieldName 格式是 "name":
         *  @see FieldInfo#genFieldNameChars()
         */
        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        boolean negative = false;
        if (chLocal == '-') {
            /** 有符号，标识是负数 */
            chLocal = charAt(bp + (offset++));
            negative = true;
        }

        long value;
        if (chLocal >= '0' && chLocal <= '9') {
            value = chLocal - '0';
            for (;;) {
                /** 循环将字符转换成数字 */
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    value = value * 10 + (chLocal - '0');
                    /** 如果数字带标点符号，认为不是合法整数，匹配失败 */
                } else if (chLocal == '.') {
                    matchStat = NOT_MATCH;
                    return 0;
                } else {
                    break;
                }
            }

            /** 如果偏移量超过最大long的21位，是无效数字 */
            boolean valid = offset - fieldName.length < 21
                    && (value >= 0 || (value == -9223372036854775808L && negative));
            if (!valid) {
                matchStat = NOT_MATCH;
                return 0;
            }
        } else {
            matchStat = NOT_MATCH;
            return 0;
        }

        if (chLocal == ',') {
            /** 如果数字后面跟着逗号，结束 并预读下一个字符 */
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return negative ? -value : value;
        }

        /**
         *  忽略和Int一致的判断数字后续的token逻辑
         */

        return negative ? -value : value;
    }
```

#### Float类型字段解析

跟`Int`一致的接口，现提供第二种获取`float`实现：

``` java
    public float floatValue() {
        /** numberString获取到的不包含数字后缀类型，但是包括负数符号(如果有) */
        String strVal = numberString();
        float floatValue = Float.parseFloat(strVal);
        /** 如果是0或者正无穷大，首字母是0-9 代表溢出 */
        if (floatValue == 0 || floatValue == Float.POSITIVE_INFINITY) {
            char c0 = strVal.charAt(0);
            if (c0 > '0' && c0 <= '9') {
                throw new JSONException("float overflow : " + strVal);
            }
        }
        return floatValue;
    }
```

提供根据属性字段名字匹配的源码实现：

``` java
    public final float scanFieldFloat(char[] fieldName) {
        matchStat = UNKNOWN;

        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return 0;
        }

        int offset = fieldName.length;
        char chLocal = charAt(bp + (offset++));

        final boolean quote = chLocal == '"';
        if (quote) {
            chLocal = charAt(bp + (offset++));
        }

        boolean negative = chLocal == '-';
        if (negative) {
            chLocal = charAt(bp + (offset++));
        }

        float value;
        if (chLocal >= '0' && chLocal <= '9') {
            int intVal = chLocal - '0';
            for (;;) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    intVal = intVal * 10 + (chLocal - '0');
                    continue;
                } else {
                    /** 如果遇到非数字字符终止 */
                    break;
                }
            }

            int power = 1;
            boolean small = (chLocal == '.');
            if (small) {
                chLocal = charAt(bp + (offset++));
                if (chLocal >= '0' && chLocal <= '9') {
                    /** 将小数点后面数字转换成int类型数字 */
                    intVal = intVal * 10 + (chLocal - '0');
                    power = 10;
                    for (;;) {
                        chLocal = charAt(bp + (offset++));
                        if (chLocal >= '0' && chLocal <= '9') {
                            /** 依次读取数字并转化int，记录小数点的数量级 */
                            intVal = intVal * 10 + (chLocal - '0');
                            power *= 10;
                            continue;
                        } else {
                            break;
                        }
                    }
                } else {
                    matchStat = NOT_MATCH;
                    return 0;
                }
            }

            boolean exp = chLocal == 'e' || chLocal == 'E';
            if (exp) {
                /** 处理科学计数法 */
                chLocal = charAt(bp + (offset++));
                if (chLocal == '+' || chLocal == '-') {
                    chLocal = charAt(bp + (offset++));
                }
                for (;;) {
                    if (chLocal >= '0' && chLocal <= '9') {
                        chLocal = charAt(bp + (offset++));
                    } else {
                        break;
                    }
                }
            }

            int start, count;
            if (quote) {
                if (chLocal != '"') {
                    matchStat = NOT_MATCH;
                    return 0;
                } else {
                    /** 遇到浮点数最后一个引号，预读下一个 */
                    chLocal = charAt(bp + (offset++));
                }

                /**
                 *  ----------------------------------------------------------------------------------------
                 *  | { | " | k | e | y | " | : | " | 7 | 0 | 0 | 8   |  .  |  5 |  5 |  5 |  5 |  " |  }
                 *  ----------------------------------------------------------------------------------------
                 *  | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 | 17 |  18
                 *  ----------------------------------------------------------------------------------------
                 *  |  | bp |  |   |   |   |   | |start|   |    |    |    |    |    |    |    |    | offset
                 *  ----------------------------------------------------------------------------------------
                 *  fieldName = "key":
                 *  fieldName.length == 6, bp == 0, offset == 17
                 *  start代表指向浮点第一个数字或者-号,
                 *  @see com.alibaba.json.bvt.parser.deser.BooleanFieldDeserializerTest#test_2()
                 */
                start = bp + fieldName.length + 1;
                count = bp + offset - start - 2;
            } else {
                start = bp + fieldName.length;
                count = bp + offset - start - 1;
            }

            if (!exp && count < 20) {
                value = ((float) intVal) / power;
                if (negative) {
                    value = -value;
                }
            } else {
                String text = this.subString(start, count);
                value = Float.parseFloat(text);
            }
        } else if (chLocal == 'n' && charAt(bp + offset) == 'u' && charAt(bp + offset + 1) == 'l' && charAt(bp + offset + 2) == 'l') {
            matchStat = VALUE_NULL;
            value = 0;
            offset += 3;
            chLocal = charAt(bp + offset++);

            if (quote && chLocal == '"') {
                chLocal = charAt(bp + offset++);
            }

            for (;;) {
                if (chLocal == ',') {
                    bp += offset;
                    this.ch = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.COMMA;
                    return value;
                } else if (chLocal == '}') {
                    bp += offset;
                    this.ch = charAt(bp);
                    matchStat = VALUE_NULL;
                    token = JSONToken.RBRACE;
                    return value;
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

        if (chLocal == ',') {
            bp += offset;
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            token = JSONToken.COMMA;
            return value;
        }

        /**
         *  省略读取数字后，剩余token匹配逻辑
         */

        return value;
    }
```

#### String类型字段解析

``` java
    public String scanString(char expectNextChar) {
        matchStat = UNKNOWN;

        int offset = 0;

        char chLocal = charAt(bp + (offset++));

        /** 兼容处理null字符串 */
        if (chLocal == 'n') {
            if (charAt(bp + offset) == 'u' && charAt(bp + offset + 1) == 'l' && charAt(bp + offset + 2) == 'l') {
                offset += 3;
                chLocal = charAt(bp + (offset++));
            } else {
                matchStat = NOT_MATCH;
                return null;
            }

            if (chLocal == expectNextChar) {
                bp += offset;
                this.ch = this.charAt(bp);
                matchStat = VALUE;
                return null;
            } else {
                matchStat = NOT_MATCH;
                return null;
            }
        }

        final String strVal;
        for (;;) {
            if (chLocal == '"') {
                int startIndex = bp + offset;
                int endIndex = indexOf('"', startIndex);
                if (endIndex == -1) {
                    throw new JSONException("unclosed str");
                }

                String stringVal = subString(bp + offset, endIndex - startIndex);
                /**
                 *  处理逻辑请参考详细注释：
                 *  @see ##scanFieldString(char[])
                 */
                if (stringVal.indexOf('\\') != -1) {
                    for (; ; ) {
                        int slashCount = 0;
                        for (int i = endIndex - 1; i >= 0; --i) {
                            if (charAt(i) == '\\') {
                                slashCount++;
                            } else {
                                break;
                            }
                        }
                        if (slashCount % 2 == 0) {
                            break;
                        }
                        endIndex = indexOf('"', endIndex + 1);
                    }

                    int chars_len = endIndex - startIndex;
                    char[] chars = sub_chars(bp + 1, chars_len);

                    stringVal = readString(chars, chars_len);
                }

                offset += (endIndex - startIndex + 1);
                chLocal = charAt(bp + (offset++));
                strVal = stringVal;
                break;
            } else if (isWhitespace(chLocal)) {
                chLocal = charAt(bp + (offset++));
                continue;
            } else {
                matchStat = NOT_MATCH;

                return stringDefaultValue();
            }
        }

        for (;;) {
            /** 如果遇到和期望字符认为结束符 */
            if (chLocal == expectNextChar) {
                bp += offset;
                /** 预读下一个字符 */
                this.ch = charAt(bp);
                matchStat = VALUE;
                return strVal;
            } else if (isWhitespace(chLocal)) {
                chLocal = charAt(bp + (offset++));
                continue;
            } else {
                matchStat = NOT_MATCH;
                return strVal;
            }
        }
    }
```

``` java
public abstract String stringVal();
```

这里提供的`stringVal()`需要由子类实现，原因：

1. 在`android6.0`和`jdk6`版本 获取子字符串会共享外层`String`的`char[]` 会导致String占用内存无法释放（特别是打文本字符串）。


``` java
    public String scanFieldString(char[] fieldName) {
        matchStat = UNKNOWN;

        /**
         *  从当前json串bp位置开始逐字符比较字段 是否匹配
         *
         *  fieldName 格式是 "name":
         *  @see FieldInfo#genFieldNameChars()
         */
        if (!charArrayCompare(fieldName)) {
            matchStat = NOT_MATCH_NAME;
            return stringDefaultValue();
        }

        // int index = bp + fieldName.length;

        int offset = fieldName.length;
        /** 读取字段下一个字符 */
        char chLocal = charAt(bp + (offset++));

        /** json 值类型字符串一定"，否则不符合规范 */
        if (chLocal != '"') {
            matchStat = NOT_MATCH;

            return stringDefaultValue();
        }

        final String strVal;
        {
            /** startIndex指向双引号下一个字符，
             *  eg : "name":"string", startIndex指向s
             */
            int startIndex = bp + fieldName.length + 1;
            int endIndex = indexOf('"', startIndex);
            if (endIndex == -1) {
                throw new JSONException("unclosed str");
            }

            int startIndex2 = bp + fieldName.length + 1; // must re compute
            String stringVal = subString(startIndex2, endIndex - startIndex2);
            /** 包含特殊转译字符 */
            if (stringVal.indexOf('\\') != -1) {
                /**
                 * 处理场景 "value\\\"" json串值
                 */
                for (;;) {
                    int slashCount = 0;
                    for (int i = endIndex - 1; i >= 0; --i) {
                        if (charAt(i) == '\\') {
                            slashCount++;
                        } else {
                            break;
                        }
                    }
                    if (slashCount % 2 == 0) {
                        break;
                    }
                    /** 如果遇到奇数转译字符，遇到"不认为值结束，找下一个"才认为结束 */
                    endIndex = indexOf('"', endIndex + 1);
                }

                /**
                 *  ---------------------------------------------------------------------------------
                 *  | " | k | e | y | " | : | " | v | a | l | u |  e  |  \ |  \ |  \ |  " |  " |
                 *  ---------------------------------------------------------------------------------
                 *  | 0 | 1 | 2 | 3 | 4 | 5 | 6 | 7 | 8 | 9 | 10 | 11 | 12 | 13 | 14 | 15 | 16 |
                 *  ---------------------------------------------------------------------------------
                 *  | bp | |   |   |   |   |   |   |   |   |    |    |    |    |    |    | endIndex |
                 *  ---------------------------------------------------------------------------------
                 *  fieldName = "key":
                 *  fieldName.length == 6, bp == 0, endIndex == 16
                 *  chars_len = 16 - (0 + 6 + 1) = 9, == value\\\"
                 */
                int chars_len = endIndex - (bp + fieldName.length + 1);
                char[] chars = sub_chars( bp + fieldName.length + 1, chars_len);

                stringVal = readString(chars, chars_len);
            }

            /** 偏移到json串字段值" 下一个字符 */
            offset += (endIndex - (bp + fieldName.length + 1) + 1);
            chLocal = charAt(bp + (offset++));
            strVal = stringVal;
        }

        if (chLocal == ',') {
            bp += offset;
            /** 读取下一个字符 */
            this.ch = this.charAt(bp);
            matchStat = VALUE;
            return strVal;
        }

        if (chLocal == '}') {
            chLocal = charAt(bp + (offset++));
            /** 如果字段值紧跟, 标记下次token为逗号 */
            if (chLocal == ',') {
                token = JSONToken.COMMA;
                bp += offset;
                this.ch = this.charAt(bp);
                /** 如果字段值紧跟] 标记下次token为右中括号 */
            } else if (chLocal == ']') {
                token = JSONToken.RBRACKET;
                bp += offset;
                this.ch = this.charAt(bp);
                /** 如果字段值紧跟} 标记下次token为右花括号 */
            } else if (chLocal == '}') {
                token = JSONToken.RBRACE;
                bp += offset;
                this.ch = this.charAt(bp);
                /** 特殊标记结束 */
            } else if (chLocal == EOI) {
                token = JSONToken.EOF;
                bp += (offset - 1);
                ch = EOI;
            } else {
                matchStat = NOT_MATCH;
                return stringDefaultValue();
            }
            matchStat = END;
        } else {
            matchStat = NOT_MATCH;
            return stringDefaultValue();
        }

        return strVal;
    }
```

目前分析的代码其实包括大部分实现了，这里没有给出`Decimal`和`Double`的实现，它们实现是类似的并且相对简单，主要是提取字符串直接用对应类的构造函数生成对象而已，如果想详细了解可以参考代码中已经添加的详尽注释。