## 概要

fastjson序列化主要使用入口就是在JSON.java类中，它提供非常简便和友好的api将java对象转换成json字符串。

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

使用便捷接口toJSONString方法，可以将任意java对象序列化为json字符串，内部调用toJSONString(Object , SerializeFilter[] , SerializerFeature... ) ：

``` java
    public static String toJSONString(Object object, SerializeFilter[] filters, SerializerFeature... features) {
        return toJSONString(object, SerializeConfig.globalInstance, filters, null, DEFAULT_GENERATE_FEATURE, features);
    }
```