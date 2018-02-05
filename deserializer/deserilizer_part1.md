## 概要

fastjson核心功能包括序列化和反序列化，反序列化的含义是将跨语言的json字符串转换成java对象。遇到到反序列化章节，这里假定你已经阅读并理解了词法分析章节的内容。

反序列化的章节比序列化复杂一些，我认为通过调试小单元代码片段的方式有助于理解，我在适当的地方会给出单元测试入口，集中精力理解具体类型的实现。

现在，我们正式开始理解反序列化实现吧。

``` java
    public static <T> T parseObject(String text, Class<T> clazz) {
        /** 根据指定text，返回期望的java对象类型class */
        return parseObject(text, clazz, new Feature[0]);
    }
```

这个反序列化接口可以处理对象包含任意字段类型，但是自身不能是泛型类型，原因是java的运行时类型擦除。`fastjson`给出了替代方法解决：

``` java
   String json = "[{},...]";
   Type listType = new TypeReference<List<Model>>() {}.getType();
   List<Model> modelList = JSON.parseObject(json, listType);
```

我们把关注点收回来，继续分析内部调用`parseObject` :

``` java
    public static <T> T parseObject(String json, Class<T> clazz, Feature... features) {
        return (T) parseObject(json, (Type) clazz, ParserConfig.global, null, DEFAULT_PARSER_FEATURE, features);
    }

        public static <T> T parseObject(String input, Type clazz, ParserConfig config, ParseProcess processor,
                                          int featureValues, Feature... features) {
        if (input == null) {
            return null;
        }

        /** 配置反序列化时启用的特性，比如是否允许json字符串字段不包含双引号 */
        if (features != null) {
            for (Feature feature : features) {
                featureValues |= feature.mask;
            }
        }

        /**
         *  初始化DefaultJSONParser，反序列化类型由它
         *  委托config查找具体序列化处理器处理
         */
        DefaultJSONParser parser = new DefaultJSONParser(input, config, featureValues);

        /** 添加拦截器 */
        if (processor != null) {
            if (processor instanceof ExtraTypeProvider) {
                parser.getExtraTypeProviders().add((ExtraTypeProvider) processor);
            }

            if (processor instanceof ExtraProcessor) {
                parser.getExtraProcessors().add((ExtraProcessor) processor);
            }

            if (processor instanceof FieldTypeResolver) {
                parser.setFieldTypeResolver((FieldTypeResolver) processor);
            }
        }

        /** 使用反序列化实例转换对象，查找具体序列化实例委托给config查找 */
        T value = (T) parser.parseObject(clazz, null);

        /** 处理json内部引用协议格式对象 */
        parser.handleResovleTask(value);

        parser.close();

        return (T) value;
    }
```

最终反序列化接口定义了执行的大框架：

1. 创建解析配置`ParserConfig`对象，包括初始化内部反序列化实例和特性配置等。
2. 添加反序列化拦截器
3. 根据具体类型查找反序列化实例，执行反序列化转换
4. 解析对象内部引用

我们继续查看`parser.parseObject(clazz, null)`逻辑：

``` java
    public <T> T parseObject(Type type, Object fieldName) {
        /** 获取json串第一个有效token */
        int token = lexer.token();
        if (token == JSONToken.NULL) {
            /** 如果返回时null，自动预读下一个token */
            lexer.nextToken();
            return null;
        }

        /** 判定token属于字符串 */
        if (token == JSONToken.LITERAL_STRING) {
            /** 获取byte字节数据，分为十六进制和base64编码 */
            if (type == byte[].class) {
                byte[] bytes = lexer.bytesValue();
                lexer.nextToken();
                return (T) bytes;
            }

            /** 获取字符数组, 特殊处理String内存占用 */
            if (type == char[].class) {
                String strVal = lexer.stringVal();
                lexer.nextToken();
                return (T) strVal.toCharArray();
            }
        }

        /** 委托config进行特定类型查找反序列化实例 */
        ObjectDeserializer derializer = config.getDeserializer(type);

        try {
            /** 执行反序列化 */
            return (T) derializer.deserialze(this, type, fieldName);
        } catch (JSONException e) {
            throw e;
        } catch (Throwable e) {
            throw new JSONException(e.getMessage(), e);
        }
    }
```

反序列化核心逻辑还是在委托配置查找反序列化实例，我们具体看看是如何查找反序列化实例的， 进入`ParserConfig#getDeserializer(java.lang.reflect.Type)`自己查看逻辑：

