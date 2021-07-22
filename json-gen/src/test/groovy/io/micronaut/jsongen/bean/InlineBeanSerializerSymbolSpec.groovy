package io.micronaut.jsongen.bean

class InlineBeanSerializerSymbolSpec extends AbstractBeanSerializerSpec {
    void "simple bean"() {
        given:
        def compiled = buildSerializer('''
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

    void "JsonProperty on accessors without prefix"() {
        given:
        def compiled = buildSerializer('''
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
        def testBean = compiled.newInstance([String], ["42"])
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'
    }

    void "JsonCreator constructor with properties mode set"() {
        given:
        def compiled = buildSerializer('''
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
        def testBean = compiled.newInstance([String], ["42"])
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'
    }

    void "JsonCreator static method"() {
        given:
        def compiled = buildSerializer('''
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
        def testBean = compiled.newInstance([String], ["42"])
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{"foo":"42"}'
    }

    void "JsonCreator no getter"() {
        given:
        def compiled = buildSerializer('''
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
        def testBean = compiled.newInstance([String], ["42"])
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.bar == "42"
        serialized == '{}'
    }
}
