
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

### 推断token类型

`fastjson` token类型推断当前`json`字符串是那种类型的token, 比如是字符串、花括号和逗号等等。

``` java
    public final void nextToken() {
        /** 将字符buffer pos设置为初始0 */
        sp = 0;

        for (;;) {
            /** pos记录为流的当前位置 */
            pos = bp;

            if (ch == '/') {
                /** 如果是注释// 或者 \/* *\/ 注释，跳过注释 */
                skipComment();
                continue;
            }

            if (ch == '"') {
                /** 读取引号内的字符串 */
                scanString();
                return;
            }

            if (ch == ',') {
                /** 跳过当前，读取下一个字符 */
                next();
                token = COMMA;
                return;
            }

            if (ch >= '0' && ch <= '9') {
                /** 读取整数 */
                scanNumber();
                return;
            }

            if (ch == '-') {
                /** 读取负数 */
                scanNumber();
                return;
            }

            switch (ch) {
                /** 读取单引号后面的字符串，和scanString逻辑一致 */
                case '\'':
                    if (!isEnabled(Feature.AllowSingleQuotes)) {
                        throw new JSONException("Feature.AllowSingleQuotes is false");
                    }
                    scanStringSingleQuote();
                    return;
                case ' ':
                case '\t':
                case '\b':
                case '\f':
                case '\n':
                case '\r':
                    next();
                    break;
                case 't': // true
                    /** 读取字符true */
                    scanTrue();
                    return;
                case 'f': // false
                    /** 读取字符false */
                    scanFalse();
                    return;
                case 'n': // new,null
                    /** 读取为new或者null的token */
                    scanNullOrNew();
                    return;
                case 'T':
                case 'N': // NULL
                case 'S':
                case 'u': // undefined
                    /** 读取标识符，已经自动预读了下一个字符 */
                    scanIdent();
                    return;
                case '(':
                    /** 读取下一个字符 */
                    next();
                    token = LPAREN;
                    return;
                case ')':
                    next();
                    token = RPAREN;
                    return;
                case '[':
                    next();
                    token = LBRACKET;
                    return;
                case ']':
                    next();
                    token = RBRACKET;
                    return;
                case '{':
                    next();
                    token = LBRACE;
                    return;
                case '}':
                    next();
                    token = RBRACE;
                    return;
                case ':':
                    next();
                    token = COLON;
                    return;
                case ';':
                    next();
                    token = SEMI;
                    return;
                case '.':
                    next();
                    token = DOT;
                    return;
                case '+':
                    next();
                    scanNumber();
                    return;
                case 'x':
                    scanHex();
                    return;
                default:
                    if (isEOF()) { // JLS
                        if (token == EOF) {
                            throw new JSONException("EOF error");
                        }

                        token = EOF;
                        pos = bp = eofPos;
                    } else {
                        /** 忽略控制字符或者删除字符 */
                        if (ch <= 31 || ch == 127) {
                            next();
                            break;
                        }

                        lexError("illegal.char", String.valueOf((int) ch));
                        next();
                    }

                    return;
            }
        }

    }
```

### 跳过注释

``` java
    protected void skipComment() {
        /** 读下一个字符 */
        next();
        /** 连续遇到左反斜杠/ */
        if (ch == '/') {
            for (;;) {
                /** 读下一个字符 */
                next();
                if (ch == '\n') {
                    /** 如果遇到换行符，继续读取下一个字符并返回 */
                    next();
                    return;
                    /** 如果已经遇到流结束，返回 */
                } else if (ch == EOI) {
                    return;
                }
            }
            /** 遇到`/*` 注释的格式 */
        } else if (ch == '*') {
            /** 读下一个字符 */
            next();
            for (; ch != EOI;) {
                if (ch == '*') {
                    /** 如果遇到*,继续尝试读取下一个字符，看看是否是/字符 */
                    next();
                    if (ch == '/') {
                        /** 如果确实是/字符，提前预读下一个有效字符后终止 */
                        next();
                        return;
                    } else {
                        /** 遇到非/ 继续跳过度下一个字符 */
                        continue;
                    }
                }
                /** 如果没有遇到`*\` 注释格式, 继续读下一个字符 */
                next();
            }
        } else {
            /** 不符合// 或者 \/* *\/ 注释格式 */
            throw new JSONException("invalid comment");
        }
    }
