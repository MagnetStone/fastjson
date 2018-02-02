package com.alibaba.json.bvt.parser.deser;

import com.alibaba.fastjson.annotation.JSONType;
import com.alibaba.fastjson.parser.Feature;
import com.alibaba.fastjson.serializer.SerializerFeature;
import org.junit.Assert;
import junit.framework.TestCase;

import com.alibaba.fastjson.JSON;

public class BooleanFieldDeserializerTest extends TestCase {

    public void test_0() throws Exception {
        Entity a = JSON.parseObject("{f1:null, f2:null}", Entity.class);
        Assert.assertEquals(true, a.isF1());
        Assert.assertEquals(null, a.getF2());
    }

    public void test_1() throws Exception {
        Model0 a = JSON.parseObject("[true,false]", Model0.class);
        Assert.assertEquals(true, a.value);
        Assert.assertEquals(false, a.value1);
    }

    public void test_2() throws Exception {
        Model1 a = JSON.parseObject("{\"key\":\"7008.5555\"}", Model1.class);
//        Model1 a = JSON.parseObject("{\"value\":true, \"value1\":true, \"key\":\"7008.5555\"}", Model1.class);
    }

    public static class Entity {

        private boolean f1 = true;
        private Boolean f2 = Boolean.TRUE;

        public boolean isF1() {
            return f1;
        }

        public void setF1(boolean f1) {
            this.f1 = f1;
        }

        public Boolean getF2() {
            return f2;
        }

        public void setF2(Boolean f2) {
            this.f2 = f2;
        }

    }

    @JSONType(serialzeFeatures = SerializerFeature.BeanToArray, parseFeatures = Feature.SupportArrayToBean, asm = false)
    public static class Model0 {

        public boolean value;
        public boolean value1;
    }

    @JSONType(asm = false)
    public static class Model1 {

        public float key;

//        public boolean value;
//        public boolean value1;


    }
}
