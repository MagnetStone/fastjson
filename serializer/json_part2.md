## 序列化回调接口实现分析

### 内部注册的序列化

fastjson针对常用的类型已经注册了序列化实现方案：

| 注册的类型 | 序列化实例 | 是否支持序列化 | 是否支持反序列化 |
| :--- | :--- | :---: | :---: |
| Boolean | BooleanCodec | 是 | 是 |
| Character | CharacterCodec | 是 | 是 |
| Byte | IntegerCodec | 是 | 是 |
| Short | IntegerCodec | 是 | 是 |
| Integer | IntegerCodec | 是 | 是 |
| Long | LongCodec | 是 | 是 |
| Float | FloatCodec | 是 | 是 |
| Double | DoubleSerializer | 是 | - |
| BigDecimal | BigDecimalCodec | 是 | 是 |
| BigInteger | BigIntegerCodec | 是 | 是 |
| String | StringCodec | 是 | 是 |
| byte\[\] | PrimitiveArraySerializer | 是 | - |
| short\[\] | PrimitiveArraySerializer | 是 | - |
| int\[\] | PrimitiveArraySerializer | 是 | - |
| long\[\] | PrimitiveArraySerializer | 是 | - |
| float\[\] | PrimitiveArraySerializer | 是 | - |
| double\[\] | PrimitiveArraySerializer | 是 | - |
| boolean\[\] | PrimitiveArraySerializer | 是 | - |
| char\[\] | PrimitiveArraySerializer | 是 | - |
| Object\[\] | ObjectArrayCodec | 是 | 是 |
| Class | MiscCodec | 是 | 是 |
| SimpleDateFormat | MiscCodec | 是 | 是 |
| Currency | MiscCodec | 是 | 是 |
| TimeZone | MiscCodec | 是 | 是 |
| InetAddress | MiscCodec | 是 | 是 |
| Inet4Address | MiscCodec | 是 | 是 |
| Inet6Address | MiscCodec | 是 | 是 |
| InetSocketAddress | MiscCodec | 是 | 是 |
| File | MiscCodec | 是 | 是 |
| Appendable | AppendableSerializer | 是 | - |
| StringBuffer | AppendableSerializer | 是 | - |
| StringBuilder | AppendableSerializer | 是 | - |
| Charset | ToStringSerializer | 是 | - |
| Pattern | ToStringSerializer | 是 | - |
| Locale | ToStringSerializer | 是 | - |
| URI | ToStringSerializer | 是 | - |
| URL | ToStringSerializer | 是 | - |
| UUID | ToStringSerializer | 是 | - |
| AtomicBoolean | AtomicCodec | 是 | 是 |
| AtomicInteger | AtomicCodec | 是 | 是 |
| AtomicLong | AtomicCodec | 是 | 是 |
| AtomicReference | ReferenceCodec | 是 | 是 |
| AtomicIntegerArray | AtomicCodec | 是 | 是 |
| AtomicLongArray | AtomicCodec | 是 | 是 |
| WeakReference | ReferenceCodec | 是 | 是 |
| SoftReference | ReferenceCodec | 是 | 是 |
| LinkedList | CollectionCodec | 是 | 是 |

### BooleanCodec序列化

其实理解了前面分析`SerializeWriter`, 接下来的内容比较容易理解, `BooleanCodec` 序列化实现 ：

``` java
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;

        /** 当前object是boolean值, 如果为null,
         *  并且序列化开启WriteNullBooleanAsFalse特性, 输出false
         */
        Boolean value = (Boolean) object;
        if (value == null) {
            out.writeNull(SerializerFeature.WriteNullBooleanAsFalse);
            return;
        }

        if (value.booleanValue()) {
            out.write("true");
        } else {
            out.write("false");
        }
    }
```

`BooleanCodec`序列化实现主要判断是否开启如果为null值是否输出false，否则输出boolean字面量值。

### CharacterCodec序列化

``` java
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;

        Character value = (Character) object;
        if (value == null) {
            /** 字符串为空，输出空字符串 */
            out.writeString("");
            return;
        }

        char c = value.charValue();
        if (c == 0) {
            /** 空白字符，输出unicode空格字符 */
            out.writeString("\u0000");
        } else {
            /** 输出字符串值 */
            out.writeString(value.toString());
        }
    }
```
### IntegerCodec序列化

