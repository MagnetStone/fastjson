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

```java
    public static <T> T deserialze(DefaultJSONParser parser) {
        final JSONLexer lexer = parser.lexer;

        if (lexer.token() == JSONToken.LITERAL_INT) {
            /** 整型字面量，预读下一个token */
            String val = lexer.numberString();
            lexer.nextToken(JSONToken.COMMA);
            return (T) Float.valueOf(Float.parseFloat(val));
        }

        if (lexer.token() == JSONToken.LITERAL_FLOAT) {
            /** 浮点数字面量，预读下一个token */
            float val = lexer.floatValue();
            lexer.nextToken(JSONToken.COMMA);
            return (T) Float.valueOf(val);
        }

        /** 处理其他情况 */
        Object value = parser.parse();

        if (value == null) {
            return null;
        }

        return (T) TypeUtils.castToFloat(value);
    }
```

### BigDecimalCodec反序列化

```java
    public static <T> T deserialze(DefaultJSONParser parser) {
        final JSONLexer lexer = parser.lexer;
        if (lexer.token() == JSONToken.LITERAL_INT) {
            /** 整型字面量，预读下一个token */
            BigDecimal decimalValue = lexer.decimalValue();
            lexer.nextToken(JSONToken.COMMA);
            return (T) decimalValue;
        }

        if (lexer.token() == JSONToken.LITERAL_FLOAT) {
            /** 浮点数字面量，预读下一个token */
            BigDecimal val = lexer.decimalValue();
            lexer.nextToken(JSONToken.COMMA);
            return (T) val;
        }

        Object value = parser.parse();
        return value == null //
            ? null //
            : (T) TypeUtils.castToBigDecimal(value);
    }
```

### StringCodec反序列化

``` java
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
        if (clazz == StringBuffer.class) {
            /** 将解析的字符序列转换成StringBuffer */
            final JSONLexer lexer = parser.lexer;
            if (lexer.token() == JSONToken.LITERAL_STRING) {
                /** 字符串字面量，预读下一个token */
                String val = lexer.stringVal();
                lexer.nextToken(JSONToken.COMMA);

                return (T) new StringBuffer(val);
            }

            Object value = parser.parse();

            if (value == null) {
                return null;
            }

            return (T) new StringBuffer(value.toString());
        }

        if (clazz == StringBuilder.class) {
            /** 将解析的字符序列转换成StringBuilder */
            final JSONLexer lexer = parser.lexer;
            if (lexer.token() == JSONToken.LITERAL_STRING) {
                String val = lexer.stringVal();
                /** 字符串字面量，预读下一个token */
                lexer.nextToken(JSONToken.COMMA);

                return (T) new StringBuilder(val);
            }

            Object value = parser.parse();

            if (value == null) {
                return null;
            }

            return (T) new StringBuilder(value.toString());
        }

        return (T) deserialze(parser);
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserialze(DefaultJSONParser parser) {
        final JSONLexer lexer = parser.getLexer();
        if (lexer.token() == JSONToken.LITERAL_STRING) {
            /** 字符串字面量，预读下一个token */
            String val = lexer.stringVal();
            lexer.nextToken(JSONToken.COMMA);
            return (T) val;
        }

        if (lexer.token() == JSONToken.LITERAL_INT) {
            /** 整型字面量，预读下一个token */
            String val = lexer.numberString();
            lexer.nextToken(JSONToken.COMMA);
            return (T) val;
        }

        Object value = parser.parse();

        if (value == null) {
            return null;
        }

        return (T) value.toString();
    }
```

### ObjectArrayCodec反序列化

