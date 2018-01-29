/*
 * Copyright 1999-2017 Alibaba Group.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.alibaba.fastjson.serializer;

import com.alibaba.fastjson.util.TypeUtils;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.util.List;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public final class ListSerializer implements ObjectSerializer {

    public static final ListSerializer instance = new ListSerializer();

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

}
