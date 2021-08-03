package io.micronaut.jsongen.bean

import io.micronaut.jsongen.JsonParseException

class InlineBeanSerializerSymbolSpec extends AbstractBeanSerializerSpec {
    void "simple bean"() {
        given:
        def compiled = buildSerializer('''
package example;

class Test {
    public String a;
    private String b;
    
    Test() {}
    
    public String getB() {
        return b;
    }
    
    public void setB(String b) {
        this.b = b;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"a": "foo", "b": "bar"}')
        def testBean = compiled.newInstance()
        testBean.a = "foo"
        testBean.b = "bar"
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.a == "foo"
        deserialized.b == "bar"
        serialized == '{"a":"foo","b":"bar"}'
    }

    void "JsonProperty on field"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;
class Test {
    @JsonProperty("foo")
    public String bar;
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": "42"}')
        def testBean = compiled.newInstance()
        testBean.bar = "42"
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'
    }

    void "JsonProperty on getter"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;
class Test {
    private String bar;
    
    @JsonProperty("foo")
    public String getBar() {
        return bar;
    }
    
    public void setBar(String bar) {
        this.bar = bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": "42"}')
        def testBean = compiled.newInstance()
        testBean.bar = "42"
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'
    }

    void "JsonProperty on is-getter"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;
class Test {
    private boolean bar;
    
    @JsonProperty("foo")
    public boolean isBar() {
        return bar;
    }
    
    public void setBar(boolean bar) {
        this.bar = bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": true}')
        def testBean = compiled.newInstance()
        testBean.bar = true
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == true
        serialized == '{"foo":true}'
    }

    void "JsonProperty on accessors without prefix"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.JsonProperty;
class Test {
    private String bar;
    
    @JsonProperty
    public String bar() {
        return bar;
    }
    
    @JsonProperty
    public void bar(String bar) {
        this.bar = bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"bar": "42"}')
        def testBean = compiled.newInstance()
        testBean.bar = "42"
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"bar":"42"}'
    }

    void "JsonCreator constructor"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    @JsonProperty("foo")
    private final String bar;
    
    @JsonCreator
    public Test(@JsonProperty("foo") String bar) {
        this.bar = bar;
    }
    
    public String getBar() {
        return bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": "42"}')
        def testBean = compiled.newInstance(["42"])
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'
    }

    void "JsonCreator constructor with properties mode set"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    @JsonProperty("foo")
    private final String bar;
    
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public Test(@JsonProperty("foo") String bar) {
        this.bar = bar;
    }
    
    public String getBar() {
        return bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": "42"}')
        def testBean = compiled.newInstance(["42"])
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'
    }

    void "JsonCreator static method"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    @JsonProperty("foo")
    private final String bar;
    
    private Test(String bar) {
        this.bar = bar;
    }
    
    @JsonCreator
    public static Test create(@JsonProperty("foo") String bar) {
        return new Test(bar);
    }
    
    public String getBar() {
        return bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": "42"}')
        def testBean = compiled.newInstance(["42"])
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'
    }

    void "JsonCreator no getter"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    private final String bar;
    
    @JsonCreator
    public Test(@JsonProperty("foo") String bar) {
        this.bar = bar;
    }
}
''')
        def deserialized = deserializeFromString(compiled.serializer, '{"foo": "42"}')
        def testBean = compiled.newInstance(["42"])
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{}'
    }

    @SuppressWarnings('JsonDuplicatePropertyKeys')
    void "duplicate property throws exception"() {
        given:
        def compiled = buildSerializer('''
package example;

class Test {
    String foo;
}
''')

        when:
        deserializeFromString(compiled.serializer, '{"foo": "42", "foo": "43"}')

        then:
        thrown JsonParseException
    }

    @SuppressWarnings('JsonDuplicatePropertyKeys')
    void "missing required property throws exception"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    String foo;
    
