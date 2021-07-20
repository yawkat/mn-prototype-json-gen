package io.micronaut.jsongen.bean

import com.fasterxml.jackson.core.JsonFactory
import com.squareup.javapoet.JavaFile
import groovy.transform.Immutable
import groovy.transform.ImmutableOptions
import io.micronaut.annotation.processing.AnnotationUtils
import io.micronaut.annotation.processing.GenericUtils
import io.micronaut.annotation.processing.ModelUtils
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.annotation.processing.test.JavaFileObjects
import io.micronaut.annotation.processing.visitor.JavaClassElement
import io.micronaut.annotation.processing.visitor.JavaVisitorContext
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.convert.value.MutableConvertibleValuesMap
import io.micronaut.inject.ast.ClassElement
import io.micronaut.jsongen.Serializer

import javax.lang.model.element.TypeElement
import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import java.nio.charset.Charset

import static javax.tools.JavaFileObject.Kind.SOURCE
import static javax.tools.JavaFileObject.Kind.SOURCE

class BeanSerializerGeneratorSpec extends AbstractBeanSerializerSpec {
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
        def deserialized = compiled.serializer.deserialize(new JsonFactory().createParser('{"a": "foo", "b": "bar"}'))
        def testBean = compiled.newInstance()
        testBean.a = "foo"
        testBean.b = "bar"
        def serialized = serializeToString(compiled.serializer, testBean)

        expect:
        deserialized.a == "foo"
        deserialized.b == "bar"
        serialized == '{"a":"foo","b":"bar"}'
    }
}
