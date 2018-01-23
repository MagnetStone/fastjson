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

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONException;
import com.alibaba.fastjson.util.IOUtils;

import java.io.IOException;
import java.io.OutputStream;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;

import static com.alibaba.fastjson.util.IOUtils.replaceChars;

/**
 * @author wenshao[szujobs@hotmail.com]
 */
public final class SerializeWriter extends Writer {

    /** 字符类型buffer */
    private final static ThreadLocal<char[]> bufLocal      = new ThreadLocal<char[]>();
    /** 字节类型buffer */
    private final static ThreadLocal<byte[]> bytesBufLocal = new ThreadLocal<byte[]>();

    /** 存储序列化结果buffer */
    protected char                           buf[];

    /** buffer中包含的字符数 */
    protected int                            count;

    /** 序列化的特性，比如写枚举按照名字还是枚举值 */
    protected int                            features;

    /** 序列化输出器 */
    private final Writer                     writer;

    /** 是否使用单引号输出json */
    protected boolean                        useSingleQuotes;
    /** 输出字段是否追加 "和：字符 */
    protected boolean                        quoteFieldNames;
    /** 是否对字段排序 */
    protected boolean                        sortField;
    /** 禁用字段循环引用探测 */
    protected boolean                        disableCircularReferenceDetect;
    protected boolean                        beanToArray;
    /** 按照toString方式获取对象字面值 */
    protected boolean                        writeNonStringValueAsString;
    /** 如果字段默认值不输出，比如原型int，默认值0不输出 */
    protected boolean                        notWriteDefaultValue;
    /** 序列化枚举时使用枚举name */
    protected boolean                        writeEnumUsingName;
    /** 序列化枚举时使用枚举toString值 */
    protected boolean                        writeEnumUsingToString;
    protected boolean                        writeDirect;
    /** key分隔符，默认单引号是'，双引号是" */
    protected char                           keySeperator;

    protected int                            maxBufSize = -1;

    protected boolean                        browserSecure;
    protected long                           sepcialBits;

    public SerializeWriter(){
        this((Writer) null);
    }

    public SerializeWriter(Writer writer){
        this(writer, JSON.DEFAULT_GENERATE_FEATURE, SerializerFeature.EMPTY);
    }

    public SerializeWriter(SerializerFeature... features){
        this(null, features);
    }

    public SerializeWriter(Writer writer, SerializerFeature... features){
        this(writer, 0, features);
    }

    /**
     * @since 1.2.9
     * @param writer
     * @param defaultFeatures
     * @param features
     */
    public SerializeWriter(Writer writer, int defaultFeatures, SerializerFeature... features){
        this.writer = writer;

        buf = bufLocal.get();

        if (buf != null) {
            bufLocal.set(null);
        } else {
            buf = new char[2048];
        }

        int featuresValue = defaultFeatures;
        for (SerializerFeature feature : features) {
            featuresValue |= feature.getMask();
        }
        this.features = featuresValue;

        computeFeatures();
    }

    public int getMaxBufSize() {
        return maxBufSize;
    }

    public void setMaxBufSize(int maxBufSize) {
        if (maxBufSize < this.buf.length) {
            throw new JSONException("must > " + buf.length);
        }

        this.maxBufSize = maxBufSize;
    }

    public int getBufferLength() {
        return this.buf.length;
    }

    public SerializeWriter(int initialSize){
        this(null, initialSize);
    }

    public SerializeWriter(Writer writer, int initialSize){
        this.writer = writer;

        if (initialSize <= 0) {
            throw new IllegalArgumentException("Negative initial size: " + initialSize);
        }
        buf = new char[initialSize];

        computeFeatures();
    }

    public void config(SerializerFeature feature, boolean state) {
        if (state) {
            features |= feature.getMask();
            // 由于枚举序列化特性WriteEnumUsingToString和WriteEnumUsingName不能共存，需要检查
            if (feature == SerializerFeature.WriteEnumUsingToString) {
                features &= ~SerializerFeature.WriteEnumUsingName.getMask();
            } else if (feature == SerializerFeature.WriteEnumUsingName) {
                features &= ~SerializerFeature.WriteEnumUsingToString.getMask();
            }
        } else {
            features &= ~feature.getMask();
        }

        computeFeatures();
    }

    final static int nonDirectFeatures = 0 //
            | SerializerFeature.UseSingleQuotes.mask //
            | SerializerFeature.BrowserCompatible.mask //
            | SerializerFeature.PrettyFormat.mask //
            | SerializerFeature.WriteEnumUsingToString.mask
            | SerializerFeature.WriteNonStringValueAsString.mask
            | SerializerFeature.WriteSlashAsSpecial.mask
            | SerializerFeature.IgnoreErrorGetter.mask
            | SerializerFeature.WriteClassName.mask
            | SerializerFeature.NotWriteDefaultValue.mask
            ;
    protected void computeFeatures() {
        quoteFieldNames = (this.features & SerializerFeature.QuoteFieldNames.mask) != 0;
        useSingleQuotes = (this.features & SerializerFeature.UseSingleQuotes.mask) != 0;
        sortField = (this.features & SerializerFeature.SortField.mask) != 0;
        disableCircularReferenceDetect = (this.features & SerializerFeature.DisableCircularReferenceDetect.mask) != 0;
        beanToArray = (this.features & SerializerFeature.BeanToArray.mask) != 0;
        writeNonStringValueAsString = (this.features & SerializerFeature.WriteNonStringValueAsString.mask) != 0;
        notWriteDefaultValue = (this.features & SerializerFeature.NotWriteDefaultValue.mask) != 0;
        writeEnumUsingName = (this.features & SerializerFeature.WriteEnumUsingName.mask) != 0;
        writeEnumUsingToString = (this.features & SerializerFeature.WriteEnumUsingToString.mask) != 0;

        writeDirect = quoteFieldNames //
                      && (this.features & nonDirectFeatures) == 0 //
                      && (beanToArray || writeEnumUsingName)
                      ;

        keySeperator = useSingleQuotes ? '\'' : '"';

        browserSecure = (this.features & SerializerFeature.BrowserSecure.mask) != 0;

        final long S0 = 0x4FFFFFFFFL, S1 = 0x8004FFFFFFFFL, S2 = 0x50000304ffffffffL;
//        long s = 0;
//        for (int i = 0; i <= 31; ++i) {
//            s |= (1L << i);
//        }
//        s |= (1L << '"');
//
//        //S0 = s;
//        //S1 = s | (1L << '/');
//
//        s |= (1L << '('); // 41
//        s |= (1L << ')'); // 42
//        s |= (1L << '<'); // 60
//        s |= (1L << '>'); // 62
//        S2 = s;
        sepcialBits = browserSecure
                ? S2
                : (features & SerializerFeature.WriteSlashAsSpecial.mask) != 0 ? S1 : S0;
    }

    public boolean isSortField() {
        return sortField;
    }

    public boolean isNotWriteDefaultValue() {
        return notWriteDefaultValue;
    }

    public boolean isEnabled(SerializerFeature feature) {
        return (this.features & feature.mask) != 0;
    }
    
    public boolean isEnabled(int feature) {
        return (this.features & feature) != 0;
    }