``` java
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;

        Number value = (Number) object;

        /** 当前object是整形值, 如果为null,
         *  并且序列化开启WriteNullNumberAsZero特性, 输出0
         */
        if (value == null) {
            out.writeNull(SerializerFeature.WriteNullNumberAsZero);
            return;
        }

        /** 判断整形或者长整型，直接输出 */
        if (object instanceof Long) {
            out.writeLong(value.longValue());
        } else {
            out.writeInt(value.intValue());
        }

        /** 如果开启WriteClassName特性，输出具体值类型 */
        if (out.isEnabled(SerializerFeature.WriteClassName)) {
            Class<?> clazz = value.getClass();
            if (clazz == Byte.class) {
                out.write('B');
            } else if (clazz == Short.class) {
                out.write('S');
            }
        }
    }
```

### LongCodec序列化

``` java
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;

        /** 当前object是长整形值, 如果为null,
         *  并且序列化开启WriteNullNumberAsZero特性, 输出0
         */
        if (object == null) {
            out.writeNull(SerializerFeature.WriteNullNumberAsZero);
        } else {
            long value = ((Long) object).longValue();
            out.writeLong(value);

            /** 如果长整型值范围和整型相同，显示添加L 标识为long */
            if (out.isEnabled(SerializerFeature.WriteClassName)
                && value <= Integer.MAX_VALUE && value >= Integer.MIN_VALUE
                && fieldType != Long.class
                && fieldType != long.class) {
                out.write('L');
            }
        }
    }
```

`Long`类型序列化会特殊标识值落在整数范围内，如果开启`WriteClassName`序列化特性，会追加L字符。


### FloatCodec序列化

``` java
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;

        /** 当前object是float值, 如果为null,
         *  并且序列化开启WriteNullNumberAsZero特性, 输出0
         */
        if (object == null) {
            out.writeNull(SerializerFeature.WriteNullNumberAsZero);
            return;
        }

        float floatValue = ((Float) object).floatValue();
        if (decimalFormat != null) {
            /** 转换一下浮点数值格式 */
            String floatText = decimalFormat.format(floatValue);
            out.write(floatText);
        } else {
            out.writeFloat(floatValue, true);
        }
    }
```

### BigDecimalCodec序列化

``` java
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;

        /** 当前object是BigDecimal值, 如果为null,
         *  并且序列化开启WriteNullNumberAsZero特性, 输出0
         */
        if (object == null) {
            out.writeNull(SerializerFeature.WriteNullNumberAsZero);
        } else {
            BigDecimal val = (BigDecimal) object;

            String outText;
            /** 如果序列化开启WriteBigDecimalAsPlain特性，搞定度输出不会包含指数e */
            if (out.isEnabled(SerializerFeature.WriteBigDecimalAsPlain)) {
                outText = val.toPlainString();
            } else {
                outText = val.toString();
            }
            out.write(outText);

            if (out.isEnabled(SerializerFeature.WriteClassName) && fieldType != BigDecimal.class && val.scale() == 0) {
                out.write('.');
            }
        }
    }
```

### BigIntegerCodec序列化

``` java
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;

        /** 当前object是BigInteger值, 如果为null,
         *  并且序列化开启WriteNullNumberAsZero特性, 输出0
         */
        if (object == null) {
            out.writeNull(SerializerFeature.WriteNullNumberAsZero);
            return;
        }
        
        BigInteger val = (BigInteger) object;
        out.write(val.toString());
    }
```

### StringCodec序列化

``` java
    public void write(JSONSerializer serializer, String value) {
        SerializeWriter out = serializer.out;

        /** 当前object是string值, 如果为null,
         *  并且序列化开启WriteNullStringAsEmpty特性, 输出空串""
         */
        if (value == null) {
            out.writeNull(SerializerFeature.WriteNullStringAsEmpty);
            return;
        }

        out.writeString(value);
    }
```

### PrimitiveArraySerializer序列化