``` java
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
        final JSONLexer lexer = parser.lexer;
        int token = lexer.token();
        if (token == JSONToken.NULL) {
            /** 解析到Null，预读下一个token */
            lexer.nextToken(JSONToken.COMMA);
            return null;
        }

        if (token == JSONToken.LITERAL_STRING || token == JSONToken.HEX) {
            byte[] bytes = lexer.bytesValue();
            lexer.nextToken(JSONToken.COMMA);

            if (bytes.length == 0 && type != byte[].class) {
                return null;
            }

            return (T) bytes;
        }

        Class componentClass;
        Type componentType;
        if (type instanceof GenericArrayType) {
            GenericArrayType clazz = (GenericArrayType) type;
            /** 获取泛型数组真实参数类型 */
            componentType = clazz.getGenericComponentType();
            if (componentType instanceof TypeVariable) {
                TypeVariable typeVar = (TypeVariable) componentType;
                Type objType = parser.getContext().type;
                if (objType instanceof ParameterizedType) {
                    /** 获取泛型参数化类型，eg: Collection<String> */
                    ParameterizedType objParamType = (ParameterizedType) objType;
                    Type objRawType = objParamType.getRawType();
                    Type actualType = null;
                    if (objRawType instanceof Class) {
                        /** 遍历Class包含的参数化类型，查找与泛型数组类型名字一致的作为真实类型 */
                        TypeVariable[] objTypeParams = ((Class) objRawType).getTypeParameters();
                        for (int i = 0; i < objTypeParams.length; ++i) {
                            if (objTypeParams[i].getName().equals(typeVar.getName())) {
                                actualType = objParamType.getActualTypeArguments()[i];
                            }
                        }
                    }
                    if (actualType instanceof Class) {
                        componentClass = (Class) actualType;
                    } else {
                        componentClass = Object.class;
                    }
                } else {
                    // 获取数组类型上界
                    componentClass = TypeUtils.getClass(typeVar.getBounds()[0]);
                }
            } else {
                componentClass = TypeUtils.getClass(componentType);
            }
        } else {
            /** 非泛型数组，普通对象数组 */
            Class clazz = (Class) type;
            componentType = componentClass = clazz.getComponentType();
        }
        JSONArray array = new JSONArray();
        /** 根据token解析数组元素放到array中 */
        parser.parseArray(componentType, array, fieldName);

        return (T) toObjectArray(parser, componentClass, array);
    }

```

### JavaBeanDeserializer反序列化

为了节省冗余的分析，我们主要分析最复杂的默认`JavaBeanDeserializer`反序列化实现。


``` java
   public JavaBeanDeserializer(ParserConfig config, JavaBeanInfo beanInfo){
        /** java对象类名称 */
        this.clazz = beanInfo.clazz;
        this.beanInfo = beanInfo;

        Map<String, FieldDeserializer> alterNameFieldDeserializers = null;
        sortedFieldDeserializers = new FieldDeserializer[beanInfo.sortedFields.length];
        /**
         *  给已排序的字段创建反序列化实例，如果字段有别名，
         *  关联别名到反序列化的映射
         */
        for (int i = 0, size = beanInfo.sortedFields.length; i < size; ++i) {
            FieldInfo fieldInfo = beanInfo.sortedFields[i];
            FieldDeserializer fieldDeserializer = config.createFieldDeserializer(config, beanInfo, fieldInfo);

            sortedFieldDeserializers[i] = fieldDeserializer;

            for (String name : fieldInfo.alternateNames) {
                if (alterNameFieldDeserializers == null) {
                    alterNameFieldDeserializers = new HashMap<String, FieldDeserializer>();
                }
                alterNameFieldDeserializers.put(name, fieldDeserializer);
            }
        }
        this.alterNameFieldDeserializers = alterNameFieldDeserializers;

        fieldDeserializers = new FieldDeserializer[beanInfo.fields.length];
        for (int i = 0, size = beanInfo.fields.length; i < size; ++i) {
            FieldInfo fieldInfo = beanInfo.fields[i];
            /** 采用二分法在sortedFieldDeserializers中查找已创建的反序列化类型 */
            FieldDeserializer fieldDeserializer = getFieldDeserializer(fieldInfo.name);
            fieldDeserializers[i] = fieldDeserializer;
        }
    }
```

构造函数就是简单构造类字段对应的反序列化实例而已，接下来看下关键实现：

