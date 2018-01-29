## 概要

fastjson序列化主要使用入口就是在`JSON.java`类中，它提供非常简便和友好的api将java对象转换成json字符串。

### JSON成员函数

```java
    /**
     *  便捷序列化java对象，序列化对象可以包含任意泛型属性字段，但是不适用本身是泛型的对象。
     *  默认序列化返回字符串，可以使用writeJSONString(Writer, Object, SerializerFeature[])
     *  将序列化字符串输出到指定输出器中
     */
    public static String toJSONString(Object object) {
        /**
         * 直接调用重载方法，将指定object序列化成json字符串，忽略序列化filter
         */
        return toJSONString(object, emptyFilters);
    }
```

使用便捷接口toJSONString方法，可以将任意java对象序列化为json字符串，内部调用`toJSONString(Object, SerializeFilter[], SerializerFeature... )` :

```java
    public static String toJSONString(Object object, SerializeFilter[] filters, SerializerFeature... features) {
        return toJSONString(object, SerializeConfig.globalInstance, filters, null, DEFAULT_GENERATE_FEATURE, features);
    }
```

继续跟踪方法调用到`toJSONString(Object, SerializeConfig ,SerializeFilter[], String, int, SerializerFeature... )` :

```java
    public static String toJSONString(Object object,                   /** 序列化对象    */
                                      SerializeConfig config,          /** 全局序列化配置 */
                                      SerializeFilter[] filters,       /** 序列化拦截器   */
                                      String dateFormat,               /** 序列化日期格式 */
                                      int defaultFeatures,             /** 默认序列化特性 */
                                      SerializerFeature... features) { /** 自定义序列化特性 */
        /** 初始化序列化writer，用features覆盖defaultFeatures配置 */
        SerializeWriter out = new SerializeWriter(null, defaultFeatures, features);

        try {

            /**
             *  初始化JSONSerializer，序列化类型由它委托config查找具体
             *  序列化处理器处理，序列化结果写入out的buffer中
             */
            JSONSerializer serializer = new JSONSerializer(out, config);

            if (dateFormat != null && dateFormat.length() != 0) {
                serializer.setDateFormat(dateFormat);
                /** 调用out 重新配置属性 并且打开WriteDateUseDateFormat特性 */
                serializer.config(SerializerFeature.WriteDateUseDateFormat, true);
            }

            if (filters != null) {
                for (SerializeFilter filter : filters) {
                    /** 添加拦截器 */
                    serializer.addFilter(filter);
                }
            }

            /** 使用序列化实例转换对象，查找具体序列化实例委托给config查找 */
            serializer.write(object);

            return out.toString();
        } finally {
            out.close();
        }
    }
```

这个序列化方法实际并不是真正执行序列化操作，首先做序列化特性配置，然后追加序列化拦截器，开始执行序列化对象操作委托给了config对象查找。

我们继续进入`serializer.write(object)` 查看：

```java
    public final void write(Object object) {
        if (object == null) {
            /** 如果对象为空，直接输出 "null" 字符串 */
            out.writeNull();
            return;
        }

        Class<?> clazz = object.getClass();
        /** 根据对象的Class类型查找具体序列化实例 */
        ObjectSerializer writer = getObjectWriter(clazz);

        try {
            /** 使用具体serializer实例处理对象 */
            writer.write(this, object, null, null, 0);
        } catch (IOException e) {
            throw new JSONException(e.getMessage(), e);
        }
    }
```

## 序列化回调接口

### ObjectSerializer序列化接口

我们发现真正序列化对象的时候是由具体`ObjectSerializer`实例完成，我们首先查看一下接口定义：

```java
    void write(JSONSerializer serializer, /** json序列化实例 */
               Object object,       /** 待序列化的对象*/
               Object fieldName,    /** 待序列化字段*/
               Type fieldType,      /** 待序列化字段类型 */
               int features) throws IOException;
```

当fastjson序列化特定的字段时会回调这个方法。

我们继续跟踪`writer.write(this, object, null, null, 0)` :

```java
    public final void write(Object object) {
        if (object == null) {
            /** 如果对象为空，直接输出 "null" 字符串 */
            out.writeNull();
            return;
        }

        Class<?> clazz = object.getClass();
        /** 根据对象的Class类型查找具体序列化实例 */
        ObjectSerializer writer = getObjectWriter(clazz);

        try {
            /** 使用具体serializer实例处理对象 */
            writer.write(this, object, null, null, 0);
        } catch (IOException e) {
            throw new JSONException(e.getMessage(), e);
        }
    }
```