    @JsonCreator
    Test(@JsonProperty(value = "foo", required = true) String foo) {
        this.foo = foo;
    }
}
''')

        when:
        deserializeFromString(compiled.serializer, '{}')

        then:
        thrown JsonParseException
    }

    void "missing required property throws exception, many variables"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    String v0, v1, v2, v3, v4, v5, v6, v7, v8, v9, v10, v11, v12, v13, v14, v15, 
    v16, v17, v18, v19, v20, v21, v22, v23, v24, v25, v26, v27, v28, v29, v30, v31, 
    v32, v33, v34, v35, v36, v37, v38, v39, v40, v41, v42, v43, v44, v45, v46, v47, 
    v48, v49, v50, v51, v52, v53, v54, v55, v56, v57, v58, v59, v60, v61, v62, v63, 
    v64, v65, v66, v67, v68, v69, v70, v71, v72, v73, v74, v75, v76, v77, v78, v79;

    @JsonCreator
    public Test(
            @JsonProperty(value = "v7", required = true) String v7,
            @JsonProperty(value = "v14", required = true) String v14,
            @JsonProperty(value = "v75", required = true) String v75
    ) {
        this.v7 = v7;
        this.v14 = v14;
        this.v75 = v75;
    }
}
''')

        when:
        deserializeFromString(compiled.serializer, '{"v7": "42", "v75": "43"}')

        then:
        def e = thrown JsonParseException
        // with the right message please
        e.message.contains("v14")
    }

    void "unknown properties lead to error"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    String foo;
}
''')

        when:
        deserializeFromString(compiled.serializer, '{"foo": "1", "bar": "2"}')

        then:
        thrown JsonParseException
    }

    void "unknown properties with proper annotation"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
@JsonIgnoreProperties(ignoreUnknown = true)
class Test {
    String foo;
}
''')

        def des = deserializeFromString(compiled.serializer, '{"foo": "1", "bar": "2"}')

        expect:
        des.foo == "1"
    }

    void "json ignore"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
@JsonIgnoreProperties(ignoreUnknown = true)
class Test {
    @JsonIgnore String foo;
    String bar;
}
''')

        def des = deserializeFromString(compiled.serializer, '{"foo": "1", "bar": "2"}')
        def testBean = compiled.newInstance()
        testBean.foo = "1"
        testBean.bar = "2"
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        des.foo == null
        des.bar == "2"
        serialized == '{"bar":"2"}'
    }

    void "nullable"() {
        given:
        def compiled = buildSerializer('''
package example;

import io.micronaut.core.annotation.Nullable;
class Test {
    @Nullable String foo;
}
''')

        def des = deserializeFromString(compiled.serializer, '{"foo": null}')
        def testBean = compiled.newInstance()
        testBean.foo = null

        expect:
        des.foo == null
        serializeToString(compiled.serializer, testBean) == '{"foo":null}'
    }

    void "unwrapped"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
class Test {
    @JsonUnwrapped Name name = new Name();
}

class Name {
    String first;
    String last;
}
''')

        def des = deserializeFromString(compiled.serializer, '{"first":"foo","last":"bar"}')
        def testBean = compiled.newInstance()
        testBean.name.first = "foo"
        testBean.name.last = "bar"

        expect:
        serializeToString(compiled.serializer, testBean) == '{"first":"foo","last":"bar"}'
        des.name != null
        des.name.first == "foo"
        des.name.last == "bar"
    }

    void "aliases"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.JsonAlias;
class Test {
    @JsonAlias("bar")
    public String foo;
}
''')

        expect:
        deserializeFromString(compiled.serializer, '{"foo": "42"}').foo == '42'
        deserializeFromString(compiled.serializer, '{"bar": "42"}').foo == '42'
    }

    void "value and creator"() {
        given:
        def compiled = buildSerializer('''
package example;

import com.fasterxml.jackson.annotation.*;
class Test {
    @JsonValue
    public final String foo;
    
    @JsonCreator
    public Test(String foo) {
        this.foo = foo;
    }
}
''')
        def testBean = compiled.newInstance(['bar'])

        expect:
        deserializeFromString(compiled.serializer, '"bar"').foo == 'bar'
        serializeToString(compiled.serializer, testBean) == '"bar"'
    }
}