    /**
     * Writes a character to the buffer.
     */
    public void write(int c) {
        int newcount = count + 1;
        /** 如果当前存储空间不够 */
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                /** 强制流输出并刷新缓冲区 */
                flush();
                newcount = 1;
            }
        }
        /** 存储单字符到buffer并更新计数 */
        buf[count] = (char) c;
        count = newcount;
    }

    /**
     * Writes characters to the buffer.
     * 
     * @param c the data to be written
     * @param off the start offset in the data
     * @param len the number of chars that are written
     */
    public void write(char c[], int off, int len) {
        if (off < 0 //
            || off > c.length //
            || len < 0 //
            || off + len > c.length //
            || off + len < 0) {
            throw new IndexOutOfBoundsException();
        } else if (len == 0) {
            return;
        }

        /** 计算总共字符串长度 */
        int newcount = count + len;
        /** 如果当前存储空间不够 */
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                /**
                 * 如果字符数组c超过缓冲区大小, 进行循环拷贝
                 */
                do {
                    /** 计算当前buffer剩余容纳字符数 */
                    int rest = buf.length - count;
                    /** c[off, off + rest) 拷贝到buf[count, ...]中*/
                    System.arraycopy(c, off, buf, count, rest);
                    count = buf.length;
                    /** 强制刷新输出流，会重置count = 0 */
                    flush();
                    /** 计算剩余需要拷贝的字符数量 */
                    len -= rest;
                    /** 剩余要拷贝字符在c中偏移量(索引) */
                    off += rest;
                } while (len > buf.length);
                newcount = len;
            }
        }
        System.arraycopy(c, off, buf, count, len);
        count = newcount;
    }

    public void expandCapacity(int minimumCapacity) {
        if (maxBufSize != -1 && minimumCapacity >= maxBufSize) {
            throw new JSONException("serialize exceeded MAX_OUTPUT_LENGTH=" + maxBufSize + ", minimumCapacity=" + minimumCapacity);
        }

        int newCapacity = buf.length + (buf.length >> 1) + 1;

        if (newCapacity < minimumCapacity) {
            newCapacity = minimumCapacity;
        }
        char newValue[] = new char[newCapacity];
        System.arraycopy(buf, 0, newValue, 0, count);
        buf = newValue;
    }
    
    public SerializeWriter append(CharSequence csq) {
        String s = (csq == null ? "null" : csq.toString());
        write(s, 0, s.length());
        return this;
    }

    public SerializeWriter append(CharSequence csq, int start, int end) {
        String s = (csq == null ? "null" : csq).subSequence(start, end).toString();
        write(s, 0, s.length());
        return this;
    }

    public SerializeWriter append(char c) {
        write(c);
        return this;
    }

    /**
     * Write a portion of a string to the buffer.
     * 
     * @param str String to be written from
     * @param off Offset from which to start reading characters
     * @param len Number of characters to be written
     */
    public void write(String str, int off, int len) {
        /** 计算总共字符串长度 */
        int newcount = count + len;
        /** 如果当前存储空间不够 */
        if (newcount > buf.length) {
            if (writer == null) {
                expandCapacity(newcount);
            } else {
                /**
                 * 如果字符串str超过缓冲区大小, 进行循环拷贝
                 */
                do {
                    /** 计算当前buffer剩余容纳字符数 */
                    int rest = buf.length - count;
                    /** 将字符串str[off, off + rest) 拷贝到buf[count, ...]中*/
                    str.getChars(off, off + rest, buf, count);
                    count = buf.length;
                    /** 强制刷新输出流，会重置count = 0 */
                    flush();
                    /** 计算剩余需要拷贝的字符数量 */
                    len -= rest;
                    /** 剩余要拷贝字符在str中偏移量(索引) */
                    off += rest;
                } while (len > buf.length);
                newcount = len;
            }
        }
        /** 存储空间充足，直接将str[off, off + len) 拷贝到buf[count, ...]中*/
        str.getChars(off, off + len, buf, count);
        count = newcount;
    }

    /**
     * Writes the contents of the buffer to another character stream.
     * 
     * @param out the output stream to write to
     * @throws IOException If an I/O error occurs.
     */
    public void writeTo(Writer out) throws IOException {
        if (this.writer != null) {
            throw new UnsupportedOperationException("writer not null");
        }
        out.write(buf, 0, count);
    }

    public void writeTo(OutputStream out, String charsetName) throws IOException {
        writeTo(out, Charset.forName(charsetName));
    }
    
    public void writeTo(OutputStream out, Charset charset) throws IOException {
        writeToEx(out, charset);
    }

    public int writeToEx(OutputStream out, Charset charset) throws IOException {
        if (this.writer != null) {
            throw new UnsupportedOperationException("writer not null");
        }
        
        if (charset == IOUtils.UTF8) {
            return encodeToUTF8(out);
        } else {
            byte[] bytes = new String(buf, 0, count).getBytes(charset);
            out.write(bytes);
            return bytes.length;
        }
    }

    /**
     * Returns a copy of the input data.
     * 
     * @return an array of chars copied from the input data.
     */
    public char[] toCharArray() {
        if (this.writer != null) {
            throw new UnsupportedOperationException("writer not null");
        }

        char[] newValue = new char[count];
        System.arraycopy(buf, 0, newValue, 0, count);
        return newValue;
    }
    
    /**
     * only for springwebsocket
     * @return
     */
    public char[] toCharArrayForSpringWebSocket() {
        if (this.writer != null) {
            throw new UnsupportedOperationException("writer not null");
        }

        char[] newValue = new char[count - 2];
        System.arraycopy(buf, 1, newValue, 0, count - 2);
        return newValue;
    }

    public byte[] toBytes(String charsetName) {
        return toBytes(charsetName == null || "UTF-8".equals(charsetName) //
            ? IOUtils.UTF8 //
            : Charset.forName(charsetName));
    }

    public byte[] toBytes(Charset charset) {
        if (this.writer != null) {
            throw new UnsupportedOperationException("writer not null");
        }
        
        if (charset == IOUtils.UTF8) {
            return encodeToUTF8Bytes();
        } else {
            return new String(buf, 0, count).getBytes(charset);
        }
    }

    private int encodeToUTF8(OutputStream out) throws IOException {

        int bytesLength = (int) (count * (double) 3);
        byte[] bytes = bytesBufLocal.get();

        if (bytes == null) {
            bytes = new byte[1024 * 8];
            bytesBufLocal.set(bytes);
        }

        if (bytes.length < bytesLength) {
            bytes = new byte[bytesLength];
        }

        int position = IOUtils.encodeUTF8(buf, 0, count, bytes);
        out.write(bytes, 0, position);
        return position;
    }
    
    private byte[] encodeToUTF8Bytes() {
        int bytesLength = (int) (count * (double) 3);
        byte[] bytes = bytesBufLocal.get();

        if (bytes == null) {
            bytes = new byte[1024 * 8];
            bytesBufLocal.set(bytes);
        }

        if (bytes.length < bytesLength) {
            bytes = new byte[bytesLength];
        }

        int position = IOUtils.encodeUTF8(buf, 0, count, bytes);
        byte[] copy = new byte[position];
        System.arraycopy(bytes, 0, copy, 0, position);
        return copy;
    }
    
    public int size() {
        return count;
    }

    public String toString() {
        return new String(buf, 0, count);
    }

    /**
     * Close the stream. This method does not release the buffer, since its contents might still be required. Note:
     * Invoking this method in this class will have no effect.
     */
    public void close() {
        if (writer != null && count > 0) {
            flush();
        }
        if (buf.length <= 1024 * 128) {
            bufLocal.set(buf);
        }

        this.buf = null;
    }

    public void write(String text) {
        if (text == null) {
            writeNull();
            return;
        }

        write(text, 0, text.length());
    }

    public void writeInt(int i) {
        /** 如果是整数最小值，调用字符串函数输出到缓冲区*/
        if (i == Integer.MIN_VALUE) {
            write("-2147483648");
            return;
        }

        /** 根据数字判断占用的位数，负数会多一位用于存储字符`-` */
        int size = (i < 0) ? IOUtils.stringSize(-i) + 1 : IOUtils.stringSize(i);

        int newcount = count + size;
        /** 如果当前存储空间不够 */
        if (newcount > buf.length) {
            if (writer == null) {
                /** 扩容到为原有buf容量1.5倍+1, copy原有buf的字符*/
                expandCapacity(newcount);
            } else {
                char[] chars = new char[size];
                /** 将整数i转换成单字符并存储到chars数组 */
                IOUtils.getChars(i, size, chars);
                /** 将chars字符数组内容写到buffer中*/
                write(chars, 0, chars.length);
                return;
            }
        }

        /** 如果buffer空间够，直接将字符写到buffer中 */
        IOUtils.getChars(i, newcount, buf);
        /** 重新计数buffer中字符数 */
        count = newcount;
    }

    public void writeByteArray(byte[] bytes) {
        if (isEnabled(SerializerFeature.WriteClassName.mask)) {
            writeHex(bytes);
            return;
        }

        int bytesLen = bytes.length;
        final char quote = useSingleQuotes ? '\'' : '"';
        if (bytesLen == 0) {
            String emptyString = useSingleQuotes ? "''" : "\"\"";
            write(emptyString);
            return;
        }

        final char[] CA = IOUtils.CA;

        int eLen = (bytesLen / 3) * 3; // Length of even 24-bits.
        int charsLen = ((bytesLen - 1) / 3 + 1) << 2; // base64 character count
        // char[] chars = new char[charsLen];
        int offset = count;
        int newcount = count + charsLen + 2;
        if (newcount > buf.length) {
            if (writer != null) {
                write(quote);

                for (int s = 0; s < eLen;) {
                    // Copy next three bytes into lower 24 bits of int, paying attension to sign.
                    int i = (bytes[s++] & 0xff) << 16 | (bytes[s++] & 0xff) << 8 | (bytes[s++] & 0xff);

                    // Encode the int into four chars
                    write(CA[(i >>> 18) & 0x3f]);
                    write(CA[(i >>> 12) & 0x3f]);
                    write(CA[(i >>> 6) & 0x3f]);
                    write(CA[i & 0x3f]);
                }

                // Pad and encode last bits if source isn't even 24 bits.
                int left = bytesLen - eLen; // 0 - 2.
                if (left > 0) {
                    // Prepare the int
                    int i = ((bytes[eLen] & 0xff) << 10) | (left == 2 ? ((bytes[bytesLen - 1] & 0xff) << 2) : 0);

                    // Set last four chars
                    write(CA[i >> 12]);
                    write(CA[(i >>> 6) & 0x3f]);
                    write(left == 2 ? CA[i & 0x3f] : '=');
                    write('=');
                }

                write(quote);
                return;
            }
            expandCapacity(newcount);
        }
        count = newcount;
        buf[offset++] = quote;

        // Encode even 24-bits
        for (int s = 0, d = offset; s < eLen;) {
            // Copy next three bytes into lower 24 bits of int, paying attension to sign.
            int i = (bytes[s++] & 0xff) << 16 | (bytes[s++] & 0xff) << 8 | (bytes[s++] & 0xff);

            // Encode the int into four chars
            buf[d++] = CA[(i >>> 18) & 0x3f];
            buf[d++] = CA[(i >>> 12) & 0x3f];
            buf[d++] = CA[(i >>> 6) & 0x3f];
            buf[d++] = CA[i & 0x3f];
        }

        // Pad and encode last bits if source isn't even 24 bits.
        int left = bytesLen - eLen; // 0 - 2.
        if (left > 0) {
            // Prepare the int
            int i = ((bytes[eLen] & 0xff) << 10) | (left == 2 ? ((bytes[bytesLen - 1] & 0xff) << 2) : 0);

            // Set last four chars
            buf[newcount - 5] = CA[i >> 12];
            buf[newcount - 4] = CA[(i >>> 6) & 0x3f];
            buf[newcount - 3] = left == 2 ? CA[i & 0x3f] : '=';
            buf[newcount - 2] = '=';
        }
        buf[newcount - 1] = quote;
    }

    public void writeHex(byte[] bytes) {
        int newcount = count + bytes.length * 2 + 3;
        if (newcount > buf.length) {
            if (writer != null) {
                char[] chars = new char[bytes.length + 3];
                int pos = 0;
                chars[pos++] = 'x';
                chars[pos++] = '\'';

                for (int i = 0; i < bytes.length; ++i) {
                    byte b = bytes[i];

                    int a = b & 0xFF;
                    int b0 = a >> 4;
                    int b1 = a & 0xf;

                    chars[pos++] = (char) (b0 + (b0 < 10 ? 48 : 55));
                    chars[pos++] = (char) (b1 + (b1 < 10 ? 48 : 55));
                }
                chars[pos++] = '\'';
                try {
                    writer.write(chars);
                } catch (IOException ex) {
                    throw new JSONException("writeBytes error.", ex);
                }
                return;
            }
            expandCapacity(newcount);
        }

        buf[count++] = 'x';
        buf[count++] = '\'';

        for (int i = 0; i < bytes.length; ++i) {
            byte b = bytes[i];

            int a = b & 0xFF;
            int b0 = a >> 4;
            int b1 = a & 0xf;

            buf[count++] = (char) (b0 + (b0 < 10 ? 48 : 55));
            buf[count++] = (char) (b1 + (b1 < 10 ? 48 : 55));
        }
        buf[count++] = '\'';
    }

    public void writeFloat(float value, boolean checkWriteClassName) {
        /** 如果value不合法或者是无穷数，调用writeNull */
        if (Float.isNaN(value) //
                || Float.isInfinite(value)) {
            writeNull();
        } else {
            /** 将高精度float转换为字符串 */
            String floatText= Float.toString(value);
            /** 启动WriteNullNumberAsZero特性，会将结尾.0去除 */
            if (isEnabled(SerializerFeature.WriteNullNumberAsZero) && floatText.endsWith(".0")) {
                floatText = floatText.substring(0, floatText.length() - 2);
            }
            write(floatText);

            /** 如果开启序列化WriteClassName特性，输出float类型 */
            if (checkWriteClassName && isEnabled(SerializerFeature.WriteClassName)) {
                write('F');
            }
        }
    }

    public void writeDouble(double doubleValue, boolean checkWriteClassName) {
        /** 如果doubleValue不合法或者是无穷数，调用writeNull */
        if (Double.isNaN(doubleValue)
                || Double.isInfinite(doubleValue)) {
            writeNull();
        } else {
            /** 将高精度double转换为字符串 */
            String doubleText = Double.toString(doubleValue);
            /** 启动WriteNullNumberAsZero特性，会将结尾.0去除 */
            if (isEnabled(SerializerFeature.WriteNullNumberAsZero) && doubleText.endsWith(".0")) {
                doubleText = doubleText.substring(0, doubleText.length() - 2);
            }

            /** 调用字符串输出方法 */
            write(doubleText);

            /** 如果开启序列化WriteClassName特性，输出Double类型 */
            if (checkWriteClassName && isEnabled(SerializerFeature.WriteClassName)) {
                write('D');
            }
        }
    }

    public void writeEnum(Enum<?> value) {
        if (value == null) {
            /** 如果枚举value为空，调用writeNull输出 */
            writeNull();
            return;
        }
        
        String strVal = null;
        /** 如果开启序列化输出枚举名字作为属性值 */
        if (writeEnumUsingName && !writeEnumUsingToString) {
            strVal = value.name();
        } else if (writeEnumUsingToString) {
            /** 采用枚举默认toString方法作为属性值 */
            strVal = value.toString();;
        }

        if (strVal != null) {
            /** 如果开启引号特性，输出json包含引号的字符串 */
            char quote = isEnabled(SerializerFeature.UseSingleQuotes) ? '\'' : '"';
            write(quote);
            write(strVal);
            write(quote);
        } else {
            /** 输出枚举所在的索引号 */
            writeInt(value.ordinal());
        }
    }

    public void writeLong(long i) {
        boolean needQuotationMark = isEnabled(SerializerFeature.BrowserCompatible) //
                                    && (!isEnabled(SerializerFeature.WriteClassName)) //
                                    && (i > 9007199254740991L || i < -9007199254740991L);

        if (i == Long.MIN_VALUE) {
            if (needQuotationMark) write("\"-9223372036854775808\"");
            /** 如果是长整数最小值，调用字符串函数输出到缓冲区*/
            else write("-9223372036854775808");
            return;
        }

        /** 根据数字判断占用的位数，负数会多一位用于存储字符`-` */
        int size = (i < 0) ? IOUtils.stringSize(-i) + 1 : IOUtils.stringSize(i);

        int newcount = count + size;
        if (needQuotationMark) newcount += 2;
        /** 如果当前存储空间不够 */
        if (newcount > buf.length) {
            if (writer == null) {
                /** 扩容到为原有buf容量1.5倍+1, copy原有buf的字符*/
                expandCapacity(newcount);
            } else {
                char[] chars = new char[size];
                /** 将长整数i转换成单字符并存储到chars数组 */
                IOUtils.getChars(i, size, chars);
                if (needQuotationMark) {
                    write('"');
                    write(chars, 0, chars.length);
                    write('"');
                } else {
                    write(chars, 0, chars.length);
                }
                return;
            }
        }

        /** 添加引号 */
        if (needQuotationMark) {
            buf[count] = '"';
            IOUtils.getChars(i, newcount - 1, buf);
            buf[newcount - 1] = '"';
        } else {
            IOUtils.getChars(i, newcount, buf);
        }

        count = newcount;
    }

    public void writeNull() {
        /** 调用输出字符串null */
        write("null");
    }
    
    public void writeNull(SerializerFeature feature) {
        writeNull(0, feature.mask);
    }
    
    public void writeNull(int beanFeatures , int feature) {
        if ((beanFeatures & feature) == 0 //
            && (this.features & feature) == 0) {
            writeNull();
            return;
        }
        
        if (feature == SerializerFeature.WriteNullListAsEmpty.mask) {
            write("[]");
        } else if (feature == SerializerFeature.WriteNullStringAsEmpty.mask) {
            writeString("");
        } else if (feature == SerializerFeature.WriteNullBooleanAsFalse.mask) {
            write("false");
        } else if (feature == SerializerFeature.WriteNullNumberAsZero.mask) {
            write('0');
        } else {
            writeNull();
        }
    }
    
    public void writeStringWithDoubleQuote(String text, final char seperator) {
        if (text == null) {
            /** 如果字符换为空，输出null字符串 */
            writeNull();
            if (seperator != 0) {
                /** 如果分隔符不为空白字符' '，输出分隔符 */
                write(seperator);
            }
            return;
        }

        int len = text.length();
        int newcount = count + len + 2;
        if (seperator != 0) {
            newcount++;
        }

        /** 如果当前存储空间不够 */
        if (newcount > buf.length) {
            if (writer != null) {
                /** 写双引号字符 */
                write('"');

                for (int i = 0; i < text.length(); ++i) {
                    /** 循环提取字符串中字符 */
                    char ch = text.charAt(i);

                    if (isEnabled(SerializerFeature.BrowserSecure)) {
                       if (ch == '(' || ch == ')' || ch == '<' || ch == '>') {
                            /** ascii转换成native编码 */
                            write('\\');
                            write('u');
                            write(IOUtils.DIGITS[(ch >>> 12) & 15]);
                            write(IOUtils.DIGITS[(ch >>> 8) & 15]);
                            write(IOUtils.DIGITS[(ch >>> 4) & 15]);
                            write(IOUtils.DIGITS[ch & 15]);
                            continue;
                        }
                    }

                    if (isEnabled(SerializerFeature.BrowserCompatible)) {
                        if (ch == '\b'      //  退格
                            || ch == '\f'   //  分页
                            || ch == '\n'   //  换行
                            || ch == '\r'   //  回车
                            || ch == '\t'   //  tab
                            || ch == '"'    //  双引号
                            || ch == '/'    //  左反斜杠
                            || ch == '\\') {//  单引号
                            /** 输出转义字符 + 字符ascii码 */
                            write('\\'); //  右反斜杠
                            write(replaceChars[(int) ch]);
                            continue;
                        }

                        if (ch < 32) {
                            /** ascii转换成native编码 */
                            write('\\');
                            write('u');
                            write('0');
                            write('0');
                            write(IOUtils.ASCII_CHARS[ch * 2]);
                            write(IOUtils.ASCII_CHARS[ch * 2 + 1]);
                            continue;
                        }

                        if (ch >= 127) {
                            /** ascii转换成native编码 */
                            write('\\');
                            write('u');
                            write(IOUtils.DIGITS[(ch >>> 12) & 15]);
                            write(IOUtils.DIGITS[(ch >>> 8) & 15]);
                            write(IOUtils.DIGITS[(ch >>> 4) & 15]);
                            write(IOUtils.DIGITS[ch & 15]);
                            continue;
                        }
                    } else {
                        /** ascii转换成native编码 */
                        if (ch < IOUtils.specicalFlags_doubleQuotes.length
                            && IOUtils.specicalFlags_doubleQuotes[ch] != 0 //
                            || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                            write('\\');
                            if (IOUtils.specicalFlags_doubleQuotes[ch] == 4) {
                                write('u');
                                write(IOUtils.DIGITS[ch >>> 12 & 15]);
                                write(IOUtils.DIGITS[ch >>> 8 & 15]);
                                write(IOUtils.DIGITS[ch >>> 4 & 15]);
                                write(IOUtils.DIGITS[ch & 15]);
                            } else {
                                write(IOUtils.replaceChars[ch]);
                            }
                            continue;
                        }
                    }

                    /** 非特殊字符，直接输出 */
                    write(ch);
                }

                /** 字符串结束 */
                write('"');
                if (seperator != 0) {
                    write(seperator);
                }
                return;
            }
            /** buffer容量不够并且输出器为空，触发扩容 */
            expandCapacity(newcount);
        }

        int start = count + 1;
        int end = start + len;

        buf[count] = '\"';
        /** buffer能够容纳字符串，直接拷贝text到buf缓冲数组 */
        text.getChars(0, len, buf, start);

        count = newcount;

        if (isEnabled(SerializerFeature.BrowserCompatible)) {
            int lastSpecialIndex = -1;

            for (int i = start; i < end; ++i) {
                /** 循环提取字符串中字符 */
                char ch = buf[i];

                if (ch == '"' //
                    || ch == '/' //
                    || ch == '\\') {
                    /** 记录指定字符最后出现的位置 */
                    lastSpecialIndex = i;
                    newcount += 1;
                    continue;
                }

                if (ch == '\b' //
                    || ch == '\f' //
                    || ch == '\n' //
                    || ch == '\r' //
                    || ch == '\t') {
                    /** 记录指定字符最后出现的位置 */
                    lastSpecialIndex = i;
                    newcount += 1;
                    continue;
                }

                if (ch < 32) {
                    lastSpecialIndex = i;
                    newcount += 5;
                    continue;
                }

                if (ch >= 127) {
                    lastSpecialIndex = i;
                    newcount += 5;
                    continue;
                }
            }

            /** 如果存储空间不足，触发到(1.5倍buffer大小+1) */
            if (newcount > buf.length) {
                expandCapacity(newcount);
            }
            count = newcount;

            /** 逆向从指定特殊字符开始遍历 */
            for (int i = lastSpecialIndex; i >= start; --i) {
                char ch = buf[i];

                if (ch == '\b' //
                    || ch == '\f'//
                    || ch == '\n' //
                    || ch == '\r' //
                    || ch == '\t') {
                    /** 将字符后移一位，插入转译字符\ */
                    System.arraycopy(buf, i + 1, buf, i + 2, end - i - 1);
                    buf[i] = '\\';
                    /** 将特殊字符转换成普通单字符 */
                    buf[i + 1] = replaceChars[(int) ch];
                    end += 1;
                    continue;
                }

                if (ch == '"' //
                    || ch == '/' //
                    || ch == '\\') {
                    /** 和上面处理一致，不需要单独替换成普通字符 */
                    System.arraycopy(buf, i + 1, buf, i + 2, end - i - 1);
                    buf[i] = '\\';
                    buf[i + 1] = ch;
                    end += 1;
                    continue;
                }

                if (ch < 32) {
                    System.arraycopy(buf, i + 1, buf, i + 6, end - i - 1);
                    /** ascii转换成native编码 */
                    buf[i] = '\\';
                    buf[i + 1] = 'u';
                    buf[i + 2] = '0';
                    buf[i + 3] = '0';
                    buf[i + 4] = IOUtils.ASCII_CHARS[ch * 2];
                    buf[i + 5] = IOUtils.ASCII_CHARS[ch * 2 + 1];
                    end += 5;
                    continue;
                }

                if (ch >= 127) {
                    System.arraycopy(buf, i + 1, buf, i + 6, end - i - 1);
                    /** ascii转换成native编码 */
                    buf[i] = '\\';
                    buf[i + 1] = 'u';
                    buf[i + 2] = IOUtils.DIGITS[(ch >>> 12) & 15];
                    buf[i + 3] = IOUtils.DIGITS[(ch >>> 8) & 15];
                    buf[i + 4] = IOUtils.DIGITS[(ch >>> 4) & 15];
                    buf[i + 5] = IOUtils.DIGITS[ch & 15];
                    end += 5;
                }
            }

            /** 追加引用符号 */
            if (seperator != 0) {
                buf[count - 2] = '\"';
                buf[count - 1] = seperator;
            } else {
                buf[count - 1] = '\"';
            }

            return;
        }

        int specialCount = 0;
        int lastSpecialIndex = -1;
        int firstSpecialIndex = -1;
        char lastSpecial = '\0';

        for (int i = start; i < end; ++i) {
            char ch = buf[i];

            if (ch >= ']') { //   93
                /** 特殊字符参考：http://www.mokuge.com/tool/asciito16/ */
                if (ch >= 0x7F // 127
                        && (ch == '\u2028' //
                        || ch == '\u2029'  //
                        || ch < 0xA0)) {   // 160 [空格]
                    if (firstSpecialIndex == -1) {
                        firstSpecialIndex = i;
                    }

                    specialCount++;
                    lastSpecialIndex = i;
                    lastSpecial = ch;
                    newcount += 4;
                }
                continue;
            }

            boolean special = (ch < 64 && (sepcialBits & (1L << ch)) != 0) || ch == '\\';
            if (special) {
                specialCount++;
                lastSpecialIndex = i;
                lastSpecial = ch;

                if (ch == '('
                        || ch == ')'
                        || ch == '<'
                        || ch == '>'
                        || (ch < IOUtils.specicalFlags_doubleQuotes.length //
                    && IOUtils.specicalFlags_doubleQuotes[ch] == 4) //
                ) {
                    newcount += 4;
                }

                if (firstSpecialIndex == -1) {
                    firstSpecialIndex = i;
                }
            }
        }

        if (specialCount > 0) {
            newcount += specialCount;
            /** 包含特殊字符并且buffer空间不够，触发扩容 */
            if (newcount > buf.length) {
                expandCapacity(newcount);
            }
            count = newcount;

            /** 将特殊字符转换成native编码，目的是节省存储空间*/
            if (specialCount == 1) {
                // 行分隔符
                if (lastSpecial == '\u2028') {
                    int srcPos = lastSpecialIndex + 1;
                    int destPos = lastSpecialIndex + 6;
                    int LengthOfCopy = end - lastSpecialIndex - 1;
                    System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                    buf[lastSpecialIndex] = '\\';
                    buf[++lastSpecialIndex] = 'u';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '0';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '8';
                }
                // 段落分隔符
                else if (lastSpecial == '\u2029') {
                    int srcPos = lastSpecialIndex + 1;
                    int destPos = lastSpecialIndex + 6;
                    int LengthOfCopy = end - lastSpecialIndex - 1;
                    System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                    buf[lastSpecialIndex] = '\\';
                    buf[++lastSpecialIndex] = 'u';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '0';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '9';
                } else if (lastSpecial == '(' || lastSpecial == ')' || lastSpecial == '<' || lastSpecial == '>') {
                    int srcPos = lastSpecialIndex + 1;
                    int destPos = lastSpecialIndex + 6;
                    int LengthOfCopy = end - lastSpecialIndex - 1;
                    System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                    buf[lastSpecialIndex] = '\\';
                    buf[++lastSpecialIndex] = 'u';

                    final char ch = lastSpecial;
                    buf[++lastSpecialIndex] = IOUtils.DIGITS[(ch >>> 12) & 15];
                    buf[++lastSpecialIndex] = IOUtils.DIGITS[(ch >>> 8) & 15];
                    buf[++lastSpecialIndex] = IOUtils.DIGITS[(ch >>> 4) & 15];
                    buf[++lastSpecialIndex] = IOUtils.DIGITS[ch & 15];
                } else {
                    final char ch = lastSpecial;
                    if (ch < IOUtils.specicalFlags_doubleQuotes.length //
                        && IOUtils.specicalFlags_doubleQuotes[ch] == 4) {
                        int srcPos = lastSpecialIndex + 1;
                        int destPos = lastSpecialIndex + 6;
                        int LengthOfCopy = end - lastSpecialIndex - 1;
                        System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);

                        int bufIndex = lastSpecialIndex;
                        buf[bufIndex++] = '\\';
                        buf[bufIndex++] = 'u';
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                    } else {
                        int srcPos = lastSpecialIndex + 1;
                        int destPos = lastSpecialIndex + 2;
                        int LengthOfCopy = end - lastSpecialIndex - 1;
                        System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                        buf[lastSpecialIndex] = '\\';
                        buf[++lastSpecialIndex] = replaceChars[(int) ch];
                    }
                }
            } else if (specialCount > 1) {
                int textIndex = firstSpecialIndex - start;
                int bufIndex = firstSpecialIndex;
                for (int i = textIndex; i < text.length(); ++i) {
                    char ch = text.charAt(i);

                    /** 将特殊字符转换成native编码，目的是节省存储空间*/ 
                    if (browserSecure && (ch == '('
                            || ch == ')'
                            || ch == '<'
                            || ch == '>')) {
                        buf[bufIndex++] = '\\';
                        buf[bufIndex++] = 'u';
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                        end += 5;
                    } else if (ch < IOUtils.specicalFlags_doubleQuotes.length //
                        && IOUtils.specicalFlags_doubleQuotes[ch] != 0 //
                        || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                        buf[bufIndex++] = '\\';
                        if (IOUtils.specicalFlags_doubleQuotes[ch] == 4) {
                            buf[bufIndex++] = 'u';
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                            end += 5;
                        } else {
                            buf[bufIndex++] = replaceChars[(int) ch];
                            end++;
                        }
                    } else {
                        // 行分隔符 、段落分隔符
                        if (ch == '\u2028' || ch == '\u2029') {
                            buf[bufIndex++] = '\\';
                            buf[bufIndex++] = 'u';
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                            end += 5;
                        } else {
                            buf[bufIndex++] = ch;
                        }
                    }
                }
            }
        }

        if (seperator != 0) {
            buf[count - 2] = '\"';
            buf[count - 1] = seperator;
        } else {
            buf[count - 1] = '\"';
        }
    }

    public void writeStringWithDoubleQuote(char[] text, final char seperator) {
        if (text == null) {
            writeNull();
            if (seperator != 0) {
                write(seperator);
            }
            return;
        }

        int len = text.length;
        int newcount = count + len + 2;
        if (seperator != 0) {
            newcount++;
        }

        if (newcount > buf.length) {
            if (writer != null) {
                write('"');

                for (int i = 0; i < text.length; ++i) {
                    char ch = text[i];

                    if (isEnabled(SerializerFeature.BrowserSecure)) {
                        if (ch == '(' || ch == ')' || ch == '<' || ch == '>') {
                            write('\\');
                            write('u');
                            write(IOUtils.DIGITS[(ch >>> 12) & 15]);
                            write(IOUtils.DIGITS[(ch >>> 8) & 15]);
                            write(IOUtils.DIGITS[(ch >>> 4) & 15]);
                            write(IOUtils.DIGITS[ch & 15]);
                            continue;
                        }
                    }

                    if (isEnabled(SerializerFeature.BrowserCompatible)) {
                        if (ch == '\b' //
                                || ch == '\f' //
                                || ch == '\n' //
                                || ch == '\r' //
                                || ch == '\t' //
                                || ch == '"' //
                                || ch == '/' //
                                || ch == '\\') {
                            write('\\');
                            write(replaceChars[(int) ch]);
                            continue;
                        }

                        if (ch < 32) {
                            write('\\');
                            write('u');
                            write('0');
                            write('0');
                            write(IOUtils.ASCII_CHARS[ch * 2]);
                            write(IOUtils.ASCII_CHARS[ch * 2 + 1]);
                            continue;
                        }

                        if (ch >= 127) {
                            write('\\');
                            write('u');
                            write(IOUtils.DIGITS[(ch >>> 12) & 15]);
                            write(IOUtils.DIGITS[(ch >>> 8) & 15]);
                            write(IOUtils.DIGITS[(ch >>> 4) & 15]);
                            write(IOUtils.DIGITS[ch & 15]);
                            continue;
                        }
                    } else {
                        if (ch < IOUtils.specicalFlags_doubleQuotes.length
                                && IOUtils.specicalFlags_doubleQuotes[ch] != 0 //
                                || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                            write('\\');
                            if (IOUtils.specicalFlags_doubleQuotes[ch] == 4) {
                                write('u');
                                write(IOUtils.DIGITS[ch >>> 12 & 15]);
                                write(IOUtils.DIGITS[ch >>> 8 & 15]);
                                write(IOUtils.DIGITS[ch >>> 4 & 15]);
                                write(IOUtils.DIGITS[ch & 15]);
                            } else {
                                write(IOUtils.replaceChars[ch]);
                            }
                            continue;
                        }
                    }

                    write(ch);
                }

                write('"');
                if (seperator != 0) {
                    write(seperator);
                }
                return;
            }
            expandCapacity(newcount);
        }

        int start = count + 1;
        int end = start + len;

        buf[count] = '\"';
//        text.getChars(0, len, buf, start);
        System.arraycopy(text, 0, buf, start, text.length);

        count = newcount;

        if (isEnabled(SerializerFeature.BrowserCompatible)) {
            int lastSpecialIndex = -1;

            for (int i = start; i < end; ++i) {
                char ch = buf[i];

                if (ch == '"' //
                        || ch == '/' //
                        || ch == '\\') {
                    lastSpecialIndex = i;
                    newcount += 1;
                    continue;
                }

                if (ch == '\b' //
                        || ch == '\f' //
                        || ch == '\n' //
                        || ch == '\r' //
                        || ch == '\t') {
                    lastSpecialIndex = i;
                    newcount += 1;
                    continue;
                }

                if (ch < 32) {
                    lastSpecialIndex = i;
                    newcount += 5;
                    continue;
                }

                if (ch >= 127) {
                    lastSpecialIndex = i;
                    newcount += 5;
                    continue;
                }
            }

            if (newcount > buf.length) {
                expandCapacity(newcount);
            }
            count = newcount;

            for (int i = lastSpecialIndex; i >= start; --i) {
                char ch = buf[i];

                if (ch == '\b' //
                        || ch == '\f'//
                        || ch == '\n' //
                        || ch == '\r' //
                        || ch == '\t') {
                    System.arraycopy(buf, i + 1, buf, i + 2, end - i - 1);
                    buf[i] = '\\';
                    buf[i + 1] = replaceChars[(int) ch];
                    end += 1;
                    continue;
                }

                if (ch == '"' //
                        || ch == '/' //
                        || ch == '\\') {
                    System.arraycopy(buf, i + 1, buf, i + 2, end - i - 1);
                    buf[i] = '\\';
                    buf[i + 1] = ch;
                    end += 1;
                    continue;
                }

                if (ch < 32) {
                    System.arraycopy(buf, i + 1, buf, i + 6, end - i - 1);
                    buf[i] = '\\';
                    buf[i + 1] = 'u';
                    buf[i + 2] = '0';
                    buf[i + 3] = '0';
                    buf[i + 4] = IOUtils.ASCII_CHARS[ch * 2];
                    buf[i + 5] = IOUtils.ASCII_CHARS[ch * 2 + 1];
                    end += 5;
                    continue;
                }

                if (ch >= 127) {
                    System.arraycopy(buf, i + 1, buf, i + 6, end - i - 1);
                    buf[i] = '\\';
                    buf[i + 1] = 'u';
                    buf[i + 2] = IOUtils.DIGITS[(ch >>> 12) & 15];
                    buf[i + 3] = IOUtils.DIGITS[(ch >>> 8) & 15];
                    buf[i + 4] = IOUtils.DIGITS[(ch >>> 4) & 15];
                    buf[i + 5] = IOUtils.DIGITS[ch & 15];
                    end += 5;
                }
            }

            if (seperator != 0) {
                buf[count - 2] = '\"';
                buf[count - 1] = seperator;
            } else {
                buf[count - 1] = '\"';
            }

            return;
        }

        int specialCount = 0;
        int lastSpecialIndex = -1;
        int firstSpecialIndex = -1;
        char lastSpecial = '\0';

        for (int i = start; i < end; ++i) {
            char ch = buf[i];

            if (ch >= ']') { // 93
                if (ch >= 0x7F //
                        && (ch == '\u2028' //
                        || ch == '\u2029' //
                        || ch < 0xA0)) {
                    if (firstSpecialIndex == -1) {
                        firstSpecialIndex = i;
                    }

                    specialCount++;
                    lastSpecialIndex = i;
                    lastSpecial = ch;
                    newcount += 4;
                }
                continue;
            }

            boolean special = (ch < 64 && (sepcialBits & (1L << ch)) != 0) || ch == '\\';
            if (special) {
                specialCount++;
                lastSpecialIndex = i;
                lastSpecial = ch;

                if (ch == '('
                        || ch == ')'
                        || ch == '<'
                        || ch == '>'
                        || (ch < IOUtils.specicalFlags_doubleQuotes.length //
                        && IOUtils.specicalFlags_doubleQuotes[ch] == 4) //
                        ) {
                    newcount += 4;
                }

                if (firstSpecialIndex == -1) {
                    firstSpecialIndex = i;
                }
            }
        }

        if (specialCount > 0) {
            newcount += specialCount;
            if (newcount > buf.length) {
                expandCapacity(newcount);
            }
            count = newcount;

            if (specialCount == 1) {
                if (lastSpecial == '\u2028') {
                    int srcPos = lastSpecialIndex + 1;
                    int destPos = lastSpecialIndex + 6;
                    int LengthOfCopy = end - lastSpecialIndex - 1;
                    System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                    buf[lastSpecialIndex] = '\\';
                    buf[++lastSpecialIndex] = 'u';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '0';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '8';
                } else if (lastSpecial == '\u2029') {
                    int srcPos = lastSpecialIndex + 1;
                    int destPos = lastSpecialIndex + 6;
                    int LengthOfCopy = end - lastSpecialIndex - 1;
                    System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                    buf[lastSpecialIndex] = '\\';
                    buf[++lastSpecialIndex] = 'u';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '0';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '9';
                } else if (lastSpecial == '(' || lastSpecial == ')' || lastSpecial == '<' || lastSpecial == '>') {
                    int srcPos = lastSpecialIndex + 1;
                    int destPos = lastSpecialIndex + 6;
                    int LengthOfCopy = end - lastSpecialIndex - 1;
                    System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                    buf[lastSpecialIndex] = '\\';
                    buf[++lastSpecialIndex] = 'u';

                    final char ch = lastSpecial;
                    buf[++lastSpecialIndex] = IOUtils.DIGITS[(ch >>> 12) & 15];
                    buf[++lastSpecialIndex] = IOUtils.DIGITS[(ch >>> 8) & 15];
                    buf[++lastSpecialIndex] = IOUtils.DIGITS[(ch >>> 4) & 15];
                    buf[++lastSpecialIndex] = IOUtils.DIGITS[ch & 15];
                } else {
                    final char ch = lastSpecial;
                    if (ch < IOUtils.specicalFlags_doubleQuotes.length //
                            && IOUtils.specicalFlags_doubleQuotes[ch] == 4) {
                        int srcPos = lastSpecialIndex + 1;
                        int destPos = lastSpecialIndex + 6;
                        int LengthOfCopy = end - lastSpecialIndex - 1;
                        System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);

                        int bufIndex = lastSpecialIndex;
                        buf[bufIndex++] = '\\';
                        buf[bufIndex++] = 'u';
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                    } else {
                        int srcPos = lastSpecialIndex + 1;
                        int destPos = lastSpecialIndex + 2;
                        int LengthOfCopy = end - lastSpecialIndex - 1;
                        System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                        buf[lastSpecialIndex] = '\\';
                        buf[++lastSpecialIndex] = replaceChars[(int) ch];
                    }
                }
            } else if (specialCount > 1) {
                int textIndex = firstSpecialIndex - start;
                int bufIndex = firstSpecialIndex;
                for (int i = textIndex; i < text.length; ++i) {
                    char ch = text[i];

                    if (browserSecure && (ch == '('
                            || ch == ')'
                            || ch == '<'
                            || ch == '>')) {
                        buf[bufIndex++] = '\\';
                        buf[bufIndex++] = 'u';
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                        end += 5;
                    } else if (ch < IOUtils.specicalFlags_doubleQuotes.length //
                            && IOUtils.specicalFlags_doubleQuotes[ch] != 0 //
                            || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                        buf[bufIndex++] = '\\';
                        if (IOUtils.specicalFlags_doubleQuotes[ch] == 4) {
                            buf[bufIndex++] = 'u';
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                            end += 5;
                        } else {
                            buf[bufIndex++] = replaceChars[(int) ch];
                            end++;
                        }
                    } else {
                        if (ch == '\u2028' || ch == '\u2029') {
                            buf[bufIndex++] = '\\';
                            buf[bufIndex++] = 'u';
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                            end += 5;
                        } else {
                            buf[bufIndex++] = ch;
                        }
                    }
                }
            }
        }

        if (seperator != 0) {
            buf[count - 2] = '\"';
            buf[count - 1] = seperator;
        } else {
            buf[count - 1] = '\"';
        }
    }
    
    public void writeFieldNameDirect(String text) {
        int len = text.length();
        int newcount = count + len + 3;

        if (newcount > buf.length) {
            expandCapacity(newcount);
        }

        int start = count + 1;

        buf[count] = '\"';
        text.getChars(0, len, buf, start);

        count = newcount;
        buf[count - 2] = '\"';
        buf[count - 1] = ':';
    }

    public void write(List<String> list) {
        if (list.isEmpty()) {
            /** 空字符列表，输出[]字符串 */
            write("[]");
            return;
        }

        int offset = count;
        final int initOffset = offset;
        for (int i = 0, list_size = list.size(); i < list_size; ++i) {
            /** 循环获取列表中包含的字符串 */
            String text = list.get(i);

            boolean hasSpecial = false;
            if (text == null) {
                /** list包含特殊的null值 */
                hasSpecial = true;
            } else {
                for (int j = 0, len = text.length(); j < len; ++j) {
                    char ch = text.charAt(j);
                    /** 包含指定特殊字符 */
                    if (hasSpecial = (ch < ' ' //
                                      || ch > '~' //
                                      || ch == '"' //
                                      || ch == '\\')) {
                        break;
                    }
                }
            }

            if (hasSpecial) {
                count = initOffset;
                write('[');
                for (int j = 0; j < list.size(); ++j) {
                    text = list.get(j);
                    /** 每个字符用,隔开输出 */
                    if (j != 0) {
                        write(',');
                    }

                    if (text == null) {
                        /** 字符串为空，直接输出null字符串 */
                        write("null");
                    } else {
                        /** 下文分析 */
                        writeStringWithDoubleQuote(text, (char) 0);
                    }
                }
                write(']');
                return;
            }

            /** 计算新的字符占用空间，额外3个字符用于存储 "," */
            int newcount = offset + text.length() + 3;
            if (i == list.size() - 1) {
                newcount++;
            }
            /** 如果当前存储空间不够*/
            if (newcount > buf.length) {
                count = offset;
                /** 扩容到为原有buf容量1.5倍+1, copy原有buf的字符*/
                expandCapacity(newcount);
            }

            if (i == 0) {
                buf[offset++] = '[';
            } else {
                buf[offset++] = ',';
            }
            buf[offset++] = '"';
            /** 拷贝text字符串到buffer数组中 */
            text.getChars(0, text.length(), buf, offset);
            offset += text.length();
            buf[offset++] = '"';
        }
        /** 最终构造列表形式 ["element", "element", ...] */
        buf[offset++] = ']';
        count = offset;
    }

    
    public void writeFieldValue(char seperator, String name, char value) {
        write(seperator);
        writeFieldName(name);
        if (value == 0) {
            writeString("\u0000");
        } else {
            writeString(Character.toString(value));
        }
    }

    public void writeFieldValue(char seperator, String name, boolean value) {
        if (!quoteFieldNames) {
            write(seperator);
            writeFieldName(name);
            write(value);
            return;
        }
        int intSize = value ? 4 : 5;

        int nameLen = name.length();
        int newcount = count + nameLen + 4 + intSize;
        if (newcount > buf.length) {
            if (writer != null) {
                write(seperator);
                writeString(name);
                write(':');
                write(value);
                return;
            }
            expandCapacity(newcount);
        }

        int start = count;
        count = newcount;

        buf[start] = seperator;

        int nameEnd = start + nameLen + 1;

        buf[start + 1] = keySeperator;

        name.getChars(0, nameLen, buf, start + 2);

        buf[nameEnd + 1] = keySeperator;

        if (value) {
            System.arraycopy(":true".toCharArray(), 0, buf, nameEnd + 2, 5);
        } else {
            System.arraycopy(":false".toCharArray(), 0, buf, nameEnd + 2, 6);
        }
    }

    public void write(boolean value) {
        if (value) {
            /** 输出true字符串 */
            write("true");
        } else {
            /** 输出false字符串 */
            write("false");
        }
    }

    public void writeFieldValue(char seperator, String name, int value) {
        if (value == Integer.MIN_VALUE || !quoteFieldNames) {
            write(seperator);
            writeFieldName(name);
            writeInt(value);
            return;
        }

        int intSize = (value < 0) ? IOUtils.stringSize(-value) + 1 : IOUtils.stringSize(value);

        int nameLen = name.length();
        int newcount = count + nameLen + 4 + intSize;
        if (newcount > buf.length) {
            if (writer != null) {
                write(seperator);
                writeFieldName(name);
                writeInt(value);
                return;
            }
            expandCapacity(newcount);
        }

        int start = count;
        count = newcount;

        buf[start] = seperator;

        int nameEnd = start + nameLen + 1;

        buf[start + 1] = keySeperator;

        name.getChars(0, nameLen, buf, start + 2);

        buf[nameEnd + 1] = keySeperator;
        buf[nameEnd + 2] = ':';

        IOUtils.getChars(value, count, buf);
    }

    public void writeFieldValue(char seperator, String name, long value) {
        if (value == Long.MIN_VALUE || !quoteFieldNames) {
            write(seperator);
            writeFieldName(name);
            writeLong(value);
            return;
        }

        int intSize = (value < 0) ? IOUtils.stringSize(-value) + 1 : IOUtils.stringSize(value);

        int nameLen = name.length();
        int newcount = count + nameLen + 4 + intSize;
        if (newcount > buf.length) {
            if (writer != null) {
                write(seperator);
                writeFieldName(name);
                writeLong(value);
                return;
            }
            expandCapacity(newcount);
        }

        int start = count;
        count = newcount;

        buf[start] = seperator;

        int nameEnd = start + nameLen + 1;

        buf[start + 1] = keySeperator;

        name.getChars(0, nameLen, buf, start + 2);

        buf[nameEnd + 1] = keySeperator;
        buf[nameEnd + 2] = ':';

        IOUtils.getChars(value, count, buf);
    }

    public void writeFieldValue(char seperator, String name, float value) {
        write(seperator);
        writeFieldName(name);
        writeFloat(value, false);
    }

    public void writeFieldValue(char seperator, String name, double value) {
        write(seperator);
        writeFieldName(name);
        writeDouble(value, false);
    }

    public void writeFieldValue(char seperator, String name, String value) {
        if (quoteFieldNames) {
            if (useSingleQuotes) {
                write(seperator);
                writeFieldName(name);
                if (value == null) {
                    writeNull();
                } else {
                    writeString(value);
                }
            } else {
                if (isEnabled(SerializerFeature.BrowserCompatible)) {
                    write(seperator);
                    writeStringWithDoubleQuote(name, ':');
                    writeStringWithDoubleQuote(value, (char) 0);
                } else {
                    writeFieldValueStringWithDoubleQuoteCheck(seperator, name, value);
                }
            }
        } else {
            write(seperator);
            writeFieldName(name);
            if (value == null) {
                writeNull();
            } else {
                writeString(value);
            }
        }
    }

    public void writeFieldValueStringWithDoubleQuoteCheck(char seperator, String name, String value) {
        int nameLen = name.length();
        int valueLen;

        int newcount = count;

        if (value == null) {
            valueLen = 4;
            newcount += nameLen + 8;
        } else {
            valueLen = value.length();
            newcount += nameLen + valueLen + 6;
        }

        if (newcount > buf.length) {
            if (writer != null) {
                write(seperator);
                writeStringWithDoubleQuote(name, ':');
                writeStringWithDoubleQuote(value, (char) 0);
                return;
            }
            expandCapacity(newcount);
        }

        buf[count] = seperator;

        int nameStart = count + 2;
        int nameEnd = nameStart + nameLen;

        buf[count + 1] = '\"';
        name.getChars(0, nameLen, buf, nameStart);

        count = newcount;

        buf[nameEnd] = '\"';

        int index = nameEnd + 1;
        buf[index++] = ':';

        if (value == null) {
            buf[index++] = 'n';
            buf[index++] = 'u';
            buf[index++] = 'l';
            buf[index++] = 'l';
            return;
        }

        buf[index++] = '"';

        int valueStart = index;
        int valueEnd = valueStart + valueLen;

        value.getChars(0, valueLen, buf, valueStart);

        int specialCount = 0;
        int lastSpecialIndex = -1;
        int firstSpecialIndex = -1;
        char lastSpecial = '\0';

        for (int i = valueStart; i < valueEnd; ++i) {
            char ch = buf[i];

            if (ch >= ']') {
                if (ch >= 0x7F //
                    && (ch == '\u2028' //
                        || ch == '\u2029' //
                        || ch < 0xA0)) {
                    if (firstSpecialIndex == -1) {
                        firstSpecialIndex = i;
                    }

                    specialCount++;
                    lastSpecialIndex = i;
                    lastSpecial = ch;
                    newcount += 4;
                }
                continue;
            }

            boolean special = (ch < 64 && (sepcialBits & (1L << ch)) != 0) || ch == '\\';
            if (special) {
                specialCount++;
                lastSpecialIndex = i;
                lastSpecial = ch;

                if (ch == '('
                        || ch == ')'
                        || ch == '<'
                        || ch == '>'
                        || (ch < IOUtils.specicalFlags_doubleQuotes.length //
                        && IOUtils.specicalFlags_doubleQuotes[ch] == 4) //
                        ) {
                    newcount += 4;
                }

                if (firstSpecialIndex == -1) {
                    firstSpecialIndex = i;
                }
            }
        }

        if (specialCount > 0) {
            newcount += specialCount;
            if (newcount > buf.length) {
                expandCapacity(newcount);
            }
            count = newcount;

            if (specialCount == 1) {
                if (lastSpecial == '\u2028') {
                    int srcPos = lastSpecialIndex + 1;
                    int destPos = lastSpecialIndex + 6;
                    int LengthOfCopy = valueEnd - lastSpecialIndex - 1;
                    System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                    buf[lastSpecialIndex] = '\\';
                    buf[++lastSpecialIndex] = 'u';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '0';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '8';
                } else if (lastSpecial == '\u2029') {
                    int srcPos = lastSpecialIndex + 1;
                    int destPos = lastSpecialIndex + 6;
                    int LengthOfCopy = valueEnd - lastSpecialIndex - 1;
                    System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                    buf[lastSpecialIndex] = '\\';
                    buf[++lastSpecialIndex] = 'u';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '0';
                    buf[++lastSpecialIndex] = '2';
                    buf[++lastSpecialIndex] = '9';
                } else if (lastSpecial == '(' || lastSpecial == ')' || lastSpecial == '<' || lastSpecial == '>') {
                    final char ch = lastSpecial;
                    int srcPos = lastSpecialIndex + 1;
                    int destPos = lastSpecialIndex + 6;
                    int LengthOfCopy = valueEnd - lastSpecialIndex - 1;
                    System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);

                    int bufIndex = lastSpecialIndex;
                    buf[bufIndex++] = '\\';
                    buf[bufIndex++] = 'u';
                    buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                    buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                    buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                    buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                } else {
                    final char ch = lastSpecial;
                    if (ch < IOUtils.specicalFlags_doubleQuotes.length //
                        && IOUtils.specicalFlags_doubleQuotes[ch] == 4) {
                        int srcPos = lastSpecialIndex + 1;
                        int destPos = lastSpecialIndex + 6;
                        int LengthOfCopy = valueEnd - lastSpecialIndex - 1;
                        System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);

                        int bufIndex = lastSpecialIndex;
                        buf[bufIndex++] = '\\';
                        buf[bufIndex++] = 'u';
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                    } else {
                        int srcPos = lastSpecialIndex + 1;
                        int destPos = lastSpecialIndex + 2;
                        int LengthOfCopy = valueEnd - lastSpecialIndex - 1;
                        System.arraycopy(buf, srcPos, buf, destPos, LengthOfCopy);
                        buf[lastSpecialIndex] = '\\';
                        buf[++lastSpecialIndex] = replaceChars[(int) ch];
                    }
                }
            } else if (specialCount > 1) {
                int textIndex = firstSpecialIndex - valueStart;
                int bufIndex = firstSpecialIndex;
                for (int i = textIndex; i < value.length(); ++i) {
                    char ch = value.charAt(i);

                    if (browserSecure && (ch == '('
                            || ch == ')'
                            || ch == '<'
                            || ch == '>')) {
                        buf[bufIndex++] = '\\';
                        buf[bufIndex++] = 'u';
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                        buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                        valueEnd += 5;
                    } else if (ch < IOUtils.specicalFlags_doubleQuotes.length //
                        && IOUtils.specicalFlags_doubleQuotes[ch] != 0 //
                        || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                        buf[bufIndex++] = '\\';
                        if (IOUtils.specicalFlags_doubleQuotes[ch] == 4) {
                            buf[bufIndex++] = 'u';
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                            valueEnd += 5;
                        } else {
                            buf[bufIndex++] = replaceChars[(int) ch];
                            valueEnd++;
                        }
                    } else {
                        if (ch == '\u2028' || ch == '\u2029') {
                            buf[bufIndex++] = '\\';
                            buf[bufIndex++] = 'u';
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 12) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 8) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[(ch >>> 4) & 15];
                            buf[bufIndex++] = IOUtils.DIGITS[ch & 15];
                            valueEnd += 5;
                        } else {
                            buf[bufIndex++] = ch;
                        }
                    }
                }
            }
        }
        

        buf[count - 1] = '\"';
    }

    public void writeFieldValueStringWithDoubleQuote(char seperator, String name, String value) {
        int nameLen = name.length();
        int valueLen;

        int newcount = count;

        valueLen = value.length();
        newcount += nameLen + valueLen + 6;

        if (newcount > buf.length) {
            if (writer != null) {
                write(seperator);
                writeStringWithDoubleQuote(name, ':');
                writeStringWithDoubleQuote(value, (char) 0);
                return;
            }
            expandCapacity(newcount);
        }

        buf[count] = seperator;

        int nameStart = count + 2;
        int nameEnd = nameStart + nameLen;

        buf[count + 1] = '\"';
        name.getChars(0, nameLen, buf, nameStart);

        count = newcount;

        buf[nameEnd] = '\"';

        int index = nameEnd + 1;
        buf[index++] = ':';
        buf[index++] = '"';

        int valueStart = index;
        value.getChars(0, valueLen, buf, valueStart);
        buf[count - 1] = '\"';
    }


    
    public void writeFieldValue(char seperator, String name, Enum<?> value) {
        if (value == null) {
            write(seperator);
            writeFieldName(name);
            writeNull();
            return;
        }

        if (writeEnumUsingName && !writeEnumUsingToString) {
            writeEnumFieldValue(seperator, name, value.name());
        } else if (writeEnumUsingToString) {
            writeEnumFieldValue(seperator, name, value.toString());
        } else {
            writeFieldValue(seperator, name, value.ordinal());
        }
    }

    private void writeEnumFieldValue(char seperator, String name, String value) {
        if (useSingleQuotes) {
            writeFieldValue(seperator, name, value);
        } else {
            writeFieldValueStringWithDoubleQuote(seperator, name, value);
        }
    }

    public void writeFieldValue(char seperator, String name, BigDecimal value) {
        write(seperator);
        writeFieldName(name);
        if (value == null) {
            writeNull();
        } else {
            write(value.toString());
        }
    }

    public void writeString(String text, char seperator) {
        if (useSingleQuotes) {
            writeStringWithSingleQuote(text);
            write(seperator);
        } else {
            writeStringWithDoubleQuote(text, seperator);
        }
    }

    public void writeString(String text) {
        if (useSingleQuotes) {
            writeStringWithSingleQuote(text);
        } else {
            writeStringWithDoubleQuote(text, (char) 0);
        }
    }

    public void writeString(char[] chars) {
        if (useSingleQuotes) {
            writeStringWithSingleQuote(chars);
        } else {
            String text = new String(chars);
            writeStringWithDoubleQuote(text, (char) 0);
        }
    }

    protected void writeStringWithSingleQuote(String text) {
        if (text == null) {
            int newcount = count + 4;
            if (newcount > buf.length) {
                expandCapacity(newcount);
            }
            "null".getChars(0, 4, buf, count);
            count = newcount;
            return;
        }

        int len = text.length();
        int newcount = count + len + 2;
        if (newcount > buf.length) {
            if (writer != null) {
                write('\'');
                for (int i = 0; i < text.length(); ++i) {
                    char ch = text.charAt(i);
                    if (ch <= 13 || ch == '\\' || ch == '\'' //
                        || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                        write('\\');
                        write(replaceChars[(int) ch]);
                    } else {
                        write(ch);
                    }
                }
                write('\'');
                return;
            }
            expandCapacity(newcount);
        }

        int start = count + 1;
        int end = start + len;

        buf[count] = '\'';
        text.getChars(0, len, buf, start);
        count = newcount;

        int specialCount = 0;
        int lastSpecialIndex = -1;
        char lastSpecial = '\0';
        for (int i = start; i < end; ++i) {
            char ch = buf[i];
            if (ch <= 13 || ch == '\\' || ch == '\'' //
                || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                specialCount++;
                lastSpecialIndex = i;
                lastSpecial = ch;
            }
        }

        newcount += specialCount;
        if (newcount > buf.length) {
            expandCapacity(newcount);
        }
        count = newcount;

        if (specialCount == 1) {
            System.arraycopy(buf, lastSpecialIndex + 1, buf, lastSpecialIndex + 2, end - lastSpecialIndex - 1);
            buf[lastSpecialIndex] = '\\';
            buf[++lastSpecialIndex] = replaceChars[(int) lastSpecial];
        } else if (specialCount > 1) {
            System.arraycopy(buf, lastSpecialIndex + 1, buf, lastSpecialIndex + 2, end - lastSpecialIndex - 1);
            buf[lastSpecialIndex] = '\\';
            buf[++lastSpecialIndex] = replaceChars[(int) lastSpecial];
            end++;
            for (int i = lastSpecialIndex - 2; i >= start; --i) {
                char ch = buf[i];

                if (ch <= 13 || ch == '\\' || ch == '\'' //
                    || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                    System.arraycopy(buf, i + 1, buf, i + 2, end - i - 1);
                    buf[i] = '\\';
                    buf[i + 1] = replaceChars[(int) ch];
                    end++;
                }
            }
        }

        buf[count - 1] = '\'';
    }

    protected void writeStringWithSingleQuote(char[] chars) {
        if (chars == null) {
            int newcount = count + 4;
            if (newcount > buf.length) {
                expandCapacity(newcount);
            }
            "null".getChars(0, 4, buf, count);
            count = newcount;
            return;
        }

        int len = chars.length;
        int newcount = count + len + 2;
        if (newcount > buf.length) {
            if (writer != null) {
                write('\'');
                for (int i = 0; i < chars.length; ++i) {
                    char ch = chars[i];
                    if (ch <= 13 || ch == '\\' || ch == '\'' //
                            || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                        write('\\');
                        write(replaceChars[(int) ch]);
                    } else {
                        write(ch);
                    }
                }
                write('\'');
                return;
            }
            expandCapacity(newcount);
        }

        int start = count + 1;
        int end = start + len;

        buf[count] = '\'';
//        text.getChars(0, len, buf, start);
        System.arraycopy(chars, 0, buf, start, chars.length);
        count = newcount;

        int specialCount = 0;
        int lastSpecialIndex = -1;
        char lastSpecial = '\0';
        for (int i = start; i < end; ++i) {
            char ch = buf[i];
            if (ch <= 13 || ch == '\\' || ch == '\'' //
                    || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                specialCount++;
                lastSpecialIndex = i;
                lastSpecial = ch;
            }
        }

        newcount += specialCount;
        if (newcount > buf.length) {
            expandCapacity(newcount);
        }
        count = newcount;

        if (specialCount == 1) {
            System.arraycopy(buf, lastSpecialIndex + 1, buf, lastSpecialIndex + 2, end - lastSpecialIndex - 1);
            buf[lastSpecialIndex] = '\\';
            buf[++lastSpecialIndex] = replaceChars[(int) lastSpecial];
        } else if (specialCount > 1) {
            System.arraycopy(buf, lastSpecialIndex + 1, buf, lastSpecialIndex + 2, end - lastSpecialIndex - 1);
            buf[lastSpecialIndex] = '\\';
            buf[++lastSpecialIndex] = replaceChars[(int) lastSpecial];
            end++;
            for (int i = lastSpecialIndex - 2; i >= start; --i) {
                char ch = buf[i];

                if (ch <= 13 || ch == '\\' || ch == '\'' //
                        || (ch == '/' && isEnabled(SerializerFeature.WriteSlashAsSpecial))) {
                    System.arraycopy(buf, i + 1, buf, i + 2, end - i - 1);
                    buf[i] = '\\';
                    buf[i + 1] = replaceChars[(int) ch];
                    end++;
                }
            }
        }

        buf[count - 1] = '\'';
    }

    public void writeFieldName(String key) {
        writeFieldName(key, false);
    }

    public void writeFieldName(String key, boolean checkSpecial) {
        if (key == null) {
            write("null:");
            return;
        }

        if (useSingleQuotes) {
            if (quoteFieldNames) {
                writeStringWithSingleQuote(key);
                write(':');
            } else {
                writeKeyWithSingleQuoteIfHasSpecial(key);
            }
        } else {
            if (quoteFieldNames) {
                writeStringWithDoubleQuote(key, ':');
            } else {
                boolean hashSpecial = key.length() == 0;
                for (int i = 0; i < key.length(); ++i) {
                    char ch = key.charAt(i);
                    boolean special = (ch < 64 && (sepcialBits & (1L << ch)) != 0) || ch == '\\';
                    if (special) {
                        hashSpecial = true;
                        break;
                    }
                }
                if (hashSpecial) {
                    writeStringWithDoubleQuote(key, ':');
                } else {
                    write(key);
                    write(':');
                }
            }
        }
    }

    private void writeKeyWithSingleQuoteIfHasSpecial(String text) {
        final byte[] specicalFlags_singleQuotes = IOUtils.specicalFlags_singleQuotes;

        int len = text.length();
        int newcount = count + len + 1;
        if (newcount > buf.length) {
            if (writer != null) {
                if (len == 0) {
                    write('\'');
                    write('\'');
                    write(':');
                    return;
                }

                boolean hasSpecial = false;
                for (int i = 0; i < len; ++i) {
                    char ch = text.charAt(i);
                    if (ch < specicalFlags_singleQuotes.length && specicalFlags_singleQuotes[ch] != 0) {
                        hasSpecial = true;
                        break;
                    }
                }

                if (hasSpecial) {
                    write('\'');
                }
                for (int i = 0; i < len; ++i) {
                    char ch = text.charAt(i);
                    if (ch < specicalFlags_singleQuotes.length && specicalFlags_singleQuotes[ch] != 0) {
                        write('\\');
                        write(replaceChars[(int) ch]);
                    } else {
                        write(ch);
                    }
                }
                if (hasSpecial) {
                    write('\'');
                }
                write(':');
                return;
            }

            expandCapacity(newcount);
        }

        if (len == 0) {
            int newCount = count + 3;
            if (newCount > buf.length) {
                expandCapacity(count + 3);
            }
            buf[count++] = '\'';
            buf[count++] = '\'';
            buf[count++] = ':';
            return;
        }

        int start = count;
        int end = start + len;

        text.getChars(0, len, buf, start);
        count = newcount;

        boolean hasSpecial = false;

        for (int i = start; i < end; ++i) {
            char ch = buf[i];
            if (ch < specicalFlags_singleQuotes.length && specicalFlags_singleQuotes[ch] != 0) {
                if (!hasSpecial) {
                    newcount += 3;
                    if (newcount > buf.length) {
                        expandCapacity(newcount);
                    }
                    count = newcount;

                    System.arraycopy(buf, i + 1, buf, i + 3, end - i - 1);
                    System.arraycopy(buf, 0, buf, 1, i);
                    buf[start] = '\'';
                    buf[++i] = '\\';
                    buf[++i] = replaceChars[(int) ch];
                    end += 2;
                    buf[count - 2] = '\'';

                    hasSpecial = true;
                } else {
                    newcount++;
                    if (newcount > buf.length) {
                        expandCapacity(newcount);
                    }
                    count = newcount;

                    System.arraycopy(buf, i + 1, buf, i + 2, end - i);
                    buf[i] = '\\';
                    buf[++i] = replaceChars[(int) ch];
                    end++;
                }
            }
        }

        buf[newcount - 1] = ':';
    }

    public void flush() {
        if (writer == null) {
            return;
        }

        try {
            writer.write(buf, 0, count);
            writer.flush();
        } catch (IOException e) {
            throw new JSONException(e.getMessage(), e);
        }
        count = 0;
    }


}