``` java
    public final void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;
        
        if (object == null) {
            /** 当前object是数组值, 如果为null,
             *  并且序列化开启WriteNullListAsEmpty特性, 输出空串""
             */
            out.writeNull(SerializerFeature.WriteNullListAsEmpty);
            return;
        }

        /** 循环写int数组 */
        if (object instanceof int[]) {
            int[] array = (int[]) object;
            out.write('[');
            for (int i = 0; i < array.length; ++i) {
                if (i != 0) {
                    out.write(',');
                }
                out.writeInt(array[i]);
            }
            out.write(']');
            return;
        }

        /** 循环写short数组 */
        if (object instanceof short[]) {
            short[] array = (short[]) object;
            out.write('[');
            for (int i = 0; i < array.length; ++i) {
                if (i != 0) {
                    out.write(',');
                }
                out.writeInt(array[i]);
            }
            out.write(']');
            return;
        }

        /** 循环写long数组 */
        if (object instanceof long[]) {
            long[] array = (long[]) object;

            out.write('[');
            for (int i = 0; i < array.length; ++i) {
                if (i != 0) {
                    out.write(',');
                }
                out.writeLong(array[i]);
            }
            out.write(']');
            return;
        }

        /** 循环写boolean数组 */
        if (object instanceof boolean[]) {
            boolean[] array = (boolean[]) object;
            out.write('[');
            for (int i = 0; i < array.length; ++i) {
                if (i != 0) {
                    out.write(',');
                }
                out.write(array[i]);
            }
            out.write(']');
            return;
        }

        /** 循环写float数组 */
        if (object instanceof float[]) {
            float[] array = (float[]) object;
            out.write('[');
            for (int i = 0; i < array.length; ++i) {
                if (i != 0) {
                    out.write(',');
                }
                
                float item = array[i];
                if (Float.isNaN(item)) {
                    out.writeNull();
                } else {
                    out.append(Float.toString(item));
                }
            }
            out.write(']');
            return;
        }

        /** 循环写double数组 */
        if (object instanceof double[]) {
            double[] array = (double[]) object;
            out.write('[');
            for (int i = 0; i < array.length; ++i) {
                if (i != 0) {
                    out.write(',');
                }
                
                double item = array[i];
                if (Double.isNaN(item)) {
                    out.writeNull();
                } else {
                    out.append(Double.toString(item));
                }
            }
            out.write(']');
            return;
        }

        /** 写字节数组 */
        if (object instanceof byte[]) {
            byte[] array = (byte[]) object;
            out.writeByteArray(array);
            return;
        }

        /** char数组当做字符串 */
        char[] chars = (char[]) object;
        out.writeString(chars);
    }
```

### ObjectArrayCodec序列化

``` java
    public final void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features)
                                                                                                       throws IOException {
        SerializeWriter out = serializer.out;

        Object[] array = (Object[]) object;

        /** 当前object是数组对象, 如果为null,
         *  并且序列化开启WriteNullListAsEmpty特性, 输出空串[]
         */
        if (object == null) {
            out.writeNull(SerializerFeature.WriteNullListAsEmpty);
            return;
        }

        int size = array.length;

        int end = size - 1;

        /** 当前object是数组对象, 如果为没有元素, 输出空串[] */
        if (end == -1) {
            out.append("[]");
            return;
        }

        SerialContext context = serializer.context;
        serializer.setContext(context, object, fieldName, 0);

        try {
            Class<?> preClazz = null;
            ObjectSerializer preWriter = null;
            out.append('[');

            /**
             *  如果开启json格式化，循环输出数组对象，
             *  会根据数组元素class类型查找序列化实例输出
             */
            if (out.isEnabled(SerializerFeature.PrettyFormat)) {
                serializer.incrementIndent();
                serializer.println();
                for (int i = 0; i < size; ++i) {
                    if (i != 0) {
                        out.write(',');
                        serializer.println();
                    }
                    serializer.write(array[i]);
                }
                serializer.decrementIdent();
                serializer.println();
                out.write(']');
                return;
            }

            for (int i = 0; i < end; ++i) {
                Object item = array[i];

                if (item == null) {
                    out.append("null,");
                } else {
                    if (serializer.containsReference(item)) {
                        serializer.writeReference(item);
                    } else {
                        Class<?> clazz = item.getClass();

                        /** 如果当前序列化元素和前一次class类型相同，避免再一次class类型查找序列化实例 */
                        if (clazz == preClazz) {
                            preWriter.write(serializer, item, null, null, 0);
                        } else {
                            preClazz = clazz;
                            /** 查找数组元素class类型的序列化器 序列化item */
                            preWriter = serializer.getObjectWriter(clazz);
                            preWriter.write(serializer, item, null, null, 0);
                        }
                    }
                    out.append(',');
                }
            }

            Object item = array[end];

            if (item == null) {
                out.append("null]");
            } else {
                if (serializer.containsReference(item)) {
                    serializer.writeReference(item);
                } else {
                    serializer.writeWithFieldName(item, end);
                }
                out.append(']');
            }
        } finally {
            serializer.context = context;
        }
    }
```

`ObjectArrayCodec`序列化主要判断是否开启格式化输出json，如果是输出添加适当的缩进。针对数组元素不一样会根据元素class类型查找具体的序列化器输出，这里优化了如果元素相同的元素避免冗余的查找序列化器。

### MiscCodec序列化

