package com.alibaba.fastjson.parser.deserializer;

import java.lang.reflect.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.annotation.JSONField;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.DefaultJSONParser.ResolveTask;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONLexerBase;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.ParseContext;
import com.alibaba.fastjson.parser.ParserConfig;
import com.alibaba.fastjson.util.FieldInfo;
import com.alibaba.fastjson.util.JavaBeanInfo;
import com.alibaba.fastjson.util.TypeUtils;

public class JavaBeanDeserializer implements ObjectDeserializer {

    private final FieldDeserializer[]   fieldDeserializers;
    protected final FieldDeserializer[] sortedFieldDeserializers;
    protected final Class<?>            clazz;
    public final JavaBeanInfo           beanInfo;
    private ConcurrentMap<String, Object> extraFieldDeserializers;

    private final Map<String, FieldDeserializer> alterNameFieldDeserializers;

    private transient long[] smartMatchHashArray;
    private transient short[] smartMatchHashArrayMapping;

    private transient long[] hashArray;
    private transient short[] hashArrayMapping;
    
    public JavaBeanDeserializer(ParserConfig config, Class<?> clazz) {
        this(config, clazz, clazz);
    }

    public JavaBeanDeserializer(ParserConfig config, Class<?> clazz, Type type){
        this(config //
                , JavaBeanInfo.build(clazz, type, config.propertyNamingStrategy, config.fieldBased, config.compatibleWithJavaBean)
        );
    }
    
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

    public FieldDeserializer getFieldDeserializer(String key) {
        return getFieldDeserializer(key, null);
    }

    public FieldDeserializer getFieldDeserializer(String key, int[] setFlags) {
        if (key == null) {
            return null;
        }
        
        int low = 0;
        int high = sortedFieldDeserializers.length - 1;

        while (low <= high) {
            int mid = (low + high) >>> 1;
            
            String fieldName = sortedFieldDeserializers[mid].fieldInfo.name;
            
            int cmp = fieldName.compareTo(key);

            if (cmp < 0) {
                low = mid + 1;
            } else if (cmp > 0) {
                high = mid - 1;
            } else {
                if (isSetFlag(mid, setFlags)) {
                    return null;
                }

                return sortedFieldDeserializers[mid]; // key found
            }
        }

        if(this.alterNameFieldDeserializers != null){
            return this.alterNameFieldDeserializers.get(key);
        }
        
        return null;  // key not found.
    }

    public FieldDeserializer getFieldDeserializer(long hash) {
        if (this.hashArray == null) {
            long[] hashArray = new long[sortedFieldDeserializers.length];
            for (int i = 0; i < sortedFieldDeserializers.length; i++) {
                hashArray[i] = TypeUtils.fnv1a_64(sortedFieldDeserializers[i].fieldInfo.name);
            }
            Arrays.sort(hashArray);
            this.hashArray = hashArray;
        }

        int pos = Arrays.binarySearch(hashArray, hash);
        if (pos < 0) {
            return null;
        }

        if (hashArrayMapping == null) {
            short[] mapping = new short[hashArray.length];
            Arrays.fill(mapping, (short) -1);
            for (int i = 0; i < sortedFieldDeserializers.length; i++) {
                int p = Arrays.binarySearch(hashArray
                        , TypeUtils.fnv1a_64(sortedFieldDeserializers[i].fieldInfo.name));
                if (p >= 0) {
                    mapping[p] = (short) i;
                }
            }
            hashArrayMapping = mapping;
        }

        int setterIndex = hashArrayMapping[pos];
        if (setterIndex != -1) {
            return sortedFieldDeserializers[setterIndex];
        }

        return null; // key not found.
    }

    static boolean isSetFlag(int i, int[] setFlags) {
        if (setFlags == null) {
            return false;
        }

        int flagIndex = i / 32;
        int bitIndex = i % 32;
        if (flagIndex < setFlags.length) {
            if ((setFlags[flagIndex] & (1 << bitIndex)) != 0) {
                return true;
            }
        }

        return false;
    }
    