我们发现在方法内部调用`getObjectWriter(clazz)`根据具体类型查找序列化实例，方法内部只有一行调用 `config.getObjectWriter(clazz)`，让我们更进一步查看委托实现细节`com.alibaba.fastjson.serializer.SerializeConfig#getObjectWriter(java.lang.Class<?>)`:

```java
    public ObjectSerializer getObjectWriter(Class<?> clazz) {
        return getObjectWriter(clazz, true);
    }
```

内部又调用`com.alibaba.fastjson.serializer.SerializeConfig#getObjectWriter(java.lang.Class<?>, boolean)`，这个类实现相对复杂了一些，我会按照代码顺序梳理所有序列化实例的要点 :

```java
    private ObjectSerializer getObjectWriter(Class<?> clazz, boolean create) {
        /** 首先从内部已经注册查找特定class的序列化实例 */
        ObjectSerializer writer = serializers.get(clazz);

        if (writer == null) {
            try {
                final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
                /** 使用当前线程类加载器 查找 META-INF/services/AutowiredObjectSerializer.class实现类 */
                for (Object o : ServiceLoader.load(AutowiredObjectSerializer.class, classLoader)) {
                    if (!(o instanceof AutowiredObjectSerializer)) {
                        continue;
                    }

                    AutowiredObjectSerializer autowired = (AutowiredObjectSerializer) o;
                    for (Type forType : autowired.getAutowiredFor()) {
                        /** 如果存在，注册到内部serializers缓存中 */
                        put(forType, autowired);
                    }
                }
            } catch (ClassCastException ex) {
                // skip
            }

            /** 尝试在已注册缓存找到特定class的序列化实例 */
            writer = serializers.get(clazz);
        }

        if (writer == null) {
            /** 使用加载JSON类的加载器 查找 META-INF/services/AutowiredObjectSerializer.class实现类 */
            final ClassLoader classLoader = JSON.class.getClassLoader();
            if (classLoader != Thread.currentThread().getContextClassLoader()) {
                try {
                    for (Object o : ServiceLoader.load(AutowiredObjectSerializer.class, classLoader)) {

                        if (!(o instanceof AutowiredObjectSerializer)) {
                            continue;
                        }

                        AutowiredObjectSerializer autowired = (AutowiredObjectSerializer) o;
                        for (Type forType : autowired.getAutowiredFor()) {
                            /** 如果存在，注册到内部serializers缓存中 */
                            put(forType, autowired);
                        }
                    }
                } catch (ClassCastException ex) {
                    // skip
                }

                /** 尝试在已注册缓存找到特定class的序列化实例 */
                writer = serializers.get(clazz);
            }
        }

        if (writer == null) {
            String className = clazz.getName();
            Class<?> superClass;

            if (Map.class.isAssignableFrom(clazz)) {
                /** 如果class实现类Map接口，使用MapSerializer序列化 */
                put(clazz, writer = MapSerializer.instance);
            } else if (List.class.isAssignableFrom(clazz)) {
                /** 如果class实现类List接口，使用ListSerializer序列化 */
                put(clazz, writer = ListSerializer.instance);
            } else if (Collection.class.isAssignableFrom(clazz)) {
                /** 如果class实现类Collection接口，使用CollectionCodec序列化 */
                put(clazz, writer = CollectionCodec.instance);
            } else if (Date.class.isAssignableFrom(clazz)) {
                /** 如果class继承Date，使用DateCodec序列化 */
                put(clazz, writer = DateCodec.instance);
            } else if (JSONAware.class.isAssignableFrom(clazz)) {
                /** 如果class实现类JSONAware接口，使用JSONAwareSerializer序列化 */
                put(clazz, writer = JSONAwareSerializer.instance);
            } else if (JSONSerializable.class.isAssignableFrom(clazz)) {
                /** 如果class实现类JSONSerializable接口，使用JSONSerializableSerializer序列化 */
                put(clazz, writer = JSONSerializableSerializer.instance);
            } else if (JSONStreamAware.class.isAssignableFrom(clazz)) {
                /** 如果class实现类JSONStreamAware接口，使用MiscCodecr序列化 */
                put(clazz, writer = MiscCodec.instance);
            } else if (clazz.isEnum()) {
                JSONType jsonType = TypeUtils.getAnnotation(clazz, JSONType.class);
                if (jsonType != null && jsonType.serializeEnumAsJavaBean()) {
                    /** 如果是枚举类型，并且启用特性 serializeEnumAsJavaBean
                     *  使用JavaBeanSerializer序列化(假设没有启用asm)
                     */
                    put(clazz, writer = createJavaBeanSerializer(clazz));
                } else {
                    /** 如果是枚举类型，没有启用特性 serializeEnumAsJavaBean
                     *  使用EnumSerializer序列化
                     */
                    put(clazz, writer = EnumSerializer.instance);
                }
            } else if ((superClass = clazz.getSuperclass()) != null && superClass.isEnum()) {
                JSONType jsonType = TypeUtils.getAnnotation(superClass, JSONType.class);
                if (jsonType != null && jsonType.serializeEnumAsJavaBean()) {
                    /** 如果父类是枚举类型，并且启用特性 serializeEnumAsJavaBean
                     *  使用JavaBeanSerializer序列化(假设没有启用asm)
                     */
                    put(clazz, writer = createJavaBeanSerializer(clazz));
                } else {
                    /** 如果父类是枚举类型，没有启用特性 serializeEnumAsJavaBean
                     *  使用EnumSerializer序列化
                     */
                    put(clazz, writer = EnumSerializer.instance);
                }
            } else if (clazz.isArray()) {
                Class<?> componentType = clazz.getComponentType();
                /** 如果是数组类型，根据数组实际类型查找序列化实例 */
                ObjectSerializer compObjectSerializer = getObjectWriter(componentType);
                put(clazz, writer = new ArraySerializer(componentType, compObjectSerializer));
            } else if (Throwable.class.isAssignableFrom(clazz)) {
                /** 注册通用JavaBeanSerializer序列化处理 Throwable */
                SerializeBeanInfo beanInfo = TypeUtils.buildBeanInfo(clazz, null, propertyNamingStrategy);
                beanInfo.features |= SerializerFeature.WriteClassName.mask;
                put(clazz, writer = new JavaBeanSerializer(beanInfo));
            } else if (TimeZone.class.isAssignableFrom(clazz) || Map.Entry.class.isAssignableFrom(clazz)) {
                /** 如果class实现Map.Entry接口或者继承类TimeZone，使用MiscCodecr序列化 */
                put(clazz, writer = MiscCodec.instance);
            } else if (Appendable.class.isAssignableFrom(clazz)) {
                /** 如果class实现Appendable接口，使用AppendableSerializer序列化 */
                put(clazz, writer = AppendableSerializer.instance);
            } else if (Charset.class.isAssignableFrom(clazz)) {
                /** 如果class继承Charset抽象类，使用ToStringSerializer序列化 */
                put(clazz, writer = ToStringSerializer.instance);
            } else if (Enumeration.class.isAssignableFrom(clazz)) {
                /** 如果class实现Enumeration接口，使用EnumerationSerializer序列化 */
                put(clazz, writer = EnumerationSerializer.instance);
            } else if (Calendar.class.isAssignableFrom(clazz)
                    || XMLGregorianCalendar.class.isAssignableFrom(clazz)) {
                /** 如果class继承类Calendar或者XMLGregorianCalendar，使用CalendarCodec序列化 */
                put(clazz, writer = CalendarCodec.instance);
            } else if (Clob.class.isAssignableFrom(clazz)) {
                /** 如果class实现Clob接口，使用ClobSeriliazer序列化 */
                put(clazz, writer = ClobSeriliazer.instance);
            } else if (TypeUtils.isPath(clazz)) {
                /** 如果class实现java.nio.file.Path接口，使用ToStringSerializer序列化 */
                put(clazz, writer = ToStringSerializer.instance);
            } else if (Iterator.class.isAssignableFrom(clazz)) {
                /** 如果class实现Iterator接口，使用MiscCodec序列化 */
                put(clazz, writer = MiscCodec.instance);
            } else {
                /**
                 *  如果class的name是"java.awt."开头 并且
                 *  继承 Point、Rectangle、Font或者Color 其中之一
                 */
                if (className.startsWith("java.awt.")
                    && AwtCodec.support(clazz)
                ) {
                    // awt
                    if (!awtError) {
                        try {
                            String[] names = new String[]{
                                    "java.awt.Color",
                                    "java.awt.Font",
                                    "java.awt.Point",
                                    "java.awt.Rectangle"
                            };
                            for (String name : names) {
                                if (name.equals(className)) {
                                    /** 如果系统支持4中类型， 使用AwtCodec 序列化 */
                                    put(Class.forName(name), writer = AwtCodec.instance);
                                    return writer;
                                }
                            }
                        } catch (Throwable e) {
                            awtError = true;
                            // skip
                        }
                    }
                }

                // jdk8
                if ((!jdk8Error) //
                    && (className.startsWith("java.time.") //
                        || className.startsWith("java.util.Optional") //
                        || className.equals("java.util.concurrent.atomic.LongAdder")
                        || className.equals("java.util.concurrent.atomic.DoubleAdder")
                    )) {
                    try {
                        {
                            String[] names = new String[]{
                                    "java.time.LocalDateTime",
                                    "java.time.LocalDate",
                                    "java.time.LocalTime",
                                    "java.time.ZonedDateTime",
                                    "java.time.OffsetDateTime",
                                    "java.time.OffsetTime",
                                    "java.time.ZoneOffset",
                                    "java.time.ZoneRegion",
                                    "java.time.Period",
                                    "java.time.Duration",
                                    "java.time.Instant"
                            };
                            for (String name : names) {
                                if (name.equals(className)) {
                                    /** 如果系统支持JDK8中日期类型， 使用Jdk8DateCodec 序列化 */
                                    put(Class.forName(name), writer = Jdk8DateCodec.instance);
                                    return writer;
                                }
                            }
                        }
                        {
                            String[] names = new String[]{
                                    "java.util.Optional",
                                    "java.util.OptionalDouble",
                                    "java.util.OptionalInt",
                                    "java.util.OptionalLong"
                            };
                            for (String name : names) {
                                if (name.equals(className)) {
                                    /** 如果系统支持JDK8中可选类型， 使用OptionalCodec 序列化 */
                                    put(Class.forName(name), writer = OptionalCodec.instance);
                                    return writer;
                                }
                            }
                        }
                        {
                            String[] names = new String[]{
                                    "java.util.concurrent.atomic.LongAdder",
                                    "java.util.concurrent.atomic.DoubleAdder"
                            };
                            for (String name : names) {
                                if (name.equals(className)) {
                                    /** 如果系统支持JDK8中原子类型， 使用AdderSerializer 序列化 */
                                    put(Class.forName(name), writer = AdderSerializer.instance);
                                    return writer;
                                }
                            }
                        }
                    } catch (Throwable e) {
                        // skip
                        jdk8Error = true;
                    }
                }

                if ((!oracleJdbcError) //
                    && className.startsWith("oracle.sql.")) {
                    try {
                        String[] names = new String[] {
                                "oracle.sql.DATE",
                                "oracle.sql.TIMESTAMP"
                        };

                        for (String name : names) {
                            if (name.equals(className)) {
                                /** 如果系统支持oralcle驱动中日期类型， 使用DateCodec 序列化 */
                                put(Class.forName(name), writer = DateCodec.instance);
                                return writer;
                            }
                        }
                    } catch (Throwable e) {
                        // skip
                        oracleJdbcError = true;
                    }
                }

                if ((!springfoxError) //
                    && className.equals("springfox.documentation.spring.web.json.Json")) {
                    try {
                        /** 如果系统支持springfox-spring-web框架中Json类型， 使用SwaggerJsonSerializer 序列化 */
                        put(Class.forName("springfox.documentation.spring.web.json.Json"),
                                writer = SwaggerJsonSerializer.instance);
                        return writer;
                    } catch (ClassNotFoundException e) {
                        // skip
                        springfoxError = true;
                    }
                }

                if ((!guavaError) //
                        && className.startsWith("com.google.common.collect.")) {
                    try {
                        String[] names = new String[] {
                                "com.google.common.collect.HashMultimap",
                                "com.google.common.collect.LinkedListMultimap",
                                "com.google.common.collect.ArrayListMultimap",
                                "com.google.common.collect.TreeMultimap"
                        };

                        for (String name : names) {
                            if (name.equals(className)) {
                                /** 如果系统支持guava框架中日期类型， 使用GuavaCodec 序列化 */
                                put(Class.forName(name), writer = GuavaCodec.instance);
                                return writer;
                            }
                        }
                    } catch (ClassNotFoundException e) {
                        // skip
                        guavaError = true;
                    }
                }

                if ((!jsonnullError) && className.equals("net.sf.json.JSONNull")) {
                    try {
                        /** 如果系统支持json-lib框架中JSONNull类型， 使用MiscCodec 序列化 */
                        put(Class.forName("net.sf.json.JSONNull"), writer = MiscCodec.instance);
                        return writer;
                    } catch (ClassNotFoundException e) {
                        // skip
                        jsonnullError = true;
                    }
                }

                Class[] interfaces = clazz.getInterfaces();
                /** 如果class只实现唯一接口，并且接口包含注解，使用AnnotationSerializer 序列化 */
                if (interfaces.length == 1 && interfaces[0].isAnnotation()) {
                    return AnnotationSerializer.instance;
                }

                /** 如果使用了cglib或者javassist动态代理 */
                if (TypeUtils.isProxy(clazz)) {
                    Class<?> superClazz = clazz.getSuperclass();

                    /** 通过父类型查找序列化，父类是真实的类型 */
                    ObjectSerializer superWriter = getObjectWriter(superClazz);
                    put(clazz, superWriter);
                    return superWriter;
                }

                /** 如果使用了jdk动态代理 */
                if (Proxy.isProxyClass(clazz)) {
                    Class handlerClass = null;

                    if (interfaces.length == 2) {
                        handlerClass = interfaces[1];
                    } else {
                        for (Class proxiedInterface : interfaces) {
                            if (proxiedInterface.getName().startsWith("org.springframework.aop.")) {
                                continue;
                            }
                            if (handlerClass != null) {
                                handlerClass = null; // multi-matched
                                break;
                            }
                            handlerClass = proxiedInterface;
                        }
                    }

                    if (handlerClass != null) {
                        /** 根据class实现接口类型查找序列化 */
                        ObjectSerializer superWriter = getObjectWriter(handlerClass);
                        put(clazz, superWriter);
                        return superWriter;
                    }
                }

                if (create) {
                    /** 没有精确匹配，使用通用JavaBeanSerializer 序列化(假设不启用asm) */
                    writer = createJavaBeanSerializer(clazz);
                    put(clazz, writer);
                }
            }

            if (writer == null) {
                /** 尝试在已注册缓存找到特定class的序列化实例 */
                writer = serializers.get(clazz);
            }
        }
        return writer;
    }
```

