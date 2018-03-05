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

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.concurrent.atomic.AtomicBoolean;

import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;
import com.alibaba.fastjson.util.TypeUtils;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class BooleanCodec implements ObjectSerializer, ObjectDeserializer {

    public final static BooleanCodec instance = new BooleanCodec();

    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features) throws IOException {
        SerializeWriter out = serializer.out;

        /** 当前object是boolean值, 如果为null,
         *  并且序列化开启WriteNullBooleanAsFalse特性, 输出false
         */
        Boolean value = (Boolean) object;
        if (value == null) {
            out.writeNull(SerializerFeature.WriteNullBooleanAsFalse);
            return;
        }

        if (value.booleanValue()) {
            out.write("true");
        } else {
            out.write("false");
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
        final JSONLexer lexer = parser.lexer;

        Boolean boolObj;

        try {
            /** 遇到true类型的token，预读下一个token */
            if (lexer.token() == JSONToken.TRUE) {
                lexer.nextToken(JSONToken.COMMA);
                boolObj = Boolean.TRUE;
                /** 遇到false类型的token，预读下一个token */
            } else if (lexer.token() == JSONToken.FALSE) {
                lexer.nextToken(JSONToken.COMMA);
                boolObj = Boolean.FALSE;
            } else if (lexer.token() == JSONToken.LITERAL_INT) {
                /** 遇到整数类型的token，预读下一个token */
                int intValue = lexer.intValue();
                lexer.nextToken(JSONToken.COMMA);

                /** 1代表true，其他情况false */
                if (intValue == 1) {
                    boolObj = Boolean.TRUE;
                } else {
                    boolObj = Boolean.FALSE;
                }
            } else {
                Object value = parser.parse();

                if (value == null) {
                    return null;
                }

                /** 处理其他情况，比如Y,T代表true */
                boolObj = TypeUtils.castToBoolean(value);
            }
        } catch (Exception ex) {
            throw new JSONException("parseBoolean error, field : " + fieldName, ex);
        }

        /** 如果是原子类型 */
        if (clazz == AtomicBoolean.class) {
            return (T) new AtomicBoolean(boolObj.booleanValue());
        }

        return (T) boolObj;
    }

    public int getFastMatchToken() {
        return JSONToken.TRUE;
    }
}