```

解析注释主要分为2中，支持`//` 或者 `/* */` 注释格式。


### 扫描字符串

当解析`json`字符串是`"`时，会调用扫描字符串方法。

``` java
    public final void scanString() {
        /** 记录当前流中token的开始位置, np指向引号的索引 */
        np = bp;
        hasSpecial = false;
        char ch;
        for (;;) {

            /** 读取当前字符串的字符 */
            ch = next();

            /** 如果遇到字符串结束符"， 则结束 */
            if (ch == '\"') {
                break;
            }

            if (ch == EOI) {
                /** 如果遇到了结束符EOI，但是没有遇到流的结尾，添加EOI结束符 */
                if (!isEOF()) {
                    putChar((char) EOI);
                    continue;
                }
                throw new JSONException("unclosed string : " + ch);
            }

            /** 处理转译字符逻辑 */
            if (ch == '\\') {
                if (!hasSpecial) {
                    /** 第一次遇到\认为是特殊符号 */
                    hasSpecial = true;

                    /** 如果buffer空间不够，执行2倍扩容 */
                    if (sp >= sbuf.length) {
                        int newCapcity = sbuf.length * 2;
                        if (sp > newCapcity) {
                            newCapcity = sp;
                        }
                        char[] newsbuf = new char[newCapcity];
                        System.arraycopy(sbuf, 0, newsbuf, 0, sbuf.length);
                        sbuf = newsbuf;
                    }

                    /** 复制有效字符串到buffer中，不包括引号 */
                    copyTo(np + 1, sp, sbuf);
                    // text.getChars(np + 1, np + 1 + sp, sbuf, 0);
                    // System.arraycopy(buf, np + 1, sbuf, 0, sp);
                }

                /** 读取转译字符\下一个字符 */
                ch = next();

                /** 转换ascii字符，请参考：https://baike.baidu.com/item/ASCII/309296?fr=aladdin */
                switch (ch) {
                    case '0':
                        /** 空字符 */
                        putChar('\0');
                        break;
                    case '1':
                        /** 标题开始 */
                        putChar('\1');
                        break;
                    case '2':
                        /** 正文开始 */
                        putChar('\2');
                        break;
                    case '3':
                        /** 正文结束 */
                        putChar('\3');
                        break;
                    case '4':
                        /** 传输结束 */
                        putChar('\4');
                        break;
                    case '5':
                        /** 请求 */
                        putChar('\5');
                        break;
                    case '6':
                        /** 收到通知 */
                        putChar('\6');
                        break;
                    case '7':
                        /** 响铃 */
                        putChar('\7');
                        break;
                    case 'b': // 8
                        /** 退格 */
                        putChar('\b');
                        break;
                    case 't': // 9
                        /** 水平制表符 */
                        putChar('\t');
                        break;
                    case 'n': // 10
                        /** 换行键 */
                        putChar('\n');
                        break;
                    case 'v': // 11
                        /** 垂直制表符 */
                        putChar('\u000B');
                        break;
                    case 'f': // 12
                        /** 换页键 */
                    case 'F':
                        /** 换页键 */
                        putChar('\f');
                        break;
                    case 'r': // 13
                        /** 回车键 */
                        putChar('\r');
                        break;
                    case '"': // 34
                        /** 双引号 */
                        putChar('"');
                        break;
                    case '\'': // 39
                        /** 闭单引号 */
                        putChar('\'');
                        break;
                    case '/': // 47
                        /** 斜杠 */
                        putChar('/');
                        break;
                    case '\\': // 92
                        /** 反斜杠 */
                        putChar('\\');
                        break;
                    case 'x':
                        /** 小写字母x, 标识一个字符 */
                        char x1 = ch = next();
                        char x2 = ch = next();

                        /** x1 左移4位 + x2 */
                        int x_val = digits[x1] * 16 + digits[x2];
                        char x_char = (char) x_val;
                        putChar(x_char);
                        break;
                    case 'u':
                        /** 小写字母u, 标识一个字符 */
                        char u1 = ch = next();
                        char u2 = ch = next();
                        char u3 = ch = next();
                        char u4 = ch = next();
                        int val = Integer.parseInt(new String(new char[] { u1, u2, u3, u4 }), 16);
                        putChar((char) val);
                        break;
                    default:
                        this.ch = ch;
                        throw new JSONException("unclosed string : " + ch);
                }
                continue;
            }

            /** 没有转译字符，递增buffer字符位置 */
            if (!hasSpecial) {
                sp++;
                continue;
            }

            /** 继续读取转译字符后面的字符 */
            if (sp == sbuf.length) {
                putChar(ch);
            } else {
                sbuf[sp++] = ch;
            }
        }

        token = JSONToken.LITERAL_STRING;
        /** 自动预读下一个字符 */
        this.ch = next();
    }
```
解析到字符串的时候会写入buffer。

