## 反序列化回调接口实现分析

### 内部注册的反序列化

fastjson针对常用的类型已经注册了反序列化实现方案，根据源代码注册`com.alibaba.fastjson.parser.ParserConfig#initDeserializers`可以得到列表：

| 注册的类型 | 反序列化实例 | 是否支持序列化 | 是否支持反序列化 |
| :--- | :--- | :---: | :---: |
| SimpleDateFormat | MiscCodec | 是 | 是 |
| Timestamp | SqlDateDeserializer | - | 是 |
| Date | SqlDateDeserializer | - | 是 |
| Time | TimeDeserializer | - | 是 |
| Date | DateCodec | 是 | 是 |
| Calendar | CalendarCodec | 是 | 是 |
| XMLGregorianCalendar | CalendarCodec | 是 | 是 |
| JSONObject | MapDeserializer | -| 是 |
| JSONArray | CollectionCodec | 是 | 是 |
| Map | MapDeserializer | -| 是 |
| HashMap | MapDeserializer | -| 是 |
| LinkedHashMap | MapDeserializer | -| 是 |
| TreeMap | MapDeserializer | -| 是 |
| ConcurrentMap | MapDeserializer | -| 是 |
| ConcurrentHashMap | MapDeserializer | -| 是 |
| Collection | CollectionCodec | 是 | 是 |
| List | CollectionCodec | 是 | 是 |
| ArrayList | CollectionCodec | 是 | 是 |
| Object | JavaObjectDeserializer | - | 是 |
| String | StringCodec | 是 | 是 |
| StringBuffer | StringCodec | 是 | 是 |
| StringBuilder | StringCodec | 是 | 是 |
| char | CharacterCodec | 是 | 是 |
| Character | CharacterCodec | 是 | 是 |
| byte | NumberDeserializer | - | 是 |
| Byte | NumberDeserializer | - | 是 |
| short | NumberDeserializer | - | 是 |
| Short | NumberDeserializer | - | 是 |
| int | IntegerCodec | 是 | 是 |
| Integer | IntegerCodec | 是 | 是 |
| long | LongCodec | 是 | 是 |
| Long | LongCodec | 是 | 是 |
| BigInteger | BigIntegerCodec | 是 | 是 |
| BigDecimal | BigDecimalCodec | 是 | 是 |
| float | FloatCodec | 是 | 是 |
| Float | FloatCodec | 是 | 是 |
| double | NumberDeserializer | 是 | 是 |
| Double | NumberDeserializer | 是 | 是 |
| boolean | BooleanCodec | 是 | 是 |
| Boolean | BooleanCodec | 是 | 是 |
| Class | MiscCodec | 是 | 是 |
| char[] | CharArrayCodec | 是 | 是 |
| AtomicBoolean | BooleanCodec | 是 | 是 |
| AtomicBoolean | IntegerCodec | 是 | 是 |
| AtomicLong | LongCodec | 是 | 是 |
| AtomicReference | ReferenceCodec | 是 | 是 |
| WeakReference | ReferenceCodec | 是 | 是 |
| SoftReference | ReferenceCodec | 是 | 是 |
| UUID | MiscCodec | 是 | 是 |
| TimeZone | MiscCodec | 是 | 是 |
| Locale | MiscCodec | 是 | 是 |
| Currency | MiscCodec | 是 | 是 |
| InetAddress | MiscCodec | 是 | 是 |
| Inet4Address | MiscCodec | 是 | 是 |
| Inet6Address | MiscCodec | 是 | 是 |
| InetSocketAddress | MiscCodec | 是 | 是 |
| File | MiscCodec | 是 | 是 |
| URI | MiscCodec | 是 | 是 |
| URL | MiscCodec | 是 | 是 |
| Pattern | MiscCodec | 是 | 是 |
| Charset | MiscCodec | 是 | 是 |
| JSONPath | MiscCodec | 是 | 是 |
| Number | NumberDeserializer | - | 是 |
| AtomicIntegerArray | AtomicCodec | 是 | 是 |
| AtomicLongArray | AtomicCodec | 是 | 是 |
| StackTraceElement | StackTraceElementDeserializer | - | 是 |
| Serializable | JavaObjectDeserializer | - | 是 |
| Cloneable | JavaObjectDeserializer | - | 是 |
| Comparable | JavaObjectDeserializer | - | 是 |
| Closeable | JavaObjectDeserializer | - | 是 |
| JSONPObject | JSONPDeserializer | - | 是 |

通过上面表格发现几乎把所有JDK常用的类型都注册了一遍，目的是在运行时能够查找到特定的反序列化实例而不需要使用默认Java的反序列化实例。

我们先从常见的类型开始分析反序列化实现。

### BooleanCodec反序列化

