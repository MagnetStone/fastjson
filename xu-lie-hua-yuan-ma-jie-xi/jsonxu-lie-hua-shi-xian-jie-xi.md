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

我们发现在方法内部调用`getObjectWriter(clazz)`根据具体类型查找序列化实例，方法内部只有一行： `config.getObjectWriter(clazz)`，让我们更进一步查看委托实现细节：

``` java
```