    public Object createInstance(DefaultJSONParser parser, Type type) {
        if (type instanceof Class) {
            if (clazz.isInterface()) {
                /** 针对反序列化时接口类型的，通过jdk冬天代理拦截put和get等操作，
                 *  进行set或者put值的操作值会存储在jsonobject内部的map结构
                 */
                Class<?> clazz = (Class<?>) type;
                ClassLoader loader = Thread.currentThread().getContextClassLoader();
                final JSONObject obj = new JSONObject();
                Object proxy = Proxy.newProxyInstance(loader, new Class<?>[] { clazz }, obj);
                return proxy;
            }
        }

        /** 忽略没有默认构造函数和没有创建对象的工厂方法 */
        if (beanInfo.defaultConstructor == null && beanInfo.factoryMethod == null) {
            return null;
        }

        /** 忽略同时存在显示构造函数和创建对象的工厂方法的场景 */
        if (beanInfo.factoryMethod != null && beanInfo.defaultConstructorParameterSize > 0) {
            return null;
        }

        Object object;
        try {
            Constructor<?> constructor = beanInfo.defaultConstructor;
            /** 存在默认无参构造函数 */
            if (beanInfo.defaultConstructorParameterSize == 0) {
                if (constructor != null) {
                    object = constructor.newInstance();
                } else {
                    /** 否则使用工厂方法生成对象 */
                    object = beanInfo.factoryMethod.invoke(null);
                }
            } else {
                ParseContext context = parser.getContext();
                if (context == null || context.object == null) {
                    throw new JSONException("can't create non-static inner class instance.");
                }

                final String typeName;
                if (type instanceof Class) {
                    typeName = ((Class<?>) type).getName();
                } else {
                    throw new JSONException("can't create non-static inner class instance.");
                }

                final int lastIndex = typeName.lastIndexOf('$');
                String parentClassName = typeName.substring(0, lastIndex);

                Object ctxObj = context.object;
                String parentName = ctxObj.getClass().getName();

                Object param = null;
                if (!parentName.equals(parentClassName)) {
                    ParseContext parentContext = context.parent;
                    if (parentContext != null
                            && parentContext.object != null
                            && ("java.util.ArrayList".equals(parentName)
                            || "java.util.List".equals(parentName)
                            || "java.util.Collection".equals(parentName)
                            || "java.util.Map".equals(parentName)
                            || "java.util.HashMap".equals(parentName))) {
                        parentName = parentContext.object.getClass().getName();
                        if (parentName.equals(parentClassName)) {
                            param = parentContext.object;
                        }
                    }
                } else {
                    param = ctxObj;
                }

                if (param == null) {
                    throw new JSONException("can't create non-static inner class instance.");
                }

                object = constructor.newInstance(param);
            }
        } catch (JSONException e) {
            throw e;
        } catch (Exception e) {
            throw new JSONException("create instance error, class " + clazz.getName(), e);
        }

        /** 开启InitStringFieldAsEmpty特性，会把字符串字段初始化为空串 */
        if (parser != null
                && parser.lexer.isEnabled(Feature.InitStringFieldAsEmpty)) {
            for (FieldInfo fieldInfo : beanInfo.fields) {
                if (fieldInfo.fieldClass == String.class) {
                    try {
                        fieldInfo.set(object, "");
                    } catch (Exception e) {
                        throw new JSONException("create instance error, class " + clazz.getName(), e);
                    }
                }
            }
        }

        return object;
    }
    
    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName) {
        return deserialze(parser, type, fieldName, 0);
    }

    public <T> T deserialze(DefaultJSONParser parser, Type type, Object fieldName, int features) {
        return deserialze(parser, type, fieldName, null, features, null);
    }

    @SuppressWarnings({ "unchecked" })
    public <T> T deserialzeArrayMapping(DefaultJSONParser parser, Type type, Object fieldName, Object object) {
        final JSONLexer lexer = parser.lexer;
        if (lexer.token() != JSONToken.LBRACKET) {
            throw new JSONException("error");
        }

        object = createInstance(parser, type);

        for (int i = 0, size = sortedFieldDeserializers.length; i < size; ++i) {
            final char seperator = (i == size - 1) ? ']' : ',';
            FieldDeserializer fieldDeser = sortedFieldDeserializers[i];
            Class<?> fieldClass = fieldDeser.fieldInfo.fieldClass;
            if (fieldClass == int.class) {
                int value = lexer.scanInt(seperator);
                fieldDeser.setValue(object, value);
            } else if (fieldClass == String.class) {
                String value = lexer.scanString(seperator);
                fieldDeser.setValue(object, value);
            } else if (fieldClass == long.class) {
                long value = lexer.scanLong(seperator);
                fieldDeser.setValue(object, value);
            } else if (fieldClass.isEnum()) {
                char ch = lexer.getCurrent();
                
                Object value;
                if (ch == '\"' || ch == 'n') {
                    value = lexer.scanEnum(fieldClass, parser.getSymbolTable(), seperator);
                } else if (ch >= '0' && ch <= '9') {
                    int ordinal = lexer.scanInt(seperator);
                    
                    EnumDeserializer enumDeser = (EnumDeserializer) ((DefaultFieldDeserializer) fieldDeser).getFieldValueDeserilizer(parser.getConfig());
                    value = enumDeser.valueOf(ordinal);
                } else {
                    value = scanEnum(lexer, seperator);
                }
                
                fieldDeser.setValue(object, value);
            } else if (fieldClass == boolean.class) {
                boolean value = lexer.scanBoolean(seperator);
                fieldDeser.setValue(object, value);
            } else if (fieldClass == float.class) {
                float value = lexer.scanFloat(seperator);
                fieldDeser.setValue(object, value);
            } else if (fieldClass == double.class) {
                double value = lexer.scanDouble(seperator);
                fieldDeser.setValue(object, value);
            } else if (fieldClass == java.util.Date.class && lexer.getCurrent() == '1') {
                long longValue = lexer.scanLong(seperator);
                fieldDeser.setValue(object, new java.util.Date(longValue));
            } else if (fieldClass == BigDecimal.class) {
                BigDecimal value = lexer.scanDecimal(seperator);
                fieldDeser.setValue(object, value);
            } else {
                lexer.nextToken(JSONToken.LBRACKET);
                Object value = parser.parseObject(fieldDeser.fieldInfo.fieldType, fieldDeser.fieldInfo.name);
                fieldDeser.setValue(object, value);

                if (lexer.token() == JSONToken.RBRACKET) {
                    break;
                }

                check(lexer, seperator == ']' ? JSONToken.RBRACKET : JSONToken.COMMA);
                // parser.accept(seperator == ']' ? JSONToken.RBRACKET : JSONToken.COMMA);
            }
        }
        lexer.nextToken(JSONToken.COMMA);

        return (T) object;
    }

    protected void check(final JSONLexer lexer, int token) {
        if (lexer.token() != token) {
            throw new JSONException("syntax error");
        }
    }
    
    protected Enum<?> scanEnum(JSONLexer lexer, char seperator) {
        throw new JSONException("illegal enum. " + lexer.info());
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
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

    protected Enum scanEnum(JSONLexerBase lexer, char[] name_chars, ObjectDeserializer fieldValueDeserilizer) {
        EnumDeserializer enumDeserializer = null;
        if (fieldValueDeserilizer instanceof EnumDeserializer) {
            enumDeserializer = (EnumDeserializer) fieldValueDeserilizer;
        }

        if (enumDeserializer == null) {
            lexer.matchStat = JSONLexer.NOT_MATCH;
            return null;
        }

        long enumNameHashCode = lexer.scanFieldSymbol(name_chars);
        if (lexer.matchStat > 0) {
            return enumDeserializer.getEnumByHashCode(enumNameHashCode);
        } else {
            return null;
        }
    }

    public boolean parseField(DefaultJSONParser parser, String key, Object object, Type objectType,
                              Map<String, Object> fieldValues) {
        return parseField(parser, key, object, objectType, fieldValues, null);
    }
    
    public boolean parseField(DefaultJSONParser parser, String key, Object object, Type objectType,
                              Map<String, Object> fieldValues, int[] setFlags) {
        JSONLexer lexer = parser.lexer;

        final int disableFieldSmartMatchMask = Feature.DisableFieldSmartMatch.mask;
        FieldDeserializer fieldDeserializer;
        if (lexer.isEnabled(disableFieldSmartMatchMask) || (this.beanInfo.parserFeatures & disableFieldSmartMatchMask) != 0) {
            fieldDeserializer = getFieldDeserializer(key);
        } else {
            fieldDeserializer = smartMatch(key, setFlags);
        }

        final int mask = Feature.SupportNonPublicField.mask;
        if (fieldDeserializer == null
                && (lexer.isEnabled(mask)
                    || (this.beanInfo.parserFeatures & mask) != 0)) {
            if (this.extraFieldDeserializers == null) {
                ConcurrentHashMap extraFieldDeserializers = new ConcurrentHashMap<String, Object>(1, 0.75f, 1);
                for (Class c = this.clazz; c != null && c != Object.class; c = c.getSuperclass()) {
                    Field[] fields = c.getDeclaredFields();
                    for (Field field : fields) {
                        String fieldName = field.getName();
                        if (this.getFieldDeserializer(fieldName) != null) {
                            continue;
                        }
                        int fieldModifiers = field.getModifiers();
                        if ((fieldModifiers & Modifier.FINAL) != 0 || (fieldModifiers & Modifier.STATIC) != 0) {
                            continue;
                        }
                        extraFieldDeserializers.put(fieldName, field);
                    }
                }
                this.extraFieldDeserializers = extraFieldDeserializers;
            }

            Object deserOrField = extraFieldDeserializers.get(key);
            if (deserOrField != null) {
                if (deserOrField instanceof FieldDeserializer) {
                    fieldDeserializer = ((FieldDeserializer) deserOrField);
                } else {
                    Field field = (Field) deserOrField;
                    field.setAccessible(true);
                    FieldInfo fieldInfo = new FieldInfo(key, field.getDeclaringClass(), field.getType(), field.getGenericType(), field, 0, 0, 0);
                    fieldDeserializer = new DefaultFieldDeserializer(parser.getConfig(), clazz, fieldInfo);
                    extraFieldDeserializers.put(key, fieldDeserializer);
                }
            }
        }

        if (fieldDeserializer == null) {
            if (!lexer.isEnabled(Feature.IgnoreNotMatch)) {
                throw new JSONException("setter not found, class " + clazz.getName() + ", property " + key);
            }

            for (FieldDeserializer fieldDeser : this.sortedFieldDeserializers) {
                FieldInfo fieldInfo = fieldDeser.fieldInfo;
                if (fieldInfo.unwrapped //
                        && fieldDeser instanceof DefaultFieldDeserializer) {
                    if (fieldInfo.field != null) {
                        DefaultFieldDeserializer defaultFieldDeserializer = (DefaultFieldDeserializer) fieldDeser;
                        ObjectDeserializer fieldValueDeser = defaultFieldDeserializer.getFieldValueDeserilizer(parser.getConfig());
                        if (fieldValueDeser instanceof JavaBeanDeserializer) {
                            JavaBeanDeserializer javaBeanFieldValueDeserializer = (JavaBeanDeserializer) fieldValueDeser;
                            FieldDeserializer unwrappedFieldDeser = javaBeanFieldValueDeserializer.getFieldDeserializer(key);
                            if (unwrappedFieldDeser != null) {
                                Object fieldObject;
                                try {
                                    fieldObject = fieldInfo.field.get(object);
                                    if (fieldObject == null) {
                                        fieldObject = ((JavaBeanDeserializer) fieldValueDeser).createInstance(parser, fieldInfo.fieldType);
                                        fieldDeser.setValue(object, fieldObject);
                                    }
                                    lexer.nextTokenWithColon(defaultFieldDeserializer.getFastMatchToken());
                                    unwrappedFieldDeser.parseField(parser, fieldObject, objectType, fieldValues);
                                    return true;
                                } catch (Exception e) {
                                    throw new JSONException("parse unwrapped field error.", e);
                                }
                            }
                        } else if (fieldValueDeser instanceof MapDeserializer) {
                            MapDeserializer javaBeanFieldValueDeserializer = (MapDeserializer) fieldValueDeser;

                            Map fieldObject;
                            try {
                                fieldObject = (Map) fieldInfo.field.get(object);
                                if (fieldObject == null) {
                                    fieldObject = javaBeanFieldValueDeserializer.createMap(fieldInfo.fieldType);
                                    fieldDeser.setValue(object, fieldObject);
                                }

                                lexer.nextTokenWithColon();
                                Object fieldValue = parser.parse(key);
                                fieldObject.put(key, fieldValue);
                            } catch (Exception e) {
                                throw new JSONException("parse unwrapped field error.", e);
                            }
                            return true;
                        }
                    } else if (fieldInfo.method.getParameterTypes().length == 2) {
                        lexer.nextTokenWithColon();
                        Object fieldValue = parser.parse(key);
                        try {
                            fieldInfo.method.invoke(object, key, fieldValue);
                        } catch (Exception e) {
                            throw new JSONException("parse unwrapped field error.", e);
                        }
                        return true;
                    }
                }
            }
            
            parser.parseExtra(object, key);

            return false;
        }

        int fieldIndex = -1;
        for (int i = 0; i < sortedFieldDeserializers.length; ++i) {
            if (sortedFieldDeserializers[i] == fieldDeserializer) {
                fieldIndex = i;
                break;
            }
        }
        if (fieldIndex != -1 && setFlags != null && key.startsWith("_")) {
            if (isSetFlag(fieldIndex, setFlags)) {
                parser.parseExtra(object, key);
                return false;
            }
        }

        lexer.nextTokenWithColon(fieldDeserializer.getFastMatchToken());

        fieldDeserializer.parseField(parser, object, objectType, fieldValues);

        return true;
    }

    public FieldDeserializer smartMatch(String key) {
        return smartMatch(key, null);
    }

    public FieldDeserializer smartMatch(String key, int[] setFlags) {
        if (key == null) {
            return null;
        }
        
        FieldDeserializer fieldDeserializer = getFieldDeserializer(key, setFlags);

        if (fieldDeserializer == null) {
            long smartKeyHash = TypeUtils.fnv1a_64_lower(key);
            if (this.smartMatchHashArray == null) {
                long[] hashArray = new long[sortedFieldDeserializers.length];
                for (int i = 0; i < sortedFieldDeserializers.length; i++) {
                    hashArray[i] = TypeUtils.fnv1a_64_lower(sortedFieldDeserializers[i].fieldInfo.name);
                }
                Arrays.sort(hashArray);
                this.smartMatchHashArray = hashArray;
            }

            // smartMatchHashArrayMapping
            int pos = Arrays.binarySearch(smartMatchHashArray, smartKeyHash);
            boolean is = false;
            if (pos < 0 && (is = key.startsWith("is"))) {
                smartKeyHash = TypeUtils.fnv1a_64_lower(key.substring(2));
                pos = Arrays.binarySearch(smartMatchHashArray, smartKeyHash);
            }

            if (pos >= 0) {
                if (smartMatchHashArrayMapping == null) {
                    short[] mapping = new short[smartMatchHashArray.length];
                    Arrays.fill(mapping, (short) -1);
                    for (int i = 0; i < sortedFieldDeserializers.length; i++) {
                        int p = Arrays.binarySearch(smartMatchHashArray
                                , TypeUtils.fnv1a_64_lower(sortedFieldDeserializers[i].fieldInfo.name));
                        if (p >= 0) {
                            mapping[p] = (short) i;
                        }
                    }
                    smartMatchHashArrayMapping = mapping;
                }

                int deserIndex = smartMatchHashArrayMapping[pos];
                if (deserIndex != -1) {
                    if (!isSetFlag(deserIndex, setFlags)) {
                        fieldDeserializer = sortedFieldDeserializers[deserIndex];
                    }
                }
            }

            if (fieldDeserializer != null) {
                FieldInfo fieldInfo = fieldDeserializer.fieldInfo;
                if ((fieldInfo.parserFeatures & Feature.DisableFieldSmartMatch.mask) != 0) {
                    return null;
                }

                Class fieldClass = fieldInfo.fieldClass;
                if (is && (fieldClass != boolean.class && fieldClass != Boolean.class)) {
                    fieldDeserializer = null;
                }
            }
        }


        return fieldDeserializer;
    }

    public int getFastMatchToken() {
        return JSONToken.LBRACE;
    }
    
    public Object createInstance(Map<String, Object> map, ParserConfig config) //
                                                                               throws IllegalArgumentException,
                                                                               IllegalAccessException,
                                                                               InvocationTargetException {
        Object object = null;
        
        if (beanInfo.creatorConstructor == null && beanInfo.factoryMethod == null) {
            object = createInstance(null, clazz);
            
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                FieldDeserializer fieldDeser = smartMatch(key);
                if (fieldDeser == null) {
                    continue;
                }

                final FieldInfo fieldInfo = fieldDeser.fieldInfo;
                Type paramType = fieldInfo.fieldType;
                value = TypeUtils.cast(value, paramType, config);

                fieldDeser.setValue(object, value);
            }

            if (beanInfo.buildMethod != null) {
                Object builtObj;
                try {
                    builtObj = beanInfo.buildMethod.invoke(object);
                } catch (Exception e) {
                    throw new JSONException("build object error", e);
                }

                return builtObj;
            }

            return object;
        }

        
        FieldInfo[] fieldInfoList = beanInfo.fields;
        int size = fieldInfoList.length;
        Object[] params = new Object[size];
        Map<String, Integer> missFields = null;
        for (int i = 0; i < size; ++i) {
            FieldInfo fieldInfo = fieldInfoList[i];
            Object param = map.get(fieldInfo.name);

            if (param == null) {
                Class<?> fieldClass = fieldInfo.fieldClass;
                if (fieldClass == int.class) {
                    param = 0;
                } else if (fieldClass == long.class) {
                    param = 0L;
                } else if (fieldClass == short.class) {
                    param = Short.valueOf((short) 0);
                } else if (fieldClass == byte.class) {
                    param = Byte.valueOf((byte) 0);
                } else if (fieldClass == float.class) {
                    param = Float.valueOf(0);
                } else if (fieldClass == double.class) {
                    param = Double.valueOf(0);
                } else if (fieldClass == char.class) {
                    param = '0';
                } else if (fieldClass == boolean.class) {
                    param = false;
                }
                if (missFields == null) {
                    missFields = new HashMap<String, Integer>();
                }
                missFields.put(fieldInfo.name, i);
            }
            params[i] = param;
        }

        if (missFields != null) {
            for (Map.Entry<String, Object> entry : map.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                FieldDeserializer fieldDeser = smartMatch(key);
                if (fieldDeser != null) {
                    Integer index = missFields.get(fieldDeser.fieldInfo.name);
                    if (index != null) {
                        params[index] = value;
                    }
                }
            }
        }

        if (beanInfo.creatorConstructor != null) {
            try {
                object = beanInfo.creatorConstructor.newInstance(params);
            } catch (Exception e) {
                throw new JSONException("create instance error, "
                                        + beanInfo.creatorConstructor.toGenericString(), e);
            }
        } else if (beanInfo.factoryMethod != null) {
            try {
                object = beanInfo.factoryMethod.invoke(null, params);
            } catch (Exception e) {
                throw new JSONException("create factory method error, " + beanInfo.factoryMethod.toString(), e);
            }
        }
        
        return object;
    }
    
    public Type getFieldType(int ordinal) {
        return sortedFieldDeserializers[ordinal].fieldInfo.fieldType;
    }

    protected Object parseRest(DefaultJSONParser parser, Type type, Object fieldName, Object instance, int features) {
        return parseRest(parser, type, fieldName, instance, features, new int[0]);
    }

    protected Object parseRest(DefaultJSONParser parser
            , Type type
            , Object fieldName
            , Object instance
            , int features
            , int[] setFlags) {
        Object value = deserialze(parser, type, fieldName, instance, features, setFlags);

        return value;
    }
    
    protected JavaBeanDeserializer getSeeAlso(ParserConfig config, JavaBeanInfo beanInfo, String typeName) {
        if (beanInfo.jsonType == null) {
            return null;
        }
        
        for (Class<?> seeAlsoClass : beanInfo.jsonType.seeAlso()) {
            ObjectDeserializer seeAlsoDeser = config.getDeserializer(seeAlsoClass);
            if (seeAlsoDeser instanceof JavaBeanDeserializer) {
                JavaBeanDeserializer seeAlsoJavaBeanDeser = (JavaBeanDeserializer) seeAlsoDeser;

                JavaBeanInfo subBeanInfo = seeAlsoJavaBeanDeser.beanInfo;
                if (subBeanInfo.typeName.equals(typeName)) {
                    return seeAlsoJavaBeanDeser;
                }
                
                JavaBeanDeserializer subSeeAlso = getSeeAlso(config, subBeanInfo, typeName);
                if (subSeeAlso != null) {
                    return subSeeAlso;
                }
            }
        }

        return null;
    }
    
    @SuppressWarnings({ "unchecked", "rawtypes" })
    protected static void parseArray(Collection collection, //
                              ObjectDeserializer deser, //
                              DefaultJSONParser parser, //
                              Type type, //
                              Object fieldName) {

        final JSONLexerBase lexer = (JSONLexerBase) parser.lexer;
        int token = lexer.token();
        if (token == JSONToken.NULL) {
            lexer.nextToken(JSONToken.COMMA);
            token = lexer.token();
            return;
        }

        if (token != JSONToken.LBRACKET) {
            parser.throwException(token);
        }
        char ch = lexer.getCurrent();
        if (ch == '[') {
            lexer.next();
            lexer.setToken(JSONToken.LBRACKET);
        } else {
            lexer.nextToken(JSONToken.LBRACKET);
        }
        
        if (lexer.token() == JSONToken.RBRACKET) {
            lexer.nextToken();
            return;
        }

        int index = 0;
        for (;;) {
            Object item = deser.deserialze(parser, type, index);
            collection.add(item);
            index++;
            if (lexer.token() == JSONToken.COMMA) {
                ch = lexer.getCurrent();
                if (ch == '[') {
                    lexer.next();
                    lexer.setToken(JSONToken.LBRACKET);
                } else {
                    lexer.nextToken(JSONToken.LBRACKET);
                }
            } else {
                break;
            }
        }
        
        token = lexer.token();
        if (token != JSONToken.RBRACKET) {
            parser.throwException(token);
        }
        
        ch = lexer.getCurrent();
        if (ch == ',') {
            lexer.next();
            lexer.setToken(JSONToken.COMMA);
        } else {
            lexer.nextToken(JSONToken.COMMA);
        }
//        parser.accept(JSONToken.RBRACKET, JSONToken.COMMA);
    }
    
}
