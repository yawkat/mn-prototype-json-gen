package io.micronaut.jsongen.bean

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class BeanIntrospectorSpec extends AbstractTypeElementSpec {
    void "basic class"() {
        given:
        def classElement = buildClassElement('''
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
    
    public String isB() {
        return b;
    }
}
''')
        def definition = BeanIntrospector.introspect(classElement)

        expect:
        definition.defaultConstructor != null
        definition.props.size() == 2

        definition.props["a"].field != null
        definition.props["a"].field.name == "a"
        definition.props["a"].setter == null
        definition.props["a"].getter == null
        definition.props["a"].isGetter == null

        definition.props["b"].field != null
        definition.props["b"].field.name == "b"
        definition.props["b"].setter != null
        definition.props["b"].setter.name == "setB"
        definition.props["b"].getter != null
        definition.props["b"].getter.name == "getB"
        definition.props["b"].isGetter != null
        definition.props["b"].isGetter.name == "isB"
    }
}
