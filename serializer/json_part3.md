## 序列化回调接口实现分析

### 特定序列化实现解析

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

### DateCodec序列化

因为日期序列化和前面已经分析的`MiscCodec`中`SimpleDateFormat`相近，在此不冗余分析，可以参考我已经添加的注释分析。

### JavaBeanSerializer序列化

因为前面已经涵盖了绝大部分`fastjson`序列化源码分析，为了节省篇幅，我准备用一个较为复杂的序列化实现`JavaBeanSerializer`作为结束这章内容。

在`SerializeConfig#getObjectWriter`中有一段逻辑`createJavaBeanSerializer`，我们针对进行细节分析 ：

``` java
    public final ObjectSerializer createJavaBeanSerializer(Class<?> clazz) {
	    /** 封装序列化clazz Bean，包含字段类型等等 */
	    SerializeBeanInfo beanInfo = TypeUtils.buildBeanInfo(clazz, null, propertyNamingStrategy, fieldBased);
	    if (beanInfo.fields.length == 0 && Iterable.class.isAssignableFrom(clazz)) {
	        /** 如果clazz是迭代器类型，使用MiscCodec序列化，会被序列化成数组 [,,,] */
	        return MiscCodec.instance;
	    }

	    return createJavaBeanSerializer(beanInfo);
	}
```

我们先进`TypeUtils.buildBeanInfo`看看内部实现：

``` java
    public static SerializeBeanInfo buildBeanInfo(Class<?> beanType //
            , Map<String,String> aliasMap //
            , PropertyNamingStrategy propertyNamingStrategy //
            , boolean fieldBased //
    ){
        JSONType jsonType = TypeUtils.getAnnotation(beanType,JSONType.class);
        String[] orders = null;
        final int features;
        String typeName = null, typeKey = null;
        if(jsonType != null){
            orders = jsonType.orders();

            typeName = jsonType.typeName();
            if(typeName.length() == 0){
                typeName = null;
            }

            PropertyNamingStrategy jsonTypeNaming = jsonType.naming();
            if (jsonTypeNaming != PropertyNamingStrategy.CamelCase) {
                propertyNamingStrategy = jsonTypeNaming;
            }

            features = SerializerFeature.of(jsonType.serialzeFeatures());
            /** 查找类型父类是否包含JSONType注解 */
            for(Class<?> supperClass = beanType.getSuperclass()
                ; supperClass != null && supperClass != Object.class
                    ; supperClass = supperClass.getSuperclass()){
                JSONType superJsonType = TypeUtils.getAnnotation(supperClass,JSONType.class);
                if(superJsonType == null){
                    break;
                }
                typeKey = superJsonType.typeKey();
                if(typeKey.length() != 0){
                    break;
                }
            }

            /** 查找类型实现的接口是否包含JSONType注解 */
            for(Class<?> interfaceClass : beanType.getInterfaces()){
                JSONType superJsonType = TypeUtils.getAnnotation(interfaceClass,JSONType.class);
                if(superJsonType != null){
                    typeKey = superJsonType.typeKey();
                    if(typeKey.length() != 0){
                        break;
                    }
                }
            }

            if(typeKey != null && typeKey.length() == 0){
                typeKey = null;
            }
        } else{
            features = 0;
        }
        /** fieldName,field ，先生成fieldName的快照，减少之后的findField的轮询 */
        Map<String,Field> fieldCacheMap = new HashMap<String,Field>();
        ParserConfig.parserAllFieldToCache(beanType, fieldCacheMap);
        List<FieldInfo> fieldInfoList = fieldBased
                ? computeGettersWithFieldBase(beanType, aliasMap, false, propertyNamingStrategy)
                : computeGetters(beanType, jsonType, aliasMap, fieldCacheMap, false, propertyNamingStrategy);
        FieldInfo[] fields = new FieldInfo[fieldInfoList.size()];
        fieldInfoList.toArray(fields);
        FieldInfo[] sortedFields;
        List<FieldInfo> sortedFieldList;
        if(orders != null && orders.length != 0){
            /** computeGettersWithFieldBase基于字段解析,
             *  computeGetters基于方法解析+字段解析
             */
            sortedFieldList = fieldBased
                    ? computeGettersWithFieldBase(beanType, aliasMap, true, propertyNamingStrategy) //
                    : computeGetters(beanType, jsonType, aliasMap, fieldCacheMap, true, propertyNamingStrategy);
        } else{
            sortedFieldList = new ArrayList<FieldInfo>(fieldInfoList);
            Collections.sort(sortedFieldList);
        }
        sortedFields = new FieldInfo[sortedFieldList.size()];
        sortedFieldList.toArray(sortedFields);
        if(Arrays.equals(sortedFields, fields)){
            sortedFields = fields;
        }
        /** 封装对象的字段信息和方法信息 */
        return new SerializeBeanInfo(beanType, jsonType, typeName, typeKey, features, fields, sortedFields);
    }
```

