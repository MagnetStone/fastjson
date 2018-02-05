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