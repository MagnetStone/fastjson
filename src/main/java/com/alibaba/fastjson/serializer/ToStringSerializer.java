package com.alibaba.fastjson.serializer;

import java.io.IOException;
import java.lang.reflect.Type;

public class ToStringSerializer implements ObjectSerializer {

    public static final ToStringSerializer instance = new ToStringSerializer();

    @Override
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

}