``` java
    protected <T> T deserialze(DefaultJSONParser parser,
                               Type type,
                               Object fieldName,
                               Object object,
                               int features,
                               int[] setFlags) {
        if (type == JSON.class || type == JSONObject.class) {
            /** 根据当前token类型判断解析对象 */
            return (T) parser.parse();
        }

        final JSONLexerBase lexer = (JSONLexerBase) parser.lexer;
        final ParserConfig config = parser.getConfig();

        int token = lexer.token();
        if (token == JSONToken.NULL) {
            /** 解析null，预读下一个token并返回 */
            lexer.nextToken(JSONToken.COMMA);
            return null;
        }

        ParseContext context = parser.getContext();
        if (object != null && context != null) {
            context = context.parent;
        }
        ParseContext childContext = null;

        try {
            Map<String, Object> fieldValues = null;

            if (token == JSONToken.RBRACE) {
                lexer.nextToken(JSONToken.COMMA);
                /** 遇到}认为遇到对象结束，尝试创建实例对象 */
                if (object == null) {
                    object = createInstance(parser, type);
                }
                return (T) object;
            }

            if (token == JSONToken.LBRACKET) {
                final int mask = Feature.SupportArrayToBean.mask;
                boolean isSupportArrayToBean = (beanInfo.parserFeatures & mask) != 0
                                               || lexer.isEnabled(Feature.SupportArrayToBean)
                                               || (features & mask) != 0
                                               ;
                if (isSupportArrayToBean) {
                    /** 将数组值反序列化为对象，根据sortedFieldDeserializers依次写字段值 */
                    return deserialzeArrayMapping(parser, type, fieldName, object);
                }
            }

            if (token != JSONToken.LBRACE && token != JSONToken.COMMA) {
                if (lexer.isBlankInput()) {
                    return null;
                }

                if (token == JSONToken.LITERAL_STRING) {
                    String strVal = lexer.stringVal();
                    /** 读到空值字符串，返回null */
                    if (strVal.length() == 0) {
                        lexer.nextToken();
                        return null;
                    }

                    if (beanInfo.jsonType != null) {
                        /** 探测是否是枚举类型 */
                        for (Class<?> seeAlsoClass : beanInfo.jsonType.seeAlso()) {
                            if (Enum.class.isAssignableFrom(seeAlsoClass)) {
                                try {
                                    Enum<?> e = Enum.valueOf((Class<Enum>) seeAlsoClass, strVal);
                                    return (T) e;
                                } catch (IllegalArgumentException e) {
                                    // skip
                                }
                            }
                        }
                    }
                } else if (token == JSONToken.LITERAL_ISO8601_DATE) {
                    Calendar calendar = lexer.getCalendar();
                }

                if (token == JSONToken.LBRACKET && lexer.getCurrent() == ']') {
                    /** 包含零元素的数组 */
                    lexer.next();
                    lexer.nextToken();
                    return null;
                }
                
                StringBuffer buf = (new StringBuffer()) //
                                                        .append("syntax error, expect {, actual ") //
                                                        .append(lexer.tokenName()) //
                                                        .append(", pos ") //
                                                        .append(lexer.pos());

                if (fieldName instanceof String) {
                    buf //
                        .append(", fieldName ") //
                        .append(fieldName);
                }

                buf.append(", fastjson-version ").append(JSON.VERSION);
                
                throw new JSONException(buf.toString());
            }

            if (parser.resolveStatus == DefaultJSONParser.TypeNameRedirect) {
                parser.resolveStatus = DefaultJSONParser.NONE;
            }

            String typeKey = beanInfo.typeKey;
            for (int fieldIndex = 0;; fieldIndex++) {
                String key = null;
                FieldDeserializer fieldDeser = null;
                FieldInfo fieldInfo = null;
                Class<?> fieldClass = null;
                JSONField feildAnnotation = null;
                /** 检查是否所有字段都已经处理 */
                if (fieldIndex < sortedFieldDeserializers.length) {
                    fieldDeser = sortedFieldDeserializers[fieldIndex];
                    fieldInfo = fieldDeser.fieldInfo;
                    fieldClass = fieldInfo.fieldClass;
                    feildAnnotation = fieldInfo.getAnnotation();
                }

                boolean matchField = false;
                boolean valueParsed = false;
                
                Object fieldValue = null;
                if (fieldDeser != null) {
                    char[] name_chars = fieldInfo.name_chars;
                    if (fieldClass == int.class || fieldClass == Integer.class) {
                        /** 扫描整数值 */
                        fieldValue = lexer.scanFieldInt(name_chars);
                        
                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            continue;  
                        }
                    } else if (fieldClass == long.class || fieldClass == Long.class) {
                        /** 扫描长整型值 */
                        fieldValue = lexer.scanFieldLong(name_chars);
                        
                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            continue;  
                        }
                    } else if (fieldClass == String.class) {
                        /** 扫描字符串值 */
                        fieldValue = lexer.scanFieldString(name_chars);
                        
                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            continue;  
                        }
                    } else if (fieldClass == java.util.Date.class && fieldInfo.format == null) {
                        /** 扫描日期值 */
                        fieldValue = lexer.scanFieldDate(name_chars);

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            continue;
                        }
                    } else if (fieldClass == BigDecimal.class) {
                        /** 扫描高精度值 */
                        fieldValue = lexer.scanFieldDecimal(name_chars);

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            continue;
                        }
                    } else if (fieldClass == BigInteger.class) {
                        fieldValue = lexer.scanFieldBigInteger(name_chars);

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            continue;
                        }
                    } else if (fieldClass == boolean.class || fieldClass == Boolean.class) {
                        /** 扫描boolean值 */
                        fieldValue = lexer.scanFieldBoolean(name_chars);
                        
                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            continue;  
                        }
                    } else if (fieldClass == float.class || fieldClass == Float.class) {
                        /** 扫描浮点值 */
                        fieldValue = lexer.scanFieldFloat(name_chars);
                        
                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            continue;  
                        }
                    } else if (fieldClass == double.class || fieldClass == Double.class) {
                        /** 扫描double值 */
                        fieldValue = lexer.scanFieldDouble(name_chars);
                        
                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            continue;  
                        }

                    } else if (fieldClass.isEnum()
                            && parser.getConfig().getDeserializer(fieldClass) instanceof EnumDeserializer
                            && (feildAnnotation == null || feildAnnotation.deserializeUsing() == Void.class)
                            ) {
                        if (fieldDeser instanceof DefaultFieldDeserializer) {
                            ObjectDeserializer fieldValueDeserilizer = ((DefaultFieldDeserializer) fieldDeser).fieldValueDeserilizer;
                            fieldValue = this.scanEnum(lexer, name_chars, fieldValueDeserilizer);

                            if (lexer.matchStat > 0) {
                                matchField = true;
                                valueParsed = true;
                            } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                                continue;
                            }
                        }
                    } else if (fieldClass == int[].class) {
                        /** 扫描整型数组值 */
                        fieldValue = lexer.scanFieldIntArray(name_chars);

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            continue;
                        }
                    } else if (fieldClass == float[].class) {
                        fieldValue = lexer.scanFieldFloatArray(name_chars);

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            continue;
                        }
                    } else if (fieldClass == float[][].class) {
                        /** 扫描浮点数组值 */
                        fieldValue = lexer.scanFieldFloatArray2(name_chars);

                        if (lexer.matchStat > 0) {
                            matchField = true;
                            valueParsed = true;
                        } else if (lexer.matchStat == JSONLexer.NOT_MATCH_NAME) {
                            continue;
                        }
                    } else if (lexer.matchField(name_chars)) {
                        matchField = true;
                    } else {
                        continue;
                    }
                }

                /** 如果当前字符串的json不匹配当前字段名称 */
                if (!matchField) {
                    /** 将当前的字段名称加入符号表 */
                    key = lexer.scanSymbol(parser.symbolTable);

                    /** 当前是无效的字段标识符，比如是,等符号 */
                    if (key == null) {
                        token = lexer.token();
                        if (token == JSONToken.RBRACE) {
                            /** 结束花括号, 预读下一个token */
                            lexer.nextToken(JSONToken.COMMA);
                            break;
                        }
                        if (token == JSONToken.COMMA) {
                            if (lexer.isEnabled(Feature.AllowArbitraryCommas)) {
                                continue;
                            }
                        }
                    }

                    if ("$ref" == key && context != null) {
                        lexer.nextTokenWithColon(JSONToken.LITERAL_STRING);
                        token = lexer.token();
                        if (token == JSONToken.LITERAL_STRING) {
                            String ref = lexer.stringVal();
                            if ("@".equals(ref)) {
                                object = context.object;
                            } else if ("..".equals(ref)) {
                                ParseContext parentContext = context.parent;
                                if (parentContext.object != null) {
                                    object = parentContext.object;
                                } else {
                                    parser.addResolveTask(new ResolveTask(parentContext, ref));
                                    parser.resolveStatus = DefaultJSONParser.NeedToResolve;
                                }
                            } else if ("$".equals(ref)) {
                                ParseContext rootContext = context;
                                while (rootContext.parent != null) {
                                    rootContext = rootContext.parent;
                                }

                                if (rootContext.object != null) {
                                    object = rootContext.object;
                                } else {
                                    parser.addResolveTask(new ResolveTask(rootContext, ref));
                                    parser.resolveStatus = DefaultJSONParser.NeedToResolve;
                                }
                            } else {
                                Object refObj = parser.resolveReference(ref);
                                if (refObj != null) {
                                    object = refObj;
                                } else {
                                    parser.addResolveTask(new ResolveTask(context, ref));
                                    parser.resolveStatus = DefaultJSONParser.NeedToResolve;
                                }
                            }
                        } else {
                            throw new JSONException("illegal ref, " + JSONToken.name(token));
                        }

                        lexer.nextToken(JSONToken.RBRACE);
                        if (lexer.token() != JSONToken.RBRACE) {
                            throw new JSONException("illegal ref");
                        }
                        lexer.nextToken(JSONToken.COMMA);

                        parser.setContext(context, object, fieldName);

                        return (T) object;
                    }

                    if ((typeKey != null && typeKey.equals(key))
                            || JSON.DEFAULT_TYPE_KEY == key) {
                        lexer.nextTokenWithColon(JSONToken.LITERAL_STRING);
                        if (lexer.token() == JSONToken.LITERAL_STRING) {
                            String typeName = lexer.stringVal();
                            lexer.nextToken(JSONToken.COMMA);

                            /** 忽略字符串中包含@type解析 */
                            if (typeName.equals(beanInfo.typeName)|| parser.isEnabled(Feature.IgnoreAutoType)) {
                                if (lexer.token() == JSONToken.RBRACE) {
                                    lexer.nextToken();
                                    break;
                                }
                                continue;
                            }
                            

                            /** 根据枚举seeAlso查找反序列化实例 */
                            ObjectDeserializer deserializer = getSeeAlso(config, this.beanInfo, typeName);
                            Class<?> userType = null;

                            if (deserializer == null) {
                                /** 无法匹配，查找类对应的泛型或者参数化类型关联的反序列化实例 */
                                Class<?> expectClass = TypeUtils.getClass(type);
                                userType = config.checkAutoType(typeName, expectClass, lexer.getFeatures());
                                deserializer = parser.getConfig().getDeserializer(userType);
                            }

                            Object typedObject = deserializer.deserialze(parser, userType, fieldName);
                            if (deserializer instanceof JavaBeanDeserializer) {
                                JavaBeanDeserializer javaBeanDeserializer = (JavaBeanDeserializer) deserializer;
                                if (typeKey != null) {
                                    FieldDeserializer typeKeyFieldDeser = javaBeanDeserializer.getFieldDeserializer(typeKey);
                                    typeKeyFieldDeser.setValue(typedObject, typeName);
                                }
                            }
                            return (T) typedObject;
                        } else {
                            throw new JSONException("syntax error");
                        }
                    }
                }

                /** 第一次创建并初始化对象实例 */
                if (object == null && fieldValues == null) {
                    object = createInstance(parser, type);
                    if (object == null) {
                        fieldValues = new HashMap<String, Object>(this.fieldDeserializers.length);
                    }
                    childContext = parser.setContext(context, object, fieldName);
                    if (setFlags == null) {
                        setFlags = new int[(this.fieldDeserializers.length / 32) + 1];
                    }
                }

                if (matchField) {
                    if (!valueParsed) {
                        /** json串当前满足字段名称，并且没有解析过值 ，
                         *  直接使用当前字段关联的反序列化实例解析
                         */
                        fieldDeser.parseField(parser, object, type, fieldValues);
                    } else {
                        if (object == null) {
                            /** 值已经解析过了，存储到map中 */
                            fieldValues.put(fieldInfo.name, fieldValue);
                        } else if (fieldValue == null) {
                            /** 字段值是null，排除int,long,float,double,boolean */
                            if (fieldClass != int.class
                                    && fieldClass != long.class
                                    && fieldClass != float.class
                                    && fieldClass != double.class
                                    && fieldClass != boolean.class
                                    ) {
                                fieldDeser.setValue(object, fieldValue);
                            }
                        } else {
                            fieldDeser.setValue(object, fieldValue);
                        }

                        if (setFlags != null) {
                            int flagIndex = fieldIndex / 32;
                            int bitIndex = fieldIndex % 32;
                            setFlags[flagIndex] |= (1 >> bitIndex);
                        }

                        if (lexer.matchStat == JSONLexer.END) {
                            break;
                        }
                    }
                } else {
                    /** 字段名称当前和json串不匹配，通常顺序或者字段增加或者缺少，
                     *  根据key查找反序列化实例解析
                     */
                    boolean match = parseField(parser, key, object, type, fieldValues, setFlags);
                    if (!match) {
                        /** 遇到封闭花括号，与读下一个token，跳出循环 */
                        if (lexer.token() == JSONToken.RBRACE) {
                            lexer.nextToken();
                            break;
                        }

                        continue;
                    } else if (lexer.token() == JSONToken.COLON) {
                        throw new JSONException("syntax error, unexpect token ':'");
                    }
                }

                if (lexer.token() == JSONToken.COMMA) {
                    continue;
                }

                if (lexer.token() == JSONToken.RBRACE) {
                    lexer.nextToken(JSONToken.COMMA);
                    break;
                }

                if (lexer.token() == JSONToken.IDENTIFIER || lexer.token() == JSONToken.ERROR) {
                    throw new JSONException("syntax error, unexpect token " + JSONToken.name(lexer.token()));
                }
            }

            if (object == null) {
                if (fieldValues == null) {
                    /** 第一次创建并初始化对象实例 */
                    object = createInstance(parser, type);
                    if (childContext == null) {
                        childContext = parser.setContext(context, object, fieldName);
                    }
                    return (T) object;
                }

                /** 提取构造函数参数名称 */
                String[] paramNames = beanInfo.creatorConstructorParameters;
                final Object[] params;
                if (paramNames != null) {
                    params = new Object[paramNames.length];
                    for (int i = 0; i < paramNames.length; i++) {
                        String paramName = paramNames[i];

                        Object param = fieldValues.remove(paramName);
                        /** 解析过的字段不包含当前参数名字 */
                        if (param == null) {
                            Type fieldType = beanInfo.creatorConstructorParameterTypes[i];
                            FieldInfo fieldInfo = beanInfo.fields[i];
                            /** 探测并设置类型默认值 */
                            if (fieldType == byte.class) {
                                param = (byte) 0;
                            } else if (fieldType == short.class) {
                                param = (short) 0;
                            } else if (fieldType == int.class) {
                                param = 0;
                            } else if (fieldType == long.class) {
                                param = 0L;
                            } else if (fieldType == float.class) {
                                param = 0F;
                            } else if (fieldType == double.class) {
                                param = 0D;
                            } else if (fieldType == boolean.class) {
                                param = Boolean.FALSE;
                            } else if (fieldType == String.class
                                    && (fieldInfo.parserFeatures & Feature.InitStringFieldAsEmpty.mask) != 0) {
                                param = "";
                            }
                        }
                        params[i] = param;
                    }
                } else {
                    /** 根据字段探测并初始化构造函数参数默认值 */
                    FieldInfo[] fieldInfoList = beanInfo.fields;
                    int size = fieldInfoList.length;
                    params = new Object[size];
                    for (int i = 0; i < size; ++i) {
                        FieldInfo fieldInfo = fieldInfoList[i];
                        Object param = fieldValues.get(fieldInfo.name);
                        if (param == null) {
                            Type fieldType = fieldInfo.fieldType;
                            if (fieldType == byte.class) {
                                param = (byte) 0;
                            } else if (fieldType == short.class) {
                                param = (short) 0;
                            } else if (fieldType == int.class) {
                                param = 0;
                            } else if (fieldType == long.class) {
                                param = 0L;
                            } else if (fieldType == float.class) {
                                param = 0F;
                            } else if (fieldType == double.class) {
                                param = 0D;
                            } else if (fieldType == boolean.class) {
                                param = Boolean.FALSE;
                            } else if (fieldType == String.class
                                    && (fieldInfo.parserFeatures & Feature.InitStringFieldAsEmpty.mask) != 0) {
                                param = "";
                            }
                        }
                        params[i] = param;
                    }
                }

                if (beanInfo.creatorConstructor != null) {
                    try {
                        object = beanInfo.creatorConstructor.newInstance(params);
                    } catch (Exception e) {
                        throw new JSONException("create instance error, " + paramNames + ", "
                                                + beanInfo.creatorConstructor.toGenericString(), e);
                    }

                    if (paramNames != null) {
                        /** 剩余字段查找反序列化器set值 */
                        for (Map.Entry<String, Object> entry : fieldValues.entrySet()) {
                            FieldDeserializer fieldDeserializer = getFieldDeserializer(entry.getKey());
                            if (fieldDeserializer != null) {
                                fieldDeserializer.setValue(object, entry.getValue());
                            }
                        }
                    }
                } else if (beanInfo.factoryMethod != null) {
                    try {
                        object = beanInfo.factoryMethod.invoke(null, params);
                    } catch (Exception e) {
                        throw new JSONException("create factory method error, " + beanInfo.factoryMethod.toString(), e);
                    }
                }

                childContext.object = object;
            }

            /** 检查是否扩展后置方法buildMethod，如果有进行调用 */
            Method buildMethod = beanInfo.buildMethod;
            if (buildMethod == null) {
                return (T) object;
            }
            
            
            Object builtObj;
            try {
                builtObj = buildMethod.invoke(object);
            } catch (Exception e) {
                throw new JSONException("build object error", e);
            }
            
            return (T) builtObj;
        } finally {
            if (childContext != null) {
                childContext.object = object;
            }
            parser.setContext(context);
        }
    }
```


这段代码实在又臭又长，实际做的事情如下：

1. 根据类所有的字段，字段类型进行json串进行匹配，首先检查json串的值是否和当前字段名称相等，如果相等认为匹配成功，会创建实例对象并且把解析字段值set进去。
2. 如果当前json串顺序和java对象字段不一致怎么办，这个时候我字段又全部遍历完了，fastjson会自动把当前解析的字段名称加入符号表中，然后查找字段名称对应的反序列化实例进行set值操作
3. 当前实现提供了解析对象后buildMethod扩展点，如果提供了会进行回调然后返回