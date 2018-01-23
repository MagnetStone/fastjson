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
    public static String toJSONString(Object object,                    // 序列化对象
                                      SerializeConfig config,           // 全局序列化配置
                                      SerializeFilter[] filters,        // 序列化拦截器
                                      String dateFormat,                // 序列化日期格式
                                      int defaultFeatures,              // 默认序列化特性
                                      SerializerFeature... features) {  // 自定义序列化特性
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

这个序列化方法实际并不是正在执行序列化操作，首先做序列化特性配置，然后追加序列化拦截器，开始执行序列化对象操作委托给了config对象查找。