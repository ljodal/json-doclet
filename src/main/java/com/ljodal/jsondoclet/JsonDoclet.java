package com.ljodal.jsondoclet;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

import com.fasterxml.jackson.core.JsonFactory;
import com.fasterxml.jackson.core.JsonGenerator;
import com.sun.javadoc.ClassDoc;
import com.sun.javadoc.ConstructorDoc;
import com.sun.javadoc.FieldDoc;
import com.sun.javadoc.LanguageVersion;
import com.sun.javadoc.MethodDoc;
import com.sun.javadoc.ParamTag;
import com.sun.javadoc.Parameter;
import com.sun.javadoc.RootDoc;
import com.sun.javadoc.SeeTag;
import com.sun.javadoc.Tag;
import com.sun.javadoc.ThrowsTag;
import com.sun.javadoc.Type;


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
        try {
            JsonDoclet doclet = new JsonDoclet(root);
            return true;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Private constructor for generating json.
     *
     * @param root The root doc object
     */
    private JsonDoclet(RootDoc root) throws IOException {
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
                if (out != null)
                    out.close();
                if (json != null)
                    json.close();
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

        // Basic information
        g.writeObjectField("name", doc.qualifiedName());
        {
            g.writeArrayFieldStart("interfaces");

            for (final ClassDoc interfaceDoc : doc.interfaces()) {
                g.writeString(interfaceDoc.qualifiedTypeName());
            }

            g.writeEndArray();
        }

        g.writeObjectField("superclass", (doc.superclassType() != null) ? doc.superclassType().qualifiedTypeName() : "");
        g.writeObjectField("comment", doc.commentText());

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

        for (final MethodDoc methodDoc : doc.methods()) {
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

        g.writeObjectField("name", type.qualifiedTypeName());
        g.writeObjectField("comment", (tag != null) ? tag.exceptionComment() : "");

        g.writeEndObject();
    }

    static void writeMethodParameter(JsonGenerator g, Parameter parameter, ParamTag[] tags)
            throws IOException {
        final ParamTag tag = find(tags, parameter);

        g.writeStartObject();

        g.writeObjectField("name", parameter.name());
        g.writeObjectField("comment", (tag != null) ? tag.parameterComment() : "");
        g.writeObjectField("type", parameter.type().qualifiedTypeName());

        g.writeEndObject();
    }

    static void writeField(JsonGenerator g, FieldDoc doc)
            throws IOException {
        g.writeStartObject();

        g.writeObjectField("name", doc.name());
        g.writeObjectField("comment", doc.commentText());
        g.writeObjectField("type", doc.type().qualifiedTypeName());

        g.writeEndObject();
    }

    static void writeMethod(JsonGenerator g, MethodDoc doc)
            throws IOException {
        g.writeStartObject();

        g.writeObjectField("name", doc.name());
        g.writeObjectField("comment", doc.commentText());
        g.writeObjectField("returnType", doc.returnType().qualifiedTypeName());

        {
            g.writeArrayFieldStart("parameters");

            for (int i = 0; i < doc.parameters().length; ++i) {
                writeMethodParameter(g, doc.parameters()[i], doc.paramTags());
            }

            g.writeEndArray();
        }
        {
            g.writeArrayFieldStart("throws");

            for (int i = 0; i < doc.thrownExceptions().length; ++i) {
                writeThrow(g, doc.thrownExceptionTypes()[i], doc.throwsTags());
            }

            g.writeEndArray();
        }

        // Check if the method overrides another method
        ClassDoc overridden = doc.overriddenClass();
        if (overridden != null) {
            g.writeObjectFieldStart("overrides");
            g.writeObjectField("class", overridden.qualifiedName());
            g.writeObjectField("method", doc.overriddenMethod().name());
            g.writeEndObject();
        }

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
}
