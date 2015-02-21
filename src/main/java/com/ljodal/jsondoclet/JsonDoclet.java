package com.ljodal.jsondoclet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.sun.javadoc.*;


/**
 * Doclet implementation for javadoc command.
 * This output is based on JavaScript Object Notation.
 *
 * @author E.Sekito
 * @since 2014/07/31
 */
public class JsonDoclet {
    private String outputDir = ".";
    private static File file = null;

    public static LanguageVersion languageVersion() {
        return LanguageVersion.JAVA_1_5;
    }

    /**
     * Entry point when generating JavaDoc.
     *
     * @param root The root doc object
     */
    public static boolean start(RootDoc root) {
        new JsonDoclet(root);
        return true;
    }

    /**
     * Private constructor for generating json.
     *
     * @param root The root doc object
     */
    private JsonDoclet(RootDoc root) {
        // Parse options
        parseOptions(root.options());

        // File handlers
        OutputStream out = null;
        JsonGenerator json = null;

        // Write each of the classes to its own file
        for (final ClassDoc classDoc : root.classes()) {
            File file = new File(this.outputDir, classDoc.qualifiedName() + ".json");

            try {
                out = new FileOutputStream(file);
                json = new JsonFactory().createGenerator(out);

                writeClass(json, classDoc);

                json.flush();
            } catch (IOException e) {
                throw new RuntimeException(e);
            } finally {
                try {
                    if (out != null)
                        out.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }

                try {
                    if (json != null && !json.isClosed())
                        json.close();
                } catch (IOException e) {
                    e.printStackTrace();
                    ;
                }
            }
        }
    }

    public static int optionLength(String option) {
        switch (option) {
            case "-d":
                return 2;
            default:
                return 0;
        }
    }

    private void parseOptions(String[][] options) {
        for (String[] opt : options) {
            switch (opt[0]) {
                case "-d":
                    outputDir = opt[1];
                    break;
            }
        }
    }

    static void writeClass(JsonGenerator g, ClassDoc doc) throws IOException {
        g.writeStartObject();

        // Write class basics, like package, name, and generics information
        writeTypeBasics(g, doc);


        g.writeObjectField("comment", doc.commentText());

        g.writeArrayFieldStart("modifiers");
        for (String s : doc.modifiers().split(" ")) {
            g.writeString(s);
        }
        g.writeEndArray();

        // Superclass
        g.writeObjectFieldStart("extends");
        writeTypeBasics(g, doc.superclassType());
        g.writeEndObject();

        // Interfaces
        g.writeArrayFieldStart("interfaces");
        for (final Type interfaceDoc : doc.interfaceTypes()) {
            g.writeStartObject();
            writeTypeBasics(g, interfaceDoc);
            g.writeEndObject();
        }
        g.writeEndArray();


        // Write since tag
        {
            final Tag tag = get(doc.tags("since"), 0);
            g.writeObjectField("since", (tag != null) ? tag.text() : "");
        }

        // Write see tags
        g.writeArrayFieldStart("see");
        for (final SeeTag tag : doc.seeTags()) {
            g.writeString(tag.referencedClassName());
        }
        g.writeEndArray();


        // Write constructors
        g.writeArrayFieldStart("constructors");
        for (final ConstructorDoc ctorDoc : doc.constructors()) {
            writeConstructor(g, ctorDoc);
        }
        g.writeEndArray();

        // Write fields
        g.writeArrayFieldStart("fields");
        for (final FieldDoc fieldDoc : doc.fields()) {
            writeField(g, fieldDoc);
        }
        g.writeEndArray();

        // Write methods
        g.writeArrayFieldStart("methods");
        for (final MethodDoc methodDoc : doc.methods(false)) {
            writeMethod(g, methodDoc);
        }
        g.writeEndArray();

        g.writeEndObject();
    }