``` java
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType,
                      int features) throws IOException {
        SerializeWriter out = serializer.out;

        if (object == null) {
            out.writeNull();
            return;
        }

        Class<?> objClass = object.getClass();

        String strVal;
        if (objClass == SimpleDateFormat.class) {
            String pattern = ((SimpleDateFormat) object).toPattern();

            /** 输出SimpleDateFormat类型的类型 */
            if (out.isEnabled(SerializerFeature.WriteClassName)) {
                if (object.getClass() != fieldType) {
                    out.write('{');
                    out.writeFieldName(JSON.DEFAULT_TYPE_KEY);
                    serializer.write(object.getClass().getName());
                    out.writeFieldValue(',', "val", pattern);
                    out.write('}');
                    return;
                }
            }

            /** 转换SimpleDateFormat对象成pattern字符串 */
            strVal = pattern;
        } else if (objClass == Class.class) {
            Class<?> clazz = (Class<?>) object;
            /** 转换Class对象成name字符串 */
            strVal = clazz.getName();
        } else if (objClass == InetSocketAddress.class) {
            InetSocketAddress address = (InetSocketAddress) object;

            InetAddress inetAddress = address.getAddress();
            /** 转换InetSocketAddress对象成地址和端口字符串 */
            out.write('{');
            if (inetAddress != null) {
                out.writeFieldName("address");
                serializer.write(inetAddress);
                out.write(',');
            }
            out.writeFieldName("port");
            out.writeInt(address.getPort());
            out.write('}');
            return;
        } else if (object instanceof File) {
            /** 转换File对象成文件路径字符串 */
            strVal = ((File) object).getPath();
        } else if (object instanceof InetAddress) {
            /** 转换InetAddress对象成主机地址字符串 */
            strVal = ((InetAddress) object).getHostAddress();
        } else if (object instanceof TimeZone) {
            TimeZone timeZone = (TimeZone) object;
            /** 转换TimeZone对象成时区id字符串 */
            strVal = timeZone.getID();
        } else if (object instanceof Currency) {
            Currency currency = (Currency) object;
            /** 转换Currency对象成币别编码字符串 */
            strVal = currency.getCurrencyCode();
        } else if (object instanceof JSONStreamAware) {
            JSONStreamAware aware = (JSONStreamAware) object;
            aware.writeJSONString(out);
            return;
        } else if (object instanceof Iterator) {
            Iterator<?> it = ((Iterator<?>) object);
            /** 迭代器转换成数组码字符串 [,,,] */
            writeIterator(serializer, out, it);
            return;
        } else if (object instanceof Iterable) {
            /** 迭代器转换成数组码字符串 [,,,] */
            Iterator<?> it = ((Iterable<?>) object).iterator();
            writeIterator(serializer, out, it);
            return;
        } else if (object instanceof Map.Entry) {
            Map.Entry entry = (Map.Entry) object;
            Object objKey = entry.getKey();
            Object objVal = entry.getValue();

            /** 输出map的Entry值 */
            if (objKey instanceof String) {
                String key = (String) objKey;

                if (objVal instanceof String) {
                    String value = (String) objVal;
                    out.writeFieldValueStringWithDoubleQuoteCheck('{', key, value);
                } else {
                    out.write('{');
                    out.writeFieldName(key);
                    /** 根据value的class类型查找序列化器并输出 */
                    serializer.write(objVal);
                }
            } else {
                /** 根据key、value的class类型查找序列化器并输出 */
                out.write('{');
                serializer.write(objKey);
                out.write(':');
                serializer.write(objVal);
            }
            out.write('}');
            return;
        } else if (object.getClass().getName().equals("net.sf.json.JSONNull")) {
            out.writeNull();
            return;
        } else {
            throw new JSONException("not support class : " + objClass);
        }

        out.writeString(strVal);
    }
```

`MiscCodec`序列化的主要思想是吧JDK内部常用的对象简化处理，比如TimeZone只保留id输出，极大地降低了输出字节大小。

### AppendableSerializer序列化

``` java
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {

        /** 当前object实现了Appendable接口, 如果为null,
         *  并且序列化开启WriteNullStringAsEmpty特性, 输出空串""
         */
        if (object == null) {
            SerializeWriter out = serializer.out;
            out.writeNull(SerializerFeature.WriteNullStringAsEmpty);
            return;
        }

        /** 输出对象toString结果作为json串 */
        serializer.write(object.toString());
    }
```

### ToStringSerializer序列化

``` java
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType,
                      int features) throws IOException {
        SerializeWriter out = serializer.out;

        /** 如果为null, 输出空串"null" */
        if (object == null) {
            out.writeNull();
            return;
        }

        /** 输出对象toString结果作为json串 */
        String strVal = object.toString();
        out.writeString(strVal);
    }
```