``` java
    public ObjectDeserializer getDeserializer(Type type) {
        /** 首先从内部已经注册查找特定class的反序列化实例 */
        ObjectDeserializer derializer = this.deserializers.get(type);
        if (derializer != null) {
            return derializer;
        }

        if (type instanceof Class<?>) {
            /** 引用类型，根据特定类型再次匹配 */
            return getDeserializer((Class<?>) type, type);
        }

        if (type instanceof ParameterizedType) {
            /** 获取泛型类型原始类型 */
            Type rawType = ((ParameterizedType) type).getRawType();
            /** 泛型原始类型是引用类型，根据特定类型再次匹配 */
            if (rawType instanceof Class<?>) {
                return getDeserializer((Class<?>) rawType, type);
            } else {
                /** 递归调用反序列化查找 */
                return getDeserializer(rawType);
            }
        }

        if (type instanceof WildcardType) {
            /** 类型是通配符或者限定类型 */
            WildcardType wildcardType = (WildcardType) type;
            Type[] upperBounds = wildcardType.getUpperBounds();
            if (upperBounds.length == 1) {
                Type upperBoundType = upperBounds[0];
                /** 获取泛型上界(? extends T)，根据特定类型再次匹配 */
                return getDeserializer(upperBoundType);
            }
        }

        /** 如果无法匹配到，使用默认JavaObjectDeserializer反序列化 */
        return JavaObjectDeserializer.instance;
    }
```

反序列化匹配`getDeserializer(Type)`主要特定处理了泛型类型，取出泛型类型真实类型还是委托内部`ParserConfig#getDeserializer(java.lang.Class<?>, java.lang.reflect.Type)`进行精确类型查找：