在解析字段的时候有一个区别，computeGettersWithFieldBase基于字段解析而computeGetters基于方法解析(get + is 开头方法)+字段解析。因为两者的解析类似，这里只给出computeGettersWithFieldBase方法解析 ：

``` java
    public static List<FieldInfo> computeGettersWithFieldBase(
            Class<?> clazz,
            Map<String,String> aliasMap,
            boolean sorted,
            PropertyNamingStrategy propertyNamingStrategy){
        Map<String,FieldInfo> fieldInfoMap = new LinkedHashMap<String,FieldInfo>();
        for(Class<?> currentClass = clazz; currentClass != null; currentClass = currentClass.getSuperclass()){
            Field[] fields = currentClass.getDeclaredFields();
            /** 遍历clazz所有字段，把字段信息封装成bean存储到fieldInfoMap中*/
            computeFields(currentClass, aliasMap, propertyNamingStrategy, fieldInfoMap, fields);
        }
        /** 主要处理字段有序的逻辑 */
        return getFieldInfos(clazz, sorted, fieldInfoMap);
    }
```

查看`computeFields`逻辑：

``` java
    private static void computeFields(
            Class<?> clazz,
            Map<String,String> aliasMap,
            PropertyNamingStrategy propertyNamingStrategy,
            Map<String,FieldInfo> fieldInfoMap,
            Field[] fields){
        for(Field field : fields){
            /** 忽略静态字段类型 */
            if(Modifier.isStatic(field.getModifiers())){
                continue;
            }
            /** 查找当前字段是否包含JSONField注解 */
            JSONField fieldAnnotation = field.getAnnotation(JSONField.class);
            int ordinal = 0, serialzeFeatures = 0, parserFeatures = 0;
            String propertyName = field.getName();
            String label = null;
            if(fieldAnnotation != null){
                /** 忽略不序列化的字段 */
                if(!fieldAnnotation.serialize()){
                    continue;
                }
                /** 获取字段序列化顺序 */
                ordinal = fieldAnnotation.ordinal();
                serialzeFeatures = SerializerFeature.of(fieldAnnotation.serialzeFeatures());
                parserFeatures = Feature.of(fieldAnnotation.parseFeatures());
                if(fieldAnnotation.name().length() != 0){
                    /** 属性名字采用JSONField注解上面的name */
                    propertyName = fieldAnnotation.name();
                }
                if(fieldAnnotation.label().length() != 0){
                    label = fieldAnnotation.label();
                }
            }
            if(aliasMap != null){
                /** 查找是否包含属性别名的字段 */
                propertyName = aliasMap.get(propertyName);
                if(propertyName == null){
                    continue;
                }
            }
            if(propertyNamingStrategy != null){
                /** 属性字段命名规则转换 */
                propertyName = propertyNamingStrategy.translate(propertyName);
            }

            /** 封装解析类型的字段和类型 */
            if(!fieldInfoMap.containsKey(propertyName)){
                FieldInfo fieldInfo = new FieldInfo(propertyName, null, field, clazz, null, ordinal, serialzeFeatures, parserFeatures,
                        null, fieldAnnotation, label);
                fieldInfoMap.put(propertyName, fieldInfo);
            }
        }
    }
```

