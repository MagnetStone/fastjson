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

import com.alibaba.fastjson.parser.DefaultJSONParser;
import com.alibaba.fastjson.parser.JSONLexer;
import com.alibaba.fastjson.parser.JSONToken;
import com.alibaba.fastjson.parser.deserializer.ObjectDeserializer;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public class StringCodec implements ObjectSerializer, ObjectDeserializer {

    public static StringCodec instance = new StringCodec();

    public void write(JSONSerializer serializer, Object object, Object fieldName, Type fieldType, int features)
                                                                                                               throws IOException {
        write(serializer, (String) object);
    }

    public void write(JSONSerializer serializer, String value) {
        SerializeWriter out = serializer.out;

        /** 当前object是string值, 如果为null,
         *  并且序列化开启WriteNullStringAsEmpty特性, 输出空串""
         */
        if (value == null) {
            out.writeNull(SerializerFeature.WriteNullStringAsEmpty);
            return;
        }

        out.writeString(value);
    }

    @SuppressWarnings("unchecked")
    public <T> T deserialze(DefaultJSONParser parser, Type clazz, Object fieldName) {
        if (clazz == StringBuffer.class) {
            /** 将解析的字符序列转换成StringBuffer */
            final JSONLexer lexer = parser.lexer;
            if (lexer.token() == JSONToken.LITERAL_STRING) {
                /** 字符串字面量，预读下一个token */
                String val = lexer.stringVal();
                lexer.nextToken(JSONToken.COMMA);

                return (T) new StringBuffer(val);
            }

            Object value = parser.parse();

            if (value == null) {
                return null;
            }

            return (T) new StringBuffer(value.toString());
        }

        if (clazz == StringBuilder.class) {
            /** 将解析的字符序列转换成StringBuilder */
            final JSONLexer lexer = parser.lexer;
            if (lexer.token() == JSONToken.LITERAL_STRING) {
                String val = lexer.stringVal();
                /** 字符串字面量，预读下一个token */
                lexer.nextToken(JSONToken.COMMA);

                return (T) new StringBuilder(val);
            }

            Object value = parser.parse();

            if (value == null) {
                return null;
            }

            return (T) new StringBuilder(value.toString());
        }

        return (T) deserialze(parser);
    }

    @SuppressWarnings("unchecked")
    public static <T> T deserialze(DefaultJSONParser parser) {
        final JSONLexer lexer = parser.getLexer();
        if (lexer.token() == JSONToken.LITERAL_STRING) {
            /** 字符串字面量，预读下一个token */
            String val = lexer.stringVal();
            lexer.nextToken(JSONToken.COMMA);
            return (T) val;
        }

        if (lexer.token() == JSONToken.LITERAL_INT) {
            /** 整型字面量，预读下一个token */
            String val = lexer.numberString();
            lexer.nextToken(JSONToken.COMMA);
            return (T) val;
        }

        Object value = parser.parse();

        if (value == null) {
            return null;
        }

        return (T) value.toString();
    }

    public int getFastMatchToken() {
        return JSONToken.LITERAL_STRING;
    }
}
