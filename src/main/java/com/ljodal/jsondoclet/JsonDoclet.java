package com.ljodal.jsondoclet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Arrays;
import java.util.List;

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

    private static final List<String> ignoredTags = Arrays.asList(
            "@see",
            "@since",
            "@param",
            "@throws", "@exception",
            "@return"
    );

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

        // Write program element basics
        writeProgramElement(g, doc);


        // Superclass, unless this is an enum
        if (!doc.isEnum()) {
            g.writeObjectFieldStart("extends");
            writeTypeBasics(g, doc.superclassType());
            g.writeEndObject();
        }

        // Interfaces
        g.writeArrayFieldStart("interfaces");
        for (final Type interfaceDoc : doc.interfaceTypes()) {
            g.writeStartObject();
            writeTypeBasics(g, interfaceDoc);
            g.writeEndObject();
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
            g.writeStartObject();
            writeMethod(g, methodDoc);
            g.writeEndObject();
        }
        g.writeEndArray();

        // Write enum fields, if this is an enum
        if (doc.isEnum()) {
            g.writeArrayFieldStart("enumConstants");
            for (final FieldDoc field : doc.enumConstants()) {
                writeField(g, field);
            }
            g.writeEndArray();
        }

        if (doc instanceof AnnotationTypeDoc) {
            g.writeArrayFieldStart("elements");
            for (final AnnotationTypeElementDoc e :
                    ((AnnotationTypeDoc) doc).elements()) {
                g.writeStartObject();
                writeAnnotationTypeElement(g, e);
                g.writeEndObject();
            }
            g.writeEndArray();
        }

        g.writeEndObject();
    }

    static void writeConstructor(JsonGenerator g, ConstructorDoc doc)
            throws IOException {
        g.writeStartObject();

        // Write executable member basics
        writeExecutableMember(g, doc);

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

        // Annotations
        g.writeArrayFieldStart("annotations");
        for (final AnnotationDesc a : parameter.annotations())
            writeAnnotationDesc(g, a);
        g.writeEndArray();

        g.writeEndObject();
    }

    static void writeField(JsonGenerator g, FieldDoc doc)
            throws IOException {
        g.writeStartObject();

        // Write doc basics
        writeMember(g, doc);

        // Only write type information if this is not an
        // enum constant
        if (!doc.isEnumConstant()) {
            writeTypeBasics(g, doc.type());
        }

        g.writeEndObject();
    }

    static void writeMethod(JsonGenerator g, MethodDoc doc)
            throws IOException {

        // Write program element basics
        writeExecutableMember(g, doc);

        // Write return type
        g.writeObjectFieldStart("return");
        writeTypeBasics(g, doc.returnType());
        g.writeEndObject();

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
    }

    static void writeAnnotationTypeElement(JsonGenerator g, AnnotationTypeElementDoc doc)
            throws IOException {

        writeMethod(g, doc);

        g.writeObjectField("defaultValue", doc.defaultValue().value());
    }

    static void writeDoc(JsonGenerator g, Doc doc)
            throws IOException {

        // Common properties
        g.writeObjectField("name", doc.name());
        g.writeObjectField("comment", doc.commentText());

        // Write see tags
        g.writeArrayFieldStart("see");
        for (final SeeTag tag : doc.seeTags()) {
            g.writeString(tag.referencedClassName());
        }
        g.writeEndArray();

        // Write since tag
        {
            final Tag tag = get(doc.tags("since"), 0);
            g.writeObjectField("since", (tag != null) ? tag.text() : "");
        }

        // Other tags
        g.writeArrayFieldStart("tags");
        for (final Tag t : doc.tags()) {
            String kind = t.kind();
            if (ignoredTags.contains(kind)) {
                continue;
            }

            // Write tag name and value
            g.writeStartObject();
            g.writeObjectField("name", t.kind());
            g.writeObjectField("value", t.text());
            g.writeEndObject();
        }
        g.writeEndArray();
    }

    static void writeProgramElement(JsonGenerator g, ProgramElementDoc doc)
            throws IOException {

        // Write doc basics, like name, comment, and tags
        writeDoc(g, doc);

        // Annotations
        g.writeArrayFieldStart("annotations");
        for (final AnnotationDesc a : doc.annotations())
            writeAnnotationDesc(g, a);
        g.writeEndArray();

        // Write package
        g.writeObjectField("package", doc.containingPackage().name());

        // Write modifiers
        // TODO: Write boolean fields?
        g.writeArrayFieldStart("modifiers");
        for (String s : doc.modifiers().split(" ")) {
            g.writeString(s);
        }
        g.writeEndArray();

        // TODO: Containing class
    }

    static void writeMember(JsonGenerator g, MemberDoc doc)
            throws IOException {

        // Write program element basics
        writeProgramElement(g, doc);

        // TODO: Write syntetic information

    }

    static void writeExecutableMember(JsonGenerator g, ExecutableMemberDoc doc)
            throws IOException {

        // Write member basics
        writeMember(g, doc);

        // Write parameters
        g.writeArrayFieldStart("parameters");
        for (final Parameter p : doc.parameters())
            writeMethodParameter(g, p, doc.paramTags());
        g.writeEndArray();

        // Write throws declarations
        g.writeArrayFieldStart("throws");
        for (final Type t : doc.thrownExceptionTypes()) {
            writeThrow(g, t, doc.throwsTags());
        }
        g.writeEndArray();

        // Is var args?
        g.writeObjectField("varargs", doc.isVarArgs());
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
        g.writeObjectField("class", t.typeName());

        if (writeAnnotationType(g, t.asAnnotationTypeDoc()))
            return;

        if (writeAnnotatedType(g, t.asAnnotatedType()))
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

    static boolean writeAnnotationType(JsonGenerator g, AnnotationTypeDoc a)
            throws IOException {
        if (a == null)
            return false;

        g.writeObjectField("type", "annotation");

        // Write package
        g.writeObjectField("package", a.containingPackage().name());

        // Write elements
        g.writeArrayFieldStart("elements");
        for (final AnnotationTypeElementDoc e : a.elements()) {
            g.writeStartObject();
            g.writeObjectField("name", e.name());
            g.writeObjectField("defaultValue", e.defaultValue().toString());
            g.writeEndObject();
        }
        g.writeEndArray();

        return true;
    }

    static boolean writeClassBasics(JsonGenerator g, ClassDoc c)
            throws IOException {
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
        } else if (c.isAnnotationType()) {
            g.writeObjectField("type", "annotation");
        } else {
            throw new IllegalArgumentException("Unsupported class: " + c);
        }

        // Write package
        g.writeObjectField("package", c.containingPackage().name());

        // Only write generics if we are asked to
        if (generics && !c.isAnnotationType()) {
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

    static void writeAnnotationDesc(JsonGenerator g, AnnotationDesc a)
            throws IOException {
        g.writeStartObject();

        final AnnotationTypeDoc t = a.annotationType();

        // Annotation information
        g.writeObjectFieldStart("annotation");
        writeTypeBasics(g, t);
        g.writeEndObject();

        // Write elements
        g.writeArrayFieldStart("elements");
        for (final AnnotationDesc.ElementValuePair v : a.elementValues()) {
            g.writeStartObject();
            g.writeObjectField("name", v.element().name());
            g.writeObjectField("value", v.value().value());
            g.writeEndObject();
        }
        g.writeEndArray();

        g.writeEndObject();
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