### 扫描数字类型

``` java
    public final void scanNumber() {
        /** 记录当前流中token的开始位置, np指向数字字符索引 */
        np = bp;

        /** 兼容处理负数 */
        if (ch == '-') {
            sp++;
            next();
        }

        for (;;) {
            if (ch >= '0' && ch <= '9') {
                /** 如果是数字字符，递增索引位置 */
                sp++;
            } else {
                break;
            }
            next();
        }

        boolean isDouble = false;

        /** 如果遇到小数点字符 */
        if (ch == '.') {
            sp++;
            /** 继续读小数点后面字符 */
            next();
            isDouble = true;

            for (;;) {
                if (ch >= '0' && ch <= '9') {
                    sp++;
                } else {
                    break;
                }
                next();
            }
        }

        /** 继续读取数字后面的类型 */
        if (ch == 'L') {
            sp++;
            next();
        } else if (ch == 'S') {
            sp++;
            next();
        } else if (ch == 'B') {
            sp++;
            next();
        } else if (ch == 'F') {
            sp++;
            next();
            isDouble = true;
        } else if (ch == 'D') {
            sp++;
            next();
            isDouble = true;
        } else if (ch == 'e' || ch == 'E') {

            /** 扫描科学计数法 */
            sp++;
            next();

            if (ch == '+' || ch == '-') {
                sp++;
                next();
            }

            for (;;) {
                if (ch >= '0' && ch <= '9') {
                    sp++;
                } else {
                    break;
                }
                next();
            }

            if (ch == 'D' || ch == 'F') {
                sp++;
                next();
            }

            isDouble = true;
        }

        if (isDouble) {
            token = JSONToken.LITERAL_FLOAT;
        } else {
            token = JSONToken.LITERAL_INT;
        }
    }
```

### 扫描Boolean

``` java
    public final void scanTrue() {
        if (ch != 't') {
            throw new JSONException("error parse true");
        }
        next();

        if (ch != 'r') {
            throw new JSONException("error parse true");
        }
        next();

        if (ch != 'u') {
            throw new JSONException("error parse true");
        }
        next();

        if (ch != 'e') {
            throw new JSONException("error parse true");
        }
        next();

        if (ch == ' ' || ch == ',' || ch == '}' || ch == ']' || ch == '\n' || ch == '\r' || ch == '\t' || ch == EOI
                || ch == '\f' || ch == '\b' || ch == ':' || ch == '/') {
            /** 兼容性防御，标记是true的token */
            token = JSONToken.TRUE;
        } else {
            throw new JSONException("scan true error");
        }
    }
```

### 扫描标识符

``` java
    public final void scanIdent() {
        /** 记录当前流中token的开始位置, np指向当前token前一个字符 */
        np = bp - 1;
        hasSpecial = false;

        for (;;) {
            sp++;

            next();
            /** 如果是字母或数字，继续读取 */
            if (Character.isLetterOrDigit(ch)) {
                continue;
            }

            /** 获取字符串值 */
            String ident = stringVal();

            if ("null".equalsIgnoreCase(ident)) {
                token = JSONToken.NULL;
            } else if ("new".equals(ident)) {
                token = JSONToken.NEW;
            } else if ("true".equals(ident)) {
                token = JSONToken.TRUE;
            } else if ("false".equals(ident)) {
                token = JSONToken.FALSE;
            } else if ("undefined".equals(ident)) {
                token = JSONToken.UNDEFINED;
            } else if ("Set".equals(ident)) {
                token = JSONToken.SET;
            } else if ("TreeSet".equals(ident)) {
                token = JSONToken.TREE_SET;
            } else {
                token = JSONToken.IDENTIFIER;
            }
            return;
        }
    }
```