处理字段有序的逻辑`getFieldInfos` :

``` java
   private static List<FieldInfo> getFieldInfos(Class<?> clazz, boolean sorted, Map<String,FieldInfo> fieldInfoMap){
        List<FieldInfo> fieldInfoList = new ArrayList<FieldInfo>();
        String[] orders = null;
        /** 查找clazz上面的JSONType注解 */
        JSONType annotation = TypeUtils.getAnnotation(clazz,JSONType.class);
        if(annotation != null){
            orders = annotation.orders();
        }
        if(orders != null && orders.length > 0){
            LinkedHashMap<String,FieldInfo> map = new LinkedHashMap<String,FieldInfo>(fieldInfoList.size());
            for(FieldInfo field : fieldInfoMap.values()){
                map.put(field.name, field);
            }
            int i = 0;
            /** 先把有序字段从map移除，并添加到有序列表fieldInfoList中 */
            for(String item : orders){
                FieldInfo field = map.get(item);
                if(field != null){
                    fieldInfoList.add(field);
                    map.remove(item);
                }
            }
            /** 将map剩余元素追加到有序列表末尾 */
            for(FieldInfo field : map.values()){
                fieldInfoList.add(field);
            }
        } else{
            /** 如果注解没有要求顺序，全部添加map元素 */
            for(FieldInfo fieldInfo : fieldInfoMap.values()){
                fieldInfoList.add(fieldInfo);
            }
            if(sorted){
                Collections.sort(fieldInfoList);
            }
        }
        return fieldInfoList;
    }
```

我们在看下具体创建`JavaBeanSerializer`序列化逻辑：

``` java
	public ObjectSerializer createJavaBeanSerializer(SerializeBeanInfo beanInfo) {
	    JSONType jsonType = beanInfo.jsonType;

        boolean asm = this.asm && !fieldBased;
	    
	    if (jsonType != null) {
	        Class<?> serializerClass = jsonType.serializer();
	        if (serializerClass != Void.class) {
	            try {
	                /** 实例化注解指定的类型 */
                    Object seralizer = serializerClass.newInstance();
                    if (seralizer instanceof ObjectSerializer) {
                        return (ObjectSerializer) seralizer;
                    }
                } catch (Throwable e) {
                    // skip
                }
	        }

	        /** 注解显示指定不使用asm */
	        if (jsonType.asm() == false) {
	            asm = false;
	        }

            /** 注解显示开启WriteNonStringValueAsString、WriteEnumUsingToString
             * 和NotWriteDefaultValue不使用asm */
            for (SerializerFeature feature : jsonType.serialzeFeatures()) {
                if (SerializerFeature.WriteNonStringValueAsString == feature //
                        || SerializerFeature.WriteEnumUsingToString == feature //
                        || SerializerFeature.NotWriteDefaultValue == feature) {
                    asm = false;
                    break;
                }
            }
        }

	    Class<?> clazz = beanInfo.beanType;
	    /** 非public类型，直接使用JavaBeanSerializer序列化 */
		if (!Modifier.isPublic(beanInfo.beanType.getModifiers())) {
			return new JavaBeanSerializer(beanInfo);
		}

        // ... 省略asm判断检查

		if (asm) {
			try {
			    /** 使用asm字节码库序列化，后面单独列一个章节分析asm源码 */
                ObjectSerializer asmSerializer = createASMSerializer(beanInfo);
                if (asmSerializer != null) {
                    return asmSerializer;
                }
            } catch (ClassNotFoundException ex) {
			    // skip
			} catch (ClassFormatError e) {
			    // skip
			} catch (ClassCastException e) {
				// skip
			} catch (Throwable e) {
				throw new JSONException("create asm serializer error, class "
						+ clazz, e);
			}
		}

		/** 默认使用JavaBeanSerializer 序列化类 */
		return new JavaBeanSerializer(beanInfo);
	}
```

