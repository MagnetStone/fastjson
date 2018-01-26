package com.alibaba.fastjson.serializer;

import java.io.IOException;
import java.lang.reflect.Type;

public class AppendableSerializer implements ObjectSerializer {

    public final static AppendableSerializer instance = new AppendableSerializer();

    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {

        /** 当前object实现了Appendable接口, 如果为null,
         *  并且序列化开启WriteNullStringAsEmpty特性, 输出空串""
         */
        if (object == null) {
            SerializeWriter out = serializer.out;
            out.writeNull(SerializerFeature.WriteNullStringAsEmpty);
            return;
        }

        /** 输出对象toString结果作为json串 */
        serializer.write(object.toString());
    }

}