### AtomicCodec序列化

``` java
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;
        
        if (object instanceof AtomicInteger) {
            AtomicInteger val = (AtomicInteger) object;
            /** 获取整数输出 */
            out.writeInt(val.get());
            return;
        }
        
        if (object instanceof AtomicLong) {
            AtomicLong val = (AtomicLong) object;
            /** 获取长整数输出 */
            out.writeLong(val.get());
            return;
        }
        
        if (object instanceof AtomicBoolean) {
            AtomicBoolean val = (AtomicBoolean) object;
            /** 获取boolean值输出 */
            out.append(val.get() ? "true" : "false");
            return;
        }

        /** 当前object是原子数组类型, 如果为null，输出[] */
        if (object == null) {
            out.writeNull(SerializerFeature.WriteNullListAsEmpty);
            return;
        }

        /** 遍历AtomicIntegerArray，输出int数组类型 */
        if (object instanceof AtomicIntegerArray) {
            AtomicIntegerArray array = (AtomicIntegerArray) object;
            int len = array.length();
            out.write('[');
            for (int i = 0; i < len; ++i) {
                int val = array.get(i);
                if (i != 0) {
                    out.write(',');
                }
                out.writeInt(val);
            }
            out.write(']');
            
            return;
        }

        /** 遍历AtomicLongArray，输出long数组类型 */
        AtomicLongArray array = (AtomicLongArray) object;
        int len = array.length();
        out.write('[');
        for (int i = 0; i < len; ++i) {
            long val = array.get(i);
            if (i != 0) {
                out.write(',');
            }
            out.writeLong(val);
        }
        out.write(']');
    }
```

### ReferenceCodec序列化

``` java
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        Object item;
        /** 当前object是Reference类型,
         *  调用get()查找对应的class序列化器输出
         */
        if (object instanceof AtomicReference) {
            AtomicReference val = (AtomicReference) object;
            item = val.get();
        } else {
            item = ((Reference) object).get();
        }
        serializer.write(item);
    }
```

### CollectionCodec序列化

``` java
    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;

        /** 当前object是集合对象, 如果为null,
         *  并且序列化开启WriteNullListAsEmpty特性, 输出空串[]
         */
        if (object == null) {
            out.writeNull(SerializerFeature.WriteNullListAsEmpty);
            return;
        }

        Type elementType = null;
        if (out.isEnabled(SerializerFeature.WriteClassName)
                || SerializerFeature.isEnabled(features, SerializerFeature.WriteClassName))
        {
            /** 获取字段泛型类型 */
            elementType = TypeUtils.getCollectionItemType(fieldType);
        }

        Collection<?> collection = (Collection<?>) object;

        SerialContext context = serializer.context;
        serializer.setContext(context, object, fieldName, 0);

        if (out.isEnabled(SerializerFeature.WriteClassName)) {
            if (HashSet.class == collection.getClass()) {
                out.append("Set");
            } else if (TreeSet.class == collection.getClass()) {
                out.append("TreeSet");
            }
        }

        try {
            int i = 0;
            out.append('[');
            for (Object item : collection) {

                if (i++ != 0) {
                    out.append(',');
                }

                if (item == null) {
                    out.writeNull();
                    continue;
                }

                Class<?> clazz = item.getClass();

                /** 获取整形类型值，输出 */
                if (clazz == Integer.class) {
                    out.writeInt(((Integer) item).intValue());
                    continue;
                }

                /** 获取整形长类型值，输出并添加L标识(如果开启WriteClassName特性) */
                if (clazz == Long.class) {
                    out.writeLong(((Long) item).longValue());

                    if (out.isEnabled(SerializerFeature.WriteClassName)) {
                        out.write('L');
                    }
                    continue;
                }

                /** 根据集合类型查找序列化实例处理，JavaBeanSerializer后面单独分析 */
                ObjectSerializer itemSerializer = serializer.getObjectWriter(clazz);
                if (SerializerFeature.isEnabled(features, SerializerFeature.WriteClassName)
                        && itemSerializer instanceof JavaBeanSerializer) {
                    JavaBeanSerializer javaBeanSerializer = (JavaBeanSerializer) itemSerializer;
                    javaBeanSerializer.writeNoneASM(serializer, item, i - 1, elementType, features);
                } else {
                    itemSerializer.write(serializer, item, i - 1, elementType, features);
                }
            }
            out.append(']');
        } finally {
            serializer.context = context;
        }
    }
```