OK, 一切就绪，接下来有请`JavaBeanSerializer`序列化实现登场：

``` java
    protected void write(JSONSerializer serializer, 
                      Object object, 
                      Object fieldName, 
                      Type fieldType, 
                      int features,
                      boolean unwrapped
    ) throws IOException {
        SerializeWriter out = serializer.out;

        if (object == null) {
            out.writeNull();
            return;
        }

        /** 如果开启循环引用检查，输出引用并返回 */
        if (writeReference(serializer, object, features)) {
            return;
        }

        final FieldSerializer[] getters;

        if (out.sortField) {
            getters = this.sortedGetters;
        } else {
            getters = this.getters;
        }

        SerialContext parent = serializer.context;
        if (!this.beanInfo.beanType.isEnum()) {
            /** 针对非枚举类型，创建新的上下文 */
            serializer.setContext(parent, object, fieldName, this.beanInfo.features, features);
        }

        final boolean writeAsArray = isWriteAsArray(serializer, features);

        try {
            final char startSeperator = writeAsArray ? '[' : '{';
            final char endSeperator = writeAsArray ? ']' : '}';
            if (!unwrapped) {
                out.append(startSeperator);
            }

            if (getters.length > 0 && out.isEnabled(SerializerFeature.PrettyFormat)) {
                serializer.incrementIndent();
                serializer.println();
            }

            boolean commaFlag = false;

            if ((this.beanInfo.features & SerializerFeature.WriteClassName.mask) != 0
                ||(features & SerializerFeature.WriteClassName.mask) != 0
                || serializer.isWriteClassName(fieldType, object)) {
                Class<?> objClass = object.getClass();

                final Type type;
                /** 获取字段的泛型类型 */
                if (objClass != fieldType && fieldType instanceof WildcardType) {
                    type = TypeUtils.getClass(fieldType);
                } else {
                    type = fieldType;
                }

                if (objClass != type) {
                    /** 输出字段类型名字 */
                    writeClassName(serializer, beanInfo.typeKey, object);
                    commaFlag = true;
                }
            }

            char seperator = commaFlag ? ',' : '\0';

            final boolean directWritePrefix = out.quoteFieldNames && !out.useSingleQuotes;
            /** 触发序列化BeforeFilter拦截器 */
            char newSeperator = this.writeBefore(serializer, object, seperator);
            commaFlag = newSeperator == ',';

            final boolean skipTransient = out.isEnabled(SerializerFeature.SkipTransientField);
            final boolean ignoreNonFieldGetter = out.isEnabled(SerializerFeature.IgnoreNonFieldGetter);

            for (int i = 0; i < getters.length; ++i) {
                FieldSerializer fieldSerializer = getters[i];

                Field field = fieldSerializer.fieldInfo.field;
                FieldInfo fieldInfo = fieldSerializer.fieldInfo;
                String fieldInfoName = fieldInfo.name;
                Class<?> fieldClass = fieldInfo.fieldClass;

                /** 忽略配置了transient关键字的字段 */
                if (skipTransient) {
                    if (field != null) {
                        if (fieldInfo.fieldTransient) {
                            continue;
                        }
                    }
                }

                /** 目前看到注解方法上面 field = null */
                if (ignoreNonFieldGetter) {
                    if (field == null) {
                        continue;
                    }
                }

                boolean notApply = false;
                /** 触发字段PropertyPreFilter拦截器 */
                if ((!this.applyName(serializer, object, fieldInfoName))
                    || !this.applyLabel(serializer, fieldInfo.label)) {
                    if (writeAsArray) {
                        notApply = true;
                    } else {
                        continue;
                    }
                }

                /** ??? */
                if (beanInfo.typeKey != null
                        && fieldInfoName.equals(beanInfo.typeKey)
                        && serializer.isWriteClassName(fieldType, object)) {
                    continue;
                }

                Object propertyValue;

                if (notApply) {
                    propertyValue = null;
                } else {
                    try {
                        propertyValue = fieldSerializer.getPropertyValueDirect(object);
                    } catch (InvocationTargetException ex) {
                        if (out.isEnabled(SerializerFeature.IgnoreErrorGetter)) {
                            propertyValue = null;
                        } else {
                            throw ex;
                        }
                    }
                }

                /** 针对属性名字和属性值 触发PropertyFilter拦截器 */
                if (!this.apply(serializer, object, fieldInfoName, propertyValue)) {
                    continue;
                }

                if (fieldClass == String.class && "trim".equals(fieldInfo.format)) {
                    /** 剔除字符串两边空格 */
                    if (propertyValue != null) {
                        propertyValue = ((String) propertyValue).trim();
                    }
                }

                String key = fieldInfoName;
                /** 触发属性名字NameFilter拦截器 */
                key = this.processKey(serializer, object, key, propertyValue);

                Object originalValue = propertyValue;
                /** 触发属性值ContextValueFilter拦截器 */
                propertyValue = this.processValue(serializer, fieldSerializer.fieldContext, object, fieldInfoName,
                                                        propertyValue);

                if (propertyValue == null) {
                    int serialzeFeatures = fieldInfo.serialzeFeatures;
                    if (beanInfo.jsonType != null) {
                        serialzeFeatures |= SerializerFeature.of(beanInfo.jsonType.serialzeFeatures());
                    }
                    // beanInfo.jsonType
                    if (fieldClass == Boolean.class) {
                        int defaultMask = SerializerFeature.WriteNullBooleanAsFalse.mask;
                        final int mask = defaultMask | SerializerFeature.WriteMapNullValue.mask;
                        if ((!writeAsArray) && (serialzeFeatures & mask) == 0 && (out.features & mask) == 0) {
                            continue;
                            /** 针对Boolean类型，值为空，输出false */
                        } else if ((serialzeFeatures & defaultMask) != 0 || (out.features & defaultMask) != 0) {
                            propertyValue = false;
                        }
                    } else if (fieldClass == String.class) {
                        int defaultMask = SerializerFeature.WriteNullStringAsEmpty.mask;
                        final int mask = defaultMask | SerializerFeature.WriteMapNullValue.mask;
                        if ((!writeAsArray) && (serialzeFeatures & mask) == 0 && (out.features & mask) == 0) {
                            continue;
                        } else if ((serialzeFeatures & defaultMask) != 0 || (out.features & defaultMask) != 0) {
                            /** 针对string类型，值为空，输出空串"" */
                            propertyValue = "";
                        }
                    } else if (Number.class.isAssignableFrom(fieldClass)) {
                        int defaultMask = SerializerFeature.WriteNullNumberAsZero.mask;
                        final int mask = defaultMask | SerializerFeature.WriteMapNullValue.mask;
                        if ((!writeAsArray) && (serialzeFeatures & mask) == 0 && (out.features & mask) == 0) {
                            continue;
                        } else if ((serialzeFeatures & defaultMask) != 0 || (out.features & defaultMask) != 0) {
                            /** 针对数字类型，值为空，输出0 */
                            propertyValue = 0;
                        }
                    } else if (Collection.class.isAssignableFrom(fieldClass)) {
                        int defaultMask = SerializerFeature.WriteNullListAsEmpty.mask;
                        final int mask = defaultMask | SerializerFeature.WriteMapNullValue.mask;
                        if ((!writeAsArray) && (serialzeFeatures & mask) == 0 && (out.features & mask) == 0) {
                            continue;
                        } else if ((serialzeFeatures & defaultMask) != 0 || (out.features & defaultMask) != 0) {
                            propertyValue = Collections.emptyList();
                        }
                        /** 针对值为null，配置序列化不输出特性，则输出json字符串排除这些属性 */
                    } else if ((!writeAsArray) && (!fieldSerializer.writeNull) && !out.isEnabled(SerializerFeature.WriteMapNullValue.mask)){
                        continue;
                    }
                }

                /** 忽略序列化配置为不输出默认值的字段 */
                if (propertyValue != null
                        && (out.notWriteDefaultValue
                        || (fieldInfo.serialzeFeatures & SerializerFeature.NotWriteDefaultValue.mask) != 0
                        || (beanInfo.features & SerializerFeature.NotWriteDefaultValue.mask) != 0
                        )) {
                    Class<?> fieldCLass = fieldInfo.fieldClass;
                    if (fieldCLass == byte.class && propertyValue instanceof Byte
                        && ((Byte) propertyValue).byteValue() == 0) {
                        continue;
                    } else if (fieldCLass == short.class && propertyValue instanceof Short
                               && ((Short) propertyValue).shortValue() == 0) {
                        continue;
                    } else if (fieldCLass == int.class && propertyValue instanceof Integer
                               && ((Integer) propertyValue).intValue() == 0) {
                        continue;
                    } else if (fieldCLass == long.class && propertyValue instanceof Long
                               && ((Long) propertyValue).longValue() == 0L) {
                        continue;
                    } else if (fieldCLass == float.class && propertyValue instanceof Float
                               && ((Float) propertyValue).floatValue() == 0F) {
                        continue;
                    } else if (fieldCLass == double.class && propertyValue instanceof Double
                               && ((Double) propertyValue).doubleValue() == 0D) {
                        continue;
                    } else if (fieldCLass == boolean.class && propertyValue instanceof Boolean
                               && !((Boolean) propertyValue).booleanValue()) {
                        continue;
                    }
                }

                if (commaFlag) {
                    if (fieldInfo.unwrapped
                            && propertyValue instanceof Map
                            && ((Map) propertyValue).size() == 0) {
                        continue;
                    }

                    out.write(',');
                    if (out.isEnabled(SerializerFeature.PrettyFormat)) {
                        serializer.println();
                    }
                }

                /** 应用拦截器后变更了key */
                if (key != fieldInfoName) {
                    if (!writeAsArray) {
                        out.writeFieldName(key, true);
                    }

                    serializer.write(propertyValue);
                } else if (originalValue != propertyValue) {
                    if (!writeAsArray) {
                        fieldSerializer.writePrefix(serializer);
                    }
                    /** 应用拦截器后变更了属性值，查找value的class类型进行序列化 */
                    serializer.write(propertyValue);
                } else {
                    if (!writeAsArray) {
                        /** 输出属性字段名称 */
                        if (!fieldInfo.unwrapped) {
                            if (directWritePrefix) {
                                out.write(fieldInfo.name_chars, 0, fieldInfo.name_chars.length);
                            } else {
                                fieldSerializer.writePrefix(serializer);
                            }
                        }
                    }

                    if (!writeAsArray) {
                        JSONField fieldAnnotation = fieldInfo.getAnnotation();
                        if (fieldClass == String.class && (fieldAnnotation == null || fieldAnnotation.serializeUsing() == Void.class)) {

                            /** 处理针对字符串类型属性值输出 */
                            if (propertyValue == null) {
                                if ((out.features & SerializerFeature.WriteNullStringAsEmpty.mask) != 0
                                    || (fieldSerializer.features & SerializerFeature.WriteNullStringAsEmpty.mask) != 0) {
                                    out.writeString("");
                                } else {
                                    out.writeNull();
                                }
                            } else {
                                String propertyValueString = (String) propertyValue;

                                if (out.useSingleQuotes) {
                                    out.writeStringWithSingleQuote(propertyValueString);
                                } else {
                                    out.writeStringWithDoubleQuote(propertyValueString, (char) 0);
                                }
                            }
                        } else {
                            if (fieldInfo.unwrapped
                                    && propertyValue instanceof Map
                                    && ((Map) propertyValue).size() == 0) {
                                commaFlag = false;
                                continue;
                            }

                            fieldSerializer.writeValue(serializer, propertyValue);
                        }
                    } else {
                        /** 基于数组形式输出 [,,,] */
                        fieldSerializer.writeValue(serializer, propertyValue);
                    }
                }

                boolean fieldUnwrappedNull = false;
                if (fieldInfo.unwrapped
                        && propertyValue instanceof Map) {
                    Map map = ((Map) propertyValue);
                    if (map.size() == 0) {
                        fieldUnwrappedNull = true;
                    } else if (!serializer.isEnabled(SerializerFeature.WriteMapNullValue)){
                        boolean hasNotNull = false;
                        for (Object value : map.values()) {
                            if (value != null) {
                                hasNotNull = true;
                                break;
                            }
                        }
                        if (!hasNotNull) {
                            fieldUnwrappedNull = true;
                        }
                    }
                }

                if (!fieldUnwrappedNull) {
                    commaFlag = true;
                }
            }

            /** 触发序列化AfterFilter拦截器 */
            this.writeAfter(serializer, object, commaFlag ? ',' : '\0');

            if (getters.length > 0 && out.isEnabled(SerializerFeature.PrettyFormat)) {
                serializer.decrementIdent();
                serializer.println();
            }

            if (!unwrapped) {
                out.append(endSeperator);
            }
        } catch (Exception e) {
            String errorMessage = "write javaBean error, fastjson version " + JSON.VERSION;
            if (object != null) {
                errorMessage += ", class " + object.getClass().getName();
            }
            if (fieldName != null) {
                errorMessage += ", fieldName : " + fieldName;
            }
            if (e.getMessage() != null) {
                errorMessage += (", " + e.getMessage());
            }

            throw new JSONException(errorMessage, e);
        } finally {
            serializer.context = parent;
        }
    }
```