查找具体序列化实例，查找方法基本思想根据class类型或者实现接口类型进行匹配查找。接下来针对逐个序列化实现依次分析。

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

### MapSerializer序列化

按照代码的顺序第一个分析到Map序列化器，内部调用write：

```java
    public void write(JSONSerializer serializer
            , Object object
            , Object fieldName
            , Type fieldType
            , int features) throws IOException {
        write(serializer, object, fieldName, fieldType, features, false);
    }
```

进入`MapSerializer#write(com.alibaba.fastjson.serializer.JSONSerializer, java.lang.Object, java.lang.Object, java.lang.reflect.Type, int, boolean)`方法:

```java
    public void write(JSONSerializer serializer
            , Object object
            , Object fieldName
            , Type fieldType
            , int features 
            , boolean unwrapped) throws IOException {
        SerializeWriter out = serializer.out;

        if (object == null) {
            /** 如果map是null, 输出 "null" 字符串 */
            out.writeNull();
            return;
        }

        Map<?, ?> map = (Map<?, ?>) object;
        final int mapSortFieldMask = SerializerFeature.MapSortField.mask;
        if ((out.features & mapSortFieldMask) != 0 || (features & mapSortFieldMask) != 0) {
            /** JSONObject包装HashMap或者LinkedHashMap */
            if (map instanceof JSONObject) {
                map = ((JSONObject) map).getInnerMap();
            }

            if ((!(map instanceof SortedMap)) && !(map instanceof LinkedHashMap)) {
                try {
                    map = new TreeMap(map);
                } catch (Exception ex) {
                    // skip
                }
            }
        }

        if (serializer.containsReference(object)) {
            /** 处理对象引用，下文详细分析 */
            serializer.writeReference(object);
            return;
        }

        SerialContext parent = serializer.context;
        /** 创建当前新的序列化context */
        serializer.setContext(parent, object, fieldName, 0);
        try {
            if (!unwrapped) {
                out.write('{');
            }

            serializer.incrementIndent();

            Class<?> preClazz = null;
            ObjectSerializer preWriter = null;

            boolean first = true;

            if (out.isEnabled(SerializerFeature.WriteClassName)) {
                String typeKey = serializer.config.typeKey;
                Class<?> mapClass = map.getClass();
                boolean containsKey = (mapClass == JSONObject.class || mapClass == HashMap.class || mapClass == LinkedHashMap.class) 
                        && map.containsKey(typeKey);
                /** 序列化的map不包含key=@type或者自定义值，则输出map的类名 */
                if (!containsKey) {
                    out.writeFieldName(typeKey);
                    out.writeString(object.getClass().getName());
                    first = false;
                }
            }

            for (Map.Entry entry : map.entrySet()) {
                Object value = entry.getValue();

                Object entryKey = entry.getKey();

                {
                    /** 遍历JSONSerializer的PropertyPreFilter拦截器，拦截key是否输出 */
                    List<PropertyPreFilter> preFilters = serializer.propertyPreFilters;
                    if (preFilters != null && preFilters.size() > 0) {
                        if (entryKey == null || entryKey instanceof String) {
                            if (!this.applyName(serializer, object, (String) entryKey)) {
                                continue;
                            }
                        } else if (entryKey.getClass().isPrimitive() || entryKey instanceof Number) {
                            String strKey = JSON.toJSONString(entryKey);
                            if (!this.applyName(serializer, object, strKey)) {
                                continue;
                            }
                        }
                    }
                }
                {
                    /** 遍历PropertyPreFilter拦截器，拦截key是否输出 */
                    List<PropertyPreFilter> preFilters = this.propertyPreFilters;
                    if (preFilters != null && preFilters.size() > 0) {
                        if (entryKey == null || entryKey instanceof String) {
                            if (!this.applyName(serializer, object, (String) entryKey)) {
                                continue;
                            }
                        } else if (entryKey.getClass().isPrimitive() || entryKey instanceof Number) {
                            String strKey = JSON.toJSONString(entryKey);
                            if (!this.applyName(serializer, object, strKey)) {
                                continue;
                            }
                        }
                    }
                }

                {
                    /** 遍历JSONSerializer的PropertyFilter拦截器，拦截key是否输出 */
                    List<PropertyFilter> propertyFilters = serializer.propertyFilters;
                    if (propertyFilters != null && propertyFilters.size() > 0) {
                        if (entryKey == null || entryKey instanceof String) {
                            if (!this.apply(serializer, object, (String) entryKey, value)) {
                                continue;
                            }
                        } else if (entryKey.getClass().isPrimitive() || entryKey instanceof Number) {
                            String strKey = JSON.toJSONString(entryKey);
                            if (!this.apply(serializer, object, strKey, value)) {
                                continue;
                            }
                        }
                    }
                }
                {
                    /** 遍历PropertyFilter拦截器，拦截key是否输出 */
                    List<PropertyFilter> propertyFilters = this.propertyFilters;
                    if (propertyFilters != null && propertyFilters.size() > 0) {
                        if (entryKey == null || entryKey instanceof String) {
                            if (!this.apply(serializer, object, (String) entryKey, value)) {
                                continue;
                            }
                        } else if (entryKey.getClass().isPrimitive() || entryKey instanceof Number) {
                            String strKey = JSON.toJSONString(entryKey);
                            if (!this.apply(serializer, object, strKey, value)) {
                                continue;
                            }
                        }
                    }
                }

                {
                    /** 遍历JSONSerializer的NameFilter拦截器，适用于key字符别名串转换 */
                    List<NameFilter> nameFilters = serializer.nameFilters;
                    if (nameFilters != null && nameFilters.size() > 0) {
                        if (entryKey == null || entryKey instanceof String) {
                            entryKey = this.processKey(serializer, object, (String) entryKey, value);
                        } else if (entryKey.getClass().isPrimitive() || entryKey instanceof Number) {
                            String strKey = JSON.toJSONString(entryKey);
                            entryKey = this.processKey(serializer, object, strKey, value);
                        }
                    }
                }
                {
                    /** 遍历NameFilter拦截器，适用于key字符串别名转换 */
                    List<NameFilter> nameFilters = this.nameFilters;
                    if (nameFilters != null && nameFilters.size() > 0) {
                        if (entryKey == null || entryKey instanceof String) {
                            entryKey = this.processKey(serializer, object, (String) entryKey, value);
                        } else if (entryKey.getClass().isPrimitive() || entryKey instanceof Number) {
                            String strKey = JSON.toJSONString(entryKey);
                            entryKey = this.processKey(serializer, object, strKey, value);
                        }
                    }
                }

                {
                    /** 处理map序列化value拦截器, ValueFilter 和 ContextValueFilter */
                    if (entryKey == null || entryKey instanceof String) {
                        value = this.processValue(serializer, null, object, (String) entryKey, value);
                    } else {
                        boolean objectOrArray = entryKey instanceof Map || entryKey instanceof Collection;
                        if (!objectOrArray) {
                            String strKey = JSON.toJSONString(entryKey);
                            value = this.processValue(serializer, null, object, strKey, value);
                        }
                    }
                }

                if (value == null) {
                    /** 如果开启map为Null，不输出 */
                    if (!out.isEnabled(SerializerFeature.WRITE_MAP_NULL_FEATURES)) {
                        continue;
                    }
                }

                if (entryKey instanceof String) {
                    String key = (String) entryKey;

                    /** 如果不是第一个属性字段增加分隔符 */
                    if (!first) {
                        out.write(',');
                    }

                    if (out.isEnabled(SerializerFeature.PrettyFormat)) {
                        serializer.println();
                    }
                    /** 输出key */
                    out.writeFieldName(key, true);
                } else {
                    if (!first) {
                        out.write(',');
                    }

                    /** 开启WriteNonStringKeyAsString, 将key做一次json串转换 */
                    if (out.isEnabled(NON_STRINGKEY_AS_STRING) && !(entryKey instanceof Enum)) {
                        String strEntryKey = JSON.toJSONString(entryKey);
                        serializer.write(strEntryKey);
                    } else {
                        serializer.write(entryKey);
                    }

                    out.write(':');
                }

                first = false;

                if (value == null) {
                    /** 如果value为空，输出空值 */
                    out.writeNull();
                    continue;
                }

                Class<?> clazz = value.getClass();

                if (clazz != preClazz) {
                    preClazz = clazz;
                    preWriter = serializer.getObjectWriter(clazz);
                }

                if (SerializerFeature.isEnabled(features, SerializerFeature.WriteClassName)
                        && preWriter instanceof JavaBeanSerializer) {
                    Type valueType = null;
                    if (fieldType instanceof ParameterizedType) {
                        ParameterizedType parameterizedType = (ParameterizedType) fieldType;
                        Type[] actualTypeArguments = parameterizedType.getActualTypeArguments();
                        if (actualTypeArguments.length == 2) {
                            valueType = actualTypeArguments[1];
                        }
                    }

                    /** 特殊处理泛型，这里假定泛型第二参数作为值的真实类型 */
                    JavaBeanSerializer javaBeanSerializer = (JavaBeanSerializer) preWriter;
                    javaBeanSerializer.writeNoneASM(serializer, value, entryKey, valueType, features);
                } else {
                    /** 根据value类型的序列化器 序列化value */
                    preWriter.write(serializer, value, entryKey, null, features);
                }
            }
        } finally {
            serializer.context = parent;
        }

        serializer.decrementIdent();
        if (out.isEnabled(SerializerFeature.PrettyFormat) && map.size() > 0) {
            serializer.println();
        }

        if (!unwrapped) {
            out.write('}');
        }
    }
```