``` java
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
        final JSONLexer lexer = parser.lexer;

        Boolean boolObj;

        try {
            /** 遇到true类型的token，预读下一个token */
            if (lexer.token() == JSONToken.TRUE) {
                lexer.nextToken(JSONToken.COMMA);
                boolObj = Boolean.TRUE;
                /** 遇到false类型的token，预读下一个token */
            } else if (lexer.token() == JSONToken.FALSE) {
                lexer.nextToken(JSONToken.COMMA);
                boolObj = Boolean.FALSE;
            } else if (lexer.token() == JSONToken.LITERAL_INT) {
                /** 遇到整数类型的token，预读下一个token */
                int intValue = lexer.intValue();
                lexer.nextToken(JSONToken.COMMA);

                /** 1代表true，其他情况false */
                if (intValue == 1) {
                    boolObj = Boolean.TRUE;
                } else {
                    boolObj = Boolean.FALSE;
                }
            } else {
                Object value = parser.parse();

                if (value == null) {
                    return null;
                }

                /** 处理其他情况，比如Y,T代表true */
                boolObj = TypeUtils.castToBoolean(value);
            }
        } catch (Exception ex) {
            throw new JSONException("parseBoolean error, field : " + fieldName, ex);
        }

        /** 如果是原子类型 */
        if (clazz == AtomicBoolean.class) {
            return (T) new AtomicBoolean(boolObj.booleanValue());
        }

        return (T) boolObj;
    }
```

每次反序列化拿到token是，当前记录的字符`ch`变量实际是token结尾的下一个字符，`boolean`类型字段会触发该接口。

### CharacterCodec反序列化

``` java
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
        /** 根据token解析类型 */
        Object value = parser.parse();
        return value == null
            ? null
            /** 转换成char类型，如果是string取字符串第一个char */
            : (T) TypeUtils.castToChar(value);
    }

    public Object parse() {
        return parse(null);
    }
```

看着反序列化应该挺简单，但是内部解析值委托给了`DefaultJSONParser#parse(java.lang.Object)`, 会把字符串解析取第一个字符处理：

``` java
    public Object parse(Object fieldName) {
        final JSONLexer lexer = this.lexer;
        switch (lexer.token()) {
            /**
             *  ...忽略其他类型token，后面遇到会讲解
             * /
            case LITERAL_STRING:
                /** 探测到是字符串类型，解析值 */
                String stringLiteral = lexer.stringVal();
                lexer.nextToken(JSONToken.COMMA);

                if (lexer.isEnabled(Feature.AllowISO8601DateFormat)) {
                    JSONScanner iso8601Lexer = new JSONScanner(stringLiteral);
                    try {
                        if (iso8601Lexer.scanISO8601DateIfMatch()) {
                            return iso8601Lexer.getCalendar().getTime();
                        }
                    } finally {
                        iso8601Lexer.close();
                    }
                }

                return stringLiteral;
            /**
             *  ...忽略其他类型token，后面遇到会讲解
             * /
        }
    }
```

### IntegerCodec反序列化

``` java
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
        final JSONLexer lexer = parser.lexer;

        final int token = lexer.token();

        /** 如果解析到null值，返回null */
        if (token == JSONToken.NULL) {
            lexer.nextToken(JSONToken.COMMA);
            return null;
        }


        Integer intObj;
        try {
            if (token == JSONToken.LITERAL_INT) {
                /** 整型字面量，预读下一个token */
                int val = lexer.intValue();
                lexer.nextToken(JSONToken.COMMA);
                intObj = Integer.valueOf(val);
            } else if (token == JSONToken.LITERAL_FLOAT) {
                /** 浮点数字面量，预读下一个token */
                BigDecimal decimalValue = lexer.decimalValue();
                lexer.nextToken(JSONToken.COMMA);
                intObj = Integer.valueOf(decimalValue.intValue());
            } else {
                if (token == JSONToken.LBRACE) {

                    /** 处理历史原因反序列化AtomicInteger成map */
                    JSONObject jsonObject = new JSONObject(true);
                    parser.parseObject(jsonObject);
                    intObj = TypeUtils.castToInt(jsonObject);
                } else {
                    /** 处理其他情况 */
                    Object value = parser.parse();
                    intObj = TypeUtils.castToInt(value);
                }
            }
        } catch (Exception ex) {
            throw new JSONException("parseInt error, field : " + fieldName, ex);
        }

        
        if (clazz == AtomicInteger.class) {
            return (T) new AtomicInteger(intObj.intValue());
        }
        
        return (T) intObj;
    }
```

针对特殊场景AutomicInteger类型，可以通过单元测试`com.alibaba.json.bvt.parser.AtomicIntegerComptableAndroidTest#test_for_compatible_zero`进行动手实践调试：

``` java
    public void test_for_compatible_zero() throws Exception {
        String text = "{\"andIncrement\":-1,\"andDecrement\":0}";

        assertEquals(0, JSON.parseObject(text, AtomicInteger.class).intValue());
    }
```

继续对`parseObject(jsonObject)`进行分析：

``` java
    public Object parseObject(final Map object) {
        return parseObject(object, null);
    }


```

### LongCodec反序列化

因为和整数反序列化极其类似，请参考`IntegerCodec`不进行冗余分析。

### FloatCodec反序列化


### BigDecimalCodec反序列化


### BigIntegerCodec反序列化


### StringCodec反序列化


### ObjectArrayCodec反序列化

### MiscCodec反序列化

### AtomicCodec反序列化

### ReferenceCodec反序列化


### CollectionCodec反序列化