    static void writeConstructor(JsonGenerator g, ConstructorDoc doc)
            throws IOException {
        g.writeStartObject();

        // Basic information
        g.writeObjectField("name", doc.name());
        g.writeObjectField("comment", doc.commentText());

        // Write parameters
        g.writeArrayFieldStart("parameters");
        for (int i = 0; i < doc.parameters().length; ++i) {
            writeMethodParameter(g, doc.parameters()[i], doc.paramTags());
        }
        g.writeEndArray();

        // Write throws declarations
        g.writeArrayFieldStart("throws");
        for (int i = 0; i < doc.thrownExceptionTypes().length; ++i) {
            writeThrow(g, doc.thrownExceptionTypes()[i], doc.throwsTags());
        }
        g.writeEndArray();

        g.writeEndObject();
    }

    static void writeThrow(JsonGenerator g, Type type, ThrowsTag[] tags)
            throws IOException {
        final ThrowsTag tag = find(tags, type);

        g.writeStartObject();

        g.writeObjectField("comment", (tag != null) ? tag.exceptionComment() : "");

        writeTypeBasics(g, type);

        g.writeEndObject();
    }

    static void writeMethodParameter(JsonGenerator g, Parameter parameter, ParamTag[] tags)
            throws IOException {
        final ParamTag tag = find(tags, parameter);

        g.writeStartObject();

        g.writeObjectField("parameter", parameter.name());
        g.writeObjectField("comment", (tag != null) ? tag.parameterComment() : "");

        writeTypeBasics(g, parameter.type());

        g.writeEndObject();
    }

    static void writeField(JsonGenerator g, FieldDoc doc)
            throws IOException {
        g.writeStartObject();

        g.writeObjectField("field", doc.name());
        g.writeObjectField("comment", doc.commentText());

        writeTypeBasics(g, doc.type());

        g.writeEndObject();
    }

    static void writeMethod(JsonGenerator g, MethodDoc doc)
            throws IOException {
        g.writeStartObject();

        // Write basic information
        g.writeObjectField("name", doc.name());
        g.writeObjectField("comment", doc.commentText());
        g.writeObjectField("varargs", doc.isVarArgs());

        // Write return type
        g.writeObjectFieldStart("return");
        writeTypeBasics(g, doc.returnType());
        g.writeEndObject();


        // Write parameters
        g.writeArrayFieldStart("parameters");
        for (Parameter p : doc.parameters())
            writeMethodParameter(g, p, doc.paramTags());
        g.writeEndArray();

        // Write throws declarations
        g.writeArrayFieldStart("throws");
        for (int i = 0; i < doc.thrownExceptions().length; ++i) {
            writeThrow(g, doc.thrownExceptionTypes()[i], doc.throwsTags());
        }
        g.writeEndArray();


        // Check if the method overrides another method
        ClassDoc overridden = doc.overriddenClass();
        if (overridden != null) {
            g.writeObjectFieldStart("overrides");
            g.writeObjectFieldStart("class");
            writeClassBasics(g, overridden);
            g.writeEndObject();
            g.writeObjectField("method", doc.overriddenMethod().name());
            g.writeEndObject();
        }

        g.writeEndObject();
    }

    static void writeTypeBasics(JsonGenerator g, Type t)
            throws IOException {

        if (t == null)
            return;

        // Common properties
        g.writeObjectField("dimension", t.dimension());

        // If it's a primitive, skipp all other checks
        if (t.isPrimitive()) {
            g.writeObjectField("type", t.typeName());
            return;
        }

        // Type name
        g.writeObjectField("name", t.typeName());

        if (writeAnnotatedType(g, t.asAnnotatedType()))
            return;

        if (writeAnnotationType(g, t.asAnnotationTypeDoc()))
            return;

        if (writeParametrizedType(g, t.asParameterizedType()))
            return;

        if (writeWildcardType(g, t.asWildcardType()))
            return;

        if (writeTypeVariable(g, t.asTypeVariable()))
            return;

        if (writeClassBasics(g, t.asClassDoc()))
            return;

        throw new IllegalArgumentException("Unsupported type: " + t);
    }