map序列化实现方法主要做了以下几件事情：

1. 处理对象引用，使用jdk的IdentityHashMap类严格判断对象严格相等。
2. 针对map的key和value执行拦截器操作。
3. 针对value的类型，查找value的class类型序列化输出。

序列化map处理引用的逻辑在 `com.alibaba.fastjson.serializer.JSONSerializer#writeReference` :

```java
    public void writeReference(Object object) {
        SerialContext context = this.context;
        Object current = context.object;

        /** 如果输出引用就是自己this, ref值为 @ */
        if (object == current) {
            out.write("{\"$ref\":\"@\"}");
            return;
        }

        SerialContext parentContext = context.parent;

        /** 如果输出引用就是父引用, ref值为 .. */
        if (parentContext != null) {
            if (object == parentContext.object) {
                out.write("{\"$ref\":\"..\"}");
                return;
            }
        }

        SerialContext rootContext = context;
        /** 查找最顶层序列化context */
        for (;;) {
            if (rootContext.parent == null) {
                break;
            }
            rootContext = rootContext.parent;
        }

        if (object == rootContext.object) {
            /** 如果最顶层引用就是自己this, ref值为 $*/
            out.write("{\"$ref\":\"$\"}");
        } else {
            /** 常规java对象引用，直接输出 */
            out.write("{\"$ref\":\"");
            out.write(references.get(object).toString());
            out.write("\"}");
        }
    }
```