在序列化过程中我们重点关注一下序列化属性值的逻辑`fieldSerializer.writeValue(serializer, propertyValue)`：

``` java
    public void writeValue(JSONSerializer serializer, Object propertyValue) throws Exception {
        if (runtimeInfo == null) {

            Class<?> runtimeFieldClass;
            /** 获取字段的类型 */
            if (propertyValue == null) {
                runtimeFieldClass = this.fieldInfo.fieldClass;
            } else {
                runtimeFieldClass = propertyValue.getClass();
            }

            ObjectSerializer fieldSerializer = null;
            JSONField fieldAnnotation = fieldInfo.getAnnotation();

            /** 创建并初始化字段指定序列化类型 */
            if (fieldAnnotation != null && fieldAnnotation.serializeUsing() != Void.class) {
                fieldSerializer = (ObjectSerializer) fieldAnnotation.serializeUsing().newInstance();
                serializeUsing = true;
            } else {
                /** 针对format和primitive类型创建序列化类型 */
                if (format != null) {
                    if (runtimeFieldClass == double.class || runtimeFieldClass == Double.class) {
                        fieldSerializer = new DoubleSerializer(format);
                    } else if (runtimeFieldClass == float.class || runtimeFieldClass == Float.class) {
                        fieldSerializer = new FloatCodec(format);
                    }
                }

                if (fieldSerializer == null) {
                    /** 根据属性值class类型查找序列化类型 */
                    fieldSerializer = serializer.getObjectWriter(runtimeFieldClass);
                }
            }

            /** 封装序列化类型和属性值的类型 */
            runtimeInfo = new RuntimeSerializerInfo(fieldSerializer, runtimeFieldClass);
        }
        
        final RuntimeSerializerInfo runtimeInfo = this.runtimeInfo;
        
        final int fieldFeatures = disableCircularReferenceDetect?
                (fieldInfo.serialzeFeatures|SerializerFeature.DisableCircularReferenceDetect.getMask()):fieldInfo.serialzeFeatures;

        if (propertyValue == null) {
            SerializeWriter out  = serializer.out;

            if (fieldInfo.fieldClass == Object.class
                    && out.isEnabled(SerializerFeature.WRITE_MAP_NULL_FEATURES)) {
                out.writeNull();
                return;
            }

            /** 针对属性值为null的情况处理 */
            Class<?> runtimeFieldClass = runtimeInfo.runtimeFieldClass;

            if (Number.class.isAssignableFrom(runtimeFieldClass)) {
                out.writeNull(features, SerializerFeature.WriteNullNumberAsZero.mask);
                return;
            } else if (String.class == runtimeFieldClass) {
                out.writeNull(features, SerializerFeature.WriteNullStringAsEmpty.mask);
                return;
            } else if (Boolean.class == runtimeFieldClass) {
                out.writeNull(features, SerializerFeature.WriteNullBooleanAsFalse.mask);
                return;
            } else if (Collection.class.isAssignableFrom(runtimeFieldClass)) {
                out.writeNull(features, SerializerFeature.WriteNullListAsEmpty.mask);
                return;
            }

            ObjectSerializer fieldSerializer = runtimeInfo.fieldSerializer;

            if ((out.isEnabled(SerializerFeature.WRITE_MAP_NULL_FEATURES))
                    && fieldSerializer instanceof JavaBeanSerializer) {
                out.writeNull();
                return;
            }

            /** 序列化null对象 */
            fieldSerializer.write(serializer, null, fieldInfo.name, fieldInfo.fieldType, fieldFeatures);
            return;
        }

        if (fieldInfo.isEnum) {
            if (writeEnumUsingName) {
                /** 使用枚举名字序列化 */
                serializer.out.writeString(((Enum<?>) propertyValue).name());
                return;
            }

            if (writeEnumUsingToString) {
                /** 使用枚举toString字符串序列化 */
                serializer.out.writeString(((Enum<?>) propertyValue).toString());
                return;
            }
        }
        
        Class<?> valueClass = propertyValue.getClass();
        ObjectSerializer valueSerializer;
        if (valueClass == runtimeInfo.runtimeFieldClass || serializeUsing) {
            /** 使用序列化注解指定的序列化类型 */
            valueSerializer = runtimeInfo.fieldSerializer;
        } else {
            valueSerializer = serializer.getObjectWriter(valueClass);
        }
        
        if (format != null && !(valueSerializer instanceof DoubleSerializer || valueSerializer instanceof FloatCodec)) {
            if (valueSerializer instanceof ContextObjectSerializer) {
                ((ContextObjectSerializer) valueSerializer).write(serializer, propertyValue, this.fieldContext);    
            } else {
                serializer.writeWithFormat(propertyValue, format);
            }
            return;
        }

        /** 特殊检查是否是具体类型序列化JavaBeanSerializer、 MapSerializer */
        if (fieldInfo.unwrapped) {
            if (valueSerializer instanceof JavaBeanSerializer) {
                JavaBeanSerializer javaBeanSerializer = (JavaBeanSerializer) valueSerializer;
                javaBeanSerializer.write(serializer, propertyValue, fieldInfo.name, fieldInfo.fieldType, fieldFeatures, true);
                return;
            }

            if (valueSerializer instanceof MapSerializer) {
                MapSerializer mapSerializer = (MapSerializer) valueSerializer;
                mapSerializer.write(serializer, propertyValue, fieldInfo.name, fieldInfo.fieldType, fieldFeatures, true);
                return;
            }
        }

        /** 针对字段类型和属性值类型不一致退化成使用JavaBeanSerializer */
        if ((features & SerializerFeature.WriteClassName.mask) != 0
                && valueClass != fieldInfo.fieldClass
                && JavaBeanSerializer.class.isInstance(valueSerializer)) {
            ((JavaBeanSerializer) valueSerializer).write(serializer, propertyValue, fieldInfo.name, fieldInfo.fieldType, fieldFeatures, false);
            return;
        }

        /** 使用值序列化类型处理 */
        valueSerializer.write(serializer, propertyValue, fieldInfo.name, fieldInfo.fieldType, fieldFeatures);
    }
```

到此序列化成json字符串已经全部讲完了，接下来讲解反序列化内容，包含词法分析的代码。