#### 扫描十六进制数

``` java
    public final void scanHex() {
        if (ch != 'x') {
            throw new JSONException("illegal state. " + ch);
        }
        next();
        /** 十六进制x紧跟着单引号 */
        /** @see com.alibaba.fastjson.serializer.SerializeWriter#writeHex(byte[]) */
        if (ch != '\'') {
            throw new JSONException("illegal state. " + ch);
        }

        np = bp;
        /** 这里一次next, for循环也读一次next, 因为十六进制被写成2个字节的单字符 */
        next();

        for (int i = 0;;++i) {
            char ch = next();
            if ((ch >= '0' && ch <= '9') || (ch >= 'A' && ch <= 'F')) {
                sp++;
                continue;
            } else if (ch == '\'') {
                sp++;
                /** 遇到结束符号，自动预读下一个字符 */
                next();
                break;
            } else {
                throw new JSONException("illegal state. " + ch);
            }
        }
        token = JSONToken.HEX;
    }

```

### 根据期望字符扫描token

``` java
    public final void nextToken(int expect) {
        /** 将字符buffer pos设置为初始0 */
        sp = 0;

        for (;;) {

            switch (expect) {
                case JSONToken.LBRACE:
                    if (ch == '{') {
                        token = JSONToken.LBRACE;
                        next();
                        return;
                    }
                    if (ch == '[') {
                        token = JSONToken.LBRACKET;
                        next();
                        return;
                    }
                    break;
                case JSONToken.COMMA:
                    if (ch == ',') {
                        token = JSONToken.COMMA;
                        next();
                        return;
                    }

                    if (ch == '}') {
                        token = JSONToken.RBRACE;
                        next();
                        return;
                    }

                    if (ch == ']') {
                        token = JSONToken.RBRACKET;
                        next();
                        return;
                    }

                    if (ch == EOI) {
                        token = JSONToken.EOF;
                        return;
                    }
                    break;
                case JSONToken.LITERAL_INT:
                    if (ch >= '0' && ch <= '9') {
                        pos = bp;
                        scanNumber();
                        return;
                    }

                    if (ch == '"') {
                        pos = bp;
                        scanString();
                        return;
                    }

                    if (ch == '[') {
                        token = JSONToken.LBRACKET;
                        next();
                        return;
                    }

                    if (ch == '{') {
                        token = JSONToken.LBRACE;
                        next();
                        return;
                    }

                    break;
                case JSONToken.LITERAL_STRING:
                    if (ch == '"') {
                        pos = bp;
                        /** 扫描字符串, pos指向字符串引号索引 */
                        scanString();
                        return;
                    }

                    if (ch >= '0' && ch <= '9') {
                        pos = bp;
                        /** 扫描数字, 前面已经分析过 */
                        scanNumber();
                        return;
                    }

                    if (ch == '[') {
                        token = JSONToken.LBRACKET;
                        next();
                        return;
                    }

                    if (ch == '{') {
                        token = JSONToken.LBRACE;
                        next();
                        return;
                    }
                    break;
                case JSONToken.LBRACKET:
                    if (ch == '[') {
                        token = JSONToken.LBRACKET;
                        next();
                        return;
                    }

                    if (ch == '{') {
                        token = JSONToken.LBRACE;
                        next();
                        return;
                    }
                    break;
                case JSONToken.RBRACKET:
                    if (ch == ']') {
                        token = JSONToken.RBRACKET;
                        next();
                        return;
                    }
                case JSONToken.EOF:
                    if (ch == EOI) {
                        token = JSONToken.EOF;
                        return;
                    }
                    break;
                case JSONToken.IDENTIFIER:
                    /** 跳过空白字符，如果是标识符_、$和字母开头，否则自动获取下一个token */
                    nextIdent();
                    return;
                default:
                    break;
            }

            /** 跳过空白字符 */
            if (ch == ' ' || ch == '\n' || ch == '\r' || ch == '\t' || ch == '\f' || ch == '\b') {
                next();
                continue;
            }

            /** 针对其他token自动读取下一个, 比如遇到冒号：,自动下一个token */
            nextToken();
            break;
        }
    }
```

这个方法主要是根据期望的字符expect，判定expect对应的token, 接下来主要分析解析对象字段的相关api实现。