### ListSerializer序列化

``` java
    public final void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features)
                                                                                                       throws IOException {

        boolean writeClassName = serializer.out.isEnabled(SerializerFeature.WriteClassName)
                || SerializerFeature.isEnabled(features, SerializerFeature.WriteClassName);

        SerializeWriter out = serializer.out;

        Type elementType = null;
        if (writeClassName) {
            /** 获取泛型字段真实类型 */
            elementType = TypeUtils.getCollectionItemType(fieldType);
        }

        if (object == null) {
            /** 如果集合对象为空并且开启WriteNullListAsEmpty特性, 输出[] */
            out.writeNull(SerializerFeature.WriteNullListAsEmpty);
            return;
        }

        List<?> list = (List<?>) object;

        if (list.size() == 0) {
            /** 如果集合对象元素为0, 输出[] */
            out.append("[]");
            return;
        }

        /** 创建当前新的序列化context */
        SerialContext context = serializer.context;
        serializer.setContext(context, object, fieldName, 0);

        ObjectSerializer itemSerializer = null;
        try {
            /** 判断是否开启json格式化 */
            if (out.isEnabled(SerializerFeature.PrettyFormat)) {
                out.append('[');
                serializer.incrementIndent();

                int i = 0;
                for (Object item : list) {
                    if (i != 0) {
                        out.append(',');
                    }

                    serializer.println();
                    if (item != null) {
                        /** 如果存在引用，输出元素引用信息 */
                        if (serializer.containsReference(item)) {
                            serializer.writeReference(item);
                        } else {
                            /** 通过元素包含的类型查找序列化实例 */
                            itemSerializer = serializer.getObjectWriter(item.getClass());
                            SerialContext itemContext = new SerialContext(context, object, fieldName, 0, 0);
                            serializer.context = itemContext;
                            /** 根据具体序列化实例输出 */
                            itemSerializer.write(serializer, item, i, elementType, features);
                        }
                    } else {
                        serializer.out.writeNull();
                    }
                    i++;
                }

                serializer.decrementIdent();
                serializer.println();
                out.append(']');
                return;
            }

            out.append('[');
            for (int i = 0, size = list.size(); i < size; ++i) {
                Object item = list.get(i);
                if (i != 0) {
                    out.append(',');
                }
                
                if (item == null) {
                    out.append("null");
                } else {
                    Class<?> clazz = item.getClass();

                    if (clazz == Integer.class) {
                        /** 元素类型如果是整数，直接输出 */
                        out.writeInt(((Integer) item).intValue());
                    } else if (clazz == Long.class) {
                        /** 元素类型如果是长整数，直接输出并判断是否追加类型L */
                        long val = ((Long) item).longValue();
                        if (writeClassName) {
                            out.writeLong(val);
                            out.write('L');
                        } else {
                            out.writeLong(val);
                        }
                    } else {
                        if ((SerializerFeature.DisableCircularReferenceDetect.mask & features) != 0){
                            /** 如果禁用循环引用检查，根据元素类型查找序列化实例输出 */
                            itemSerializer = serializer.getObjectWriter(item.getClass());
                            itemSerializer.write(serializer, item, i, elementType, features);
                        }else {
                            if (!out.disableCircularReferenceDetect) {
                                /** 如果没有禁用循环引用检查，创建新的序列化上下文 */
                                SerialContext itemContext = new SerialContext(context, object, fieldName, 0, 0);
                                serializer.context = itemContext;
                            }

                            if (serializer.containsReference(item)) {
                                /** 处理对象引用 */
                                serializer.writeReference(item);
                            } else {
                                /** 根据集合类型查找序列化实例处理，JavaBeanSerializer后面单独分析 */
                                itemSerializer = serializer.getObjectWriter(item.getClass());
                                if ((SerializerFeature.WriteClassName.mask & features) != 0
                                        && itemSerializer instanceof JavaBeanSerializer)
                                {
                                    JavaBeanSerializer javaBeanSerializer = (JavaBeanSerializer) itemSerializer;
                                    javaBeanSerializer.writeNoneASM(serializer, item, i, elementType, features);
                                } else {
                                    itemSerializer.write(serializer, item, i, elementType, features);
                                }
                            }
                        }
                    }
                }
            }
            out.append(']');
        } finally {
            serializer.context = context;
        }
    }
```

`ListSerializer`序列化主要判断是否需要格式化json输出，对整型和长整型进行特殊取值，如果是对象类型根据class类别查找序列化实例处理，和hessian2源码实现原理类似。