``` java
    public ObjectDeserializer getDeserializer(Class<?> clazz, Type type) {
        /** 首先从内部已经注册查找特定type的反序列化实例 */
        ObjectDeserializer derializer = deserializers.get(type);
        if (derializer != null) {
            return derializer;
        }

        if (type == null) {
            type = clazz;
        }

        /** 再次从内部已经注册查找特定class的反序列化实例 */
        derializer = deserializers.get(type);
        if (derializer != null) {
            return derializer;
        }

        {
            JSONType annotation = TypeUtils.getAnnotation(clazz,JSONType.class);
            if (annotation != null) {
                Class<?> mappingTo = annotation.mappingTo();
                /** 根据类型注解指定的反序列化类型 */
                if (mappingTo != Void.class) {
                    return getDeserializer(mappingTo, mappingTo);
                }
            }
        }

        if (type instanceof WildcardType || type instanceof TypeVariable || type instanceof ParameterizedType) {
            /** 根据泛型真实类型查找反序列化实例 */
            derializer = deserializers.get(clazz);
        }

        if (derializer != null) {
            return derializer;
        }

        /** 获取class名称，进行类型匹配(可以支持高版本jdk和三方库) */
        String className = clazz.getName();
        className = className.replace('$', '.');

        if (className.startsWith("java.awt.")
            && AwtCodec.support(clazz)) {
            /**
             *  如果class的name是"java.awt."开头 并且
             *  继承 Point、Rectangle、Font或者Color 其中之一
             */
            if (!awtError) {
                String[] names = new String[] {
                        "java.awt.Point",
                        "java.awt.Font",
                        "java.awt.Rectangle",
                        "java.awt.Color"
                };

                try {
                    for (String name : names) {
                        if (name.equals(className)) {
                            /** 如果系统支持4中类型， 使用AwtCodec 反序列化 */
                            deserializers.put(Class.forName(name), derializer = AwtCodec.instance);
                            return derializer;
                        }
                    }
                } catch (Throwable e) {
                    // skip
                    awtError = true;
                }

                derializer = AwtCodec.instance;
            }
        }

        if (!jdk8Error) {
            try {
                if (className.startsWith("java.time.")) {
                    String[] names = new String[] {
                            "java.time.LocalDateTime",
                            "java.time.LocalDate",
                            "java.time.LocalTime",
                            "java.time.ZonedDateTime",
                            "java.time.OffsetDateTime",
                            "java.time.OffsetTime",
                            "java.time.ZoneOffset",
                            "java.time.ZoneRegion",
                            "java.time.ZoneId",
                            "java.time.Period",
                            "java.time.Duration",
                            "java.time.Instant"
                    };

                    for (String name : names) {
                        if (name.equals(className)) {
                            /** 如果系统支持JDK8中日期类型， 使用Jdk8DateCodec 反序列化 */
                            deserializers.put(Class.forName(name), derializer = Jdk8DateCodec.instance);
                            return derializer;
                        }
                    }
                } else if (className.startsWith("java.util.Optional")) {
                    String[] names = new String[] {
                            "java.util.Optional",
                            "java.util.OptionalDouble",
                            "java.util.OptionalInt",
                            "java.util.OptionalLong"
                    };
                    for (String name : names) {
                        if (name.equals(className)) {
                            /** 如果系统支持JDK8中可选类型， 使用OptionalCodec 反序列化 */
                            deserializers.put(Class.forName(name), derializer = OptionalCodec.instance);
                            return derializer;
                        }
                    }
                }
            } catch (Throwable e) {
                // skip
                jdk8Error = true;
            }
        }

        if (className.equals("java.nio.file.Path")) {
            deserializers.put(clazz, derializer = MiscCodec.instance);
        }

        if (clazz == Map.Entry.class) {
            deserializers.put(clazz, derializer = MiscCodec.instance);
        }

        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            /** 使用当前线程类加载器 查找 META-INF/services/AutowiredObjectDeserializer.class实现类 */
            for (AutowiredObjectDeserializer autowired : ServiceLoader.load(AutowiredObjectDeserializer.class,
                                                                            classLoader)) {
                for (Type forType : autowired.getAutowiredFor()) {
                    deserializers.put(forType, autowired);
                }
            }
        } catch (Exception ex) {
            // skip
        }

        if (derializer == null) {
            derializer = deserializers.get(type);
        }

        if (derializer != null) {
            return derializer;
        }

        if (clazz.isEnum()) {
            Class<?> deserClass = null;
            JSONType jsonType = clazz.getAnnotation(JSONType.class);
            if (jsonType != null) {
                deserClass = jsonType.deserializer();
                try {
                    /** 如果是枚举类型并使用了注解，使用注解指定的反序列化 */
                    derializer = (ObjectDeserializer) deserClass.newInstance();
                    deserializers.put(clazz, derializer);
                    return derializer;
                } catch (Throwable error) {
                    // skip
                }
            }
            /** 如果是枚举类型，使用EnumSerializer反序列化 */
            derializer = new EnumDeserializer(clazz);
        } else if (clazz.isArray()) {
            /** 如果是数组类型，使用数组对象反序列化实例 */
            derializer = ObjectArrayCodec.instance;
        } else if (clazz == Set.class || clazz == HashSet.class || clazz == Collection.class || clazz == List.class
                   || clazz == ArrayList.class) {
            /** 如果class实现集合接口，使用CollectionCodec反序列化 */
            derializer = CollectionCodec.instance;
        } else if (Collection.class.isAssignableFrom(clazz)) {
            /** 如果class实现类Collection接口，使用CollectionCodec反序列化 */
            derializer = CollectionCodec.instance;
        } else if (Map.class.isAssignableFrom(clazz)) {
            /** 如果class实现Map接口，使用MapDeserializer反序列化 */
            derializer = MapDeserializer.instance;
        } else if (Throwable.class.isAssignableFrom(clazz)) {
            /** 如果class继承Throwable类，使用ThrowableDeserializer反序列化 */
            derializer = new ThrowableDeserializer(this, clazz);
        } else if (PropertyProcessable.class.isAssignableFrom(clazz)) {
            derializer = new PropertyProcessableDeserializer((Class<PropertyProcessable>)clazz);
        } else {
            /** 默认使用JavaBeanDeserializer反序列化(没有开启asm情况下) */
            derializer = createJavaBeanDeserializer(clazz, type);
        }

        /** 加入cache，避免同类型反复创建 */
        putDeserializer(type, derializer);

        return derializer;
    }
```

其实查找反序列化和之前提到了序列化类似，根据特定类型匹配接口或者继承实现类查找的，这里指的关注一下创建通用反序列化实例 `createJavaBeanDeserializer(clazz, type)` ：

``` java
    public ObjectDeserializer createJavaBeanDeserializer(Class<?> clazz, Type type) {
        boolean asmEnable = this.asmEnable & !this.fieldBased;

        /**
         *  ... 省略判定是否开启asm逻辑
         */

        /** 创建通用Java对象反序列化实例JavaBeanDeserializer */
        if (!asmEnable) {
            return new JavaBeanDeserializer(this, clazz, type);
        }

        /**
         *  ... 省略创建基于asm的反序列化对象
         */
    }
```

对于自定义类反序列化，如果没有开启`asm`的情况下，会使用`JavaBeanDeserializer`进行反序列化转换，这里有意屏蔽基于`asm`直接操纵字节码实现，后面会单独列一个章节对该主题深入讲解。

接下来会进入反序列化实现细节深入理解。