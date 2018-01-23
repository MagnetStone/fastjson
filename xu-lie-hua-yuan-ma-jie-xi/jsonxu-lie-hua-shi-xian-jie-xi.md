## 概要

fastjson序列化主要使用入口就是在`JSON.java`类中，它提供非常简便和友好的api将java对象转换成json字符串。

### JSON成员函数

``` java
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

``` java
    public static String toJSONString(Object object, SerializeFilter[] filters, SerializerFeature... features) {
        return toJSONString(object, SerializeConfig.globalInstance, filters, null, DEFAULT_GENERATE_FEATURE, features);
    }
```

继续跟踪方法调用到`toJSONString(Object, SerializeConfig ,SerializeFilter[], String, int, SerializerFeature... )` :

``` java
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

``` java
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

``` java
    void write(JSONSerializer serializer, /** json序列化实例 */
               Object object,       /** 待序列化的对象*/
               Object fieldName,    /** 待序列化字段*/
               Type fieldType,      /** 待序列化字段类型 */
               int features) throws IOException;
```

当fastjson序列化特定的字段时会回调这个方法。

我们继续跟踪`writer.write(this, object, null, null, 0)` : 

``` java
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

``` java
    public ObjectSerializer getObjectWriter(Class<?> clazz) {
        return getObjectWriter(clazz, true);
    }
```

内部又调用`com.alibaba.fastjson.serializer.SerializeConfig#getObjectWriter(java.lang.Class<?>, boolean)`，这个类实现相对复杂了一些，我会按照代码顺序梳理所有序列化实例的要点 :

``` java
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