    static boolean writeParametrizedType(JsonGenerator g, ParameterizedType t)
            throws IOException {
        if (t == null)
            return false;

        writeClassBasics(g, t.asClassDoc(), false);

        // Write generics parameters
        g.writeArrayFieldStart("genericTypes");
        for (Type type : t.typeArguments()) {
            g.writeStartObject();
            writeTypeBasics(g, type);
            g.writeEndObject();
        }
        g.writeEndArray();

        return true;
    }

    static boolean writeWildcardType(JsonGenerator g, WildcardType t)
            throws IOException {
        if (t == null)
            return false;

        g.writeObjectField("type", "wildcard");

        g.writeArrayFieldStart("extendsBounds");
        for (Type type : t.extendsBounds()) {
            g.writeStartObject();
            writeTypeBasics(g, type);
            g.writeEndObject();
        }
        g.writeEndArray();

        g.writeArrayFieldStart("superBounds");
        for (Type type : t.superBounds()) {
            g.writeStartObject();
            writeTypeBasics(g, type);
            g.writeEndObject();
        }
        g.writeEndArray();

        return true;
    }

    static boolean writeTypeVariable(JsonGenerator g, TypeVariable v)
            throws IOException {
        if (v == null)
            return false;

        g.writeObjectField("type", "generic");
        g.writeArrayFieldStart("bounds");
        for (Type t : v.bounds()) {
            g.writeStartObject();
            writeTypeBasics(g, t);
            g.writeEndObject();
        }
        g.writeEndArray();

        return true;
    }

    static boolean writeAnnotatedType(JsonGenerator g, AnnotatedType t)
            throws IOException {

        if (t == null)
            return false;

        g.writeObjectField("type", "annotation");

        throw new UnsupportedOperationException("Annotation type not supported yet: " + t);
    }

    static boolean writeAnnotationType(JsonGenerator g, AnnotationTypeDoc d)
    throws IOException {
        if (d == null)
            return false;

        throw new UnsupportedOperationException("");
    }

    static boolean writeClassBasics(JsonGenerator g, ClassDoc c)
            throws IOException{
        return writeClassBasics(g, c, true);
    }

    static boolean writeClassBasics(JsonGenerator g, ClassDoc c, boolean generics)
            throws IOException {
        if (c == null)
            return false;

        if (c.isInterface()) {
            g.writeObjectField("type", "interface");
        } else if (c.isEnum()) {
            g.writeObjectField("type", "enum");
        } else if (c.isException()) {
            g.writeObjectField("type", "exception");
        } else if (c.isError()) {
            g.writeObjectField("type", "error");
        } else if (c.isOrdinaryClass()) {
            g.writeObjectField("type", "class");
        } else {
            throw new IllegalArgumentException("Unsupported class: " + c);
        }

        // Write package
        g.writeObjectField("package", c.containingPackage().name());

        if (generics) {
            // Write generics parameters
            g.writeArrayFieldStart("genericTypes");
            for (TypeVariable t : c.typeParameters()) {
                g.writeStartObject();
                writeTypeBasics(g, t);
                g.writeEndObject();
            }
            g.writeEndArray();
        }

        return true;
    }

    static <T> T get(T[] elements, int i) {
        if (i < 0) {
            throw new IllegalArgumentException();
        }

        if (i < elements.length) {
            return elements[i];
        } else {
            return null;
        }
    }

    static ThrowsTag find(ThrowsTag[] tags, Type type) {
        for (final ThrowsTag tag : tags)
            if (type.equals(tag.exceptionType()))
                return tag;

        return null;
    }

    static ParamTag find(ParamTag[] tags, Parameter param) {
        for (final ParamTag tag : tags)
            if (param.name().equals(tag.parameterName()))
                return tag;

        return null;
    }

    @Override
    public boolean equals(Object obj) {
        return super.equals(obj);
    }
}
