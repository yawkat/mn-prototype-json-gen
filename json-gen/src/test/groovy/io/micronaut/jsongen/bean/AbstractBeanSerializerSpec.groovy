package io.micronaut.jsongen.bean

import com.fasterxml.jackson.core.JsonFactory
import com.fasterxml.jackson.core.JsonFactoryBuilder
import com.fasterxml.jackson.core.JsonGenerator
import groovy.transform.Immutable
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.jsongen.Serializer

import javax.tools.JavaFileObject
import javax.tools.SimpleJavaFileObject
import java.lang.reflect.Constructor
import java.nio.charset.Charset

import static javax.tools.JavaFileObject.Kind.SOURCE

class AbstractBeanSerializerSpec extends AbstractTypeElementSpec {
    static final JsonFactory JSON_FACTORY = new JsonFactoryBuilder().build();

    CompiledBean<?> buildSerializer(String cls) {
        def classElement = buildClassElement(cls)
        def serializerCode = new BeanSerializerGenerator(classElement).generate()
        def files = newJavaParser().generate(
                new StringSourceJavaFileObject(classElement.name, cls),
                new StringSourceJavaFileObject(classElement.name + '$Serializer', serializerCode.toString())
        )

        ClassLoader classLoader = new ClassLoader() {
            @Override
            protected Class<?> findClass(String name) throws ClassNotFoundException {
                String fileName = name.replace('.', '/') + '.class'
                JavaFileObject generated = files.find { it.name.endsWith(fileName) }
                if (generated != null) {
                    def bytes = generated.openInputStream().bytes
                    return defineClass(name, bytes, 0, bytes.length)
                }
                return super.findClass(name)
            }
        }

        def beanClass = classLoader.loadClass(classElement.name)
        def serializerClass = classLoader.loadClass(classElement.name + '$Serializer')
        def serializerInstance = (Serializer<?>) serializerClass.getField("INSTANCE").get(null)
        return new CompiledBean(beanClass, serializerInstance)
    }

    @Immutable(knownImmutableClasses = [Serializer])
    class CompiledBean<T> {
        Class<T> beanClass
        Serializer<T> serializer

        def newInstance() {
            def constructor = beanClass.getDeclaredConstructor()
            constructor.accessible = true
            return constructor.newInstance()
        }
    }

    /**
     * stolen from micronaut-inject
     * todo: reuse instead
     */
    private static final class StringSourceJavaFileObject extends SimpleJavaFileObject {
        final String source;
        final long lastModified;

        /**
         * Default constructor.
         * @param fullyQualifiedName the fully qualified name
         * @param source The source
         */
        StringSourceJavaFileObject(String fullyQualifiedName, String source) {
            super(createUri(fullyQualifiedName), SOURCE);
            this.source = source;
            this.lastModified = System.currentTimeMillis();
        }

        private static URI createUri(String fullyQualifiedClassName) {
            return URI.create(fullyQualifiedClassName.replace('.', '/')
                    + SOURCE.extension);
        }

        @Override
        public CharSequence getCharContent(boolean ignoreEncodingErrors) {
            return source;
        }

        @Override
        public OutputStream openOutputStream() {
            throw new IllegalStateException();
        }

        @Override
        public InputStream openInputStream() {
            return new ByteArrayInputStream(source.getBytes(Charset.defaultCharset()));
        }

        @Override
        public Writer openWriter() {
            throw new IllegalStateException();
        }

        @Override
        public Reader openReader(boolean ignoreEncodingErrors) {
            return new StringReader(source);
        }

        @Override
        public long getLastModified() {
            return lastModified;
        }
    }

    static <T> String serializeToString(Serializer<T> serializer, T value) {
        def writer = new StringWriter()
        def generator = JSON_FACTORY.createGenerator(writer)
        serializer.serialize(generator, value)
        generator.close()
        return writer.toString()
    }
}
