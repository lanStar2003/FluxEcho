package com.fluxecho;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;

/**
 * Compiles a single-file program from {@code tools/} and calls it, so that a test checks a generated file against the
 * very code that generates it (not against a copy of that code in the test). The tools are run by hand with
 * {@code java tools/X.java}, outside Gradle; here each one is compiled once per test JVM into
 * {@code build/tmp/tools-test/X}.
 */
public final class Tools {

    private static final Map<String, Class<?>> LOADED = new HashMap<>();

    private Tools() {}

    /** The class of {@code tools/<name>.java}, compiled on first use. */
    public static synchronized Class<?> load(String name) {
        Class<?> c = LOADED.get(name);
        if (c != null) return c;
        JavaCompiler javac = ToolProvider.getSystemJavaCompiler();
        if (javac == null) throw new IllegalStateException("tests need a JDK to compile tools/" + name + ".java");
        File out = new File("build/tmp/tools-test/" + name);
        if (!out.isDirectory() && !out.mkdirs()) throw new IllegalStateException("cannot make " + out);
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int status = javac.run(
            null,
            null,
            err,
            "-encoding",
            "UTF-8",
            "-proc:none",
            "-nowarn",
            "-d",
            out.getPath(),
            "tools/" + name + ".java");
        if (status != 0) throw new IllegalStateException(
            "tools/" + name + ".java does not compile:\n" + new String(err.toByteArray(), StandardCharsets.UTF_8));
        try {
            // the loader stays open for the rest of the test JVM, like the class it loaded
            @SuppressWarnings("resource")
            URLClassLoader loader = new URLClassLoader(
                new URL[] { out.toURI()
                    .toURL() },
                Tools.class.getClassLoader());
            c = Class.forName(name, true, loader);
        } catch (Exception e) {
            throw new IllegalStateException("cannot load tools/" + name + ".java", e);
        }
        LOADED.put(name, c);
        return c;
    }

    /** Calls a public static method of a tool. */
    public static Object call(String tool, String method, Class<?>[] types, Object... args) {
        try {
            Method m = load(tool).getMethod(method, types);
            return m.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable t = e.getCause();
            if (t instanceof RuntimeException r) throw r;
            throw new IllegalStateException(tool + "." + method + " failed", t);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(tool + "." + method + " is missing", e);
        }
    }
}
