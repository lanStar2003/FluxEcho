package com.fluxecho.nei;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import codechicken.nei.recipe.TemplateRecipeHandler;

/**
 * NEI answers every lookup with a copy of each recipe handler, made through {@code newInstance()}, which by default
 * calls a constructor without arguments. A handler without one fails every lookup ("error loading recipes"), so each
 * of ours must have that constructor or its own {@code newInstance()}.
 */
class HandlerCopyTest {

    @Test
    void everyHandlerCanBeCopied() throws IOException, ClassNotFoundException {
        Path root = Paths.get("build", "classes", "java", "main");
        assertTrue(Files.isDirectory(root), "compiled classes not found at " + root.toAbsolutePath());
        List<String> broken = new ArrayList<>(), seen = new ArrayList<>();
        List<Path> files;
        try (Stream<Path> s = Files.walk(root)) {
            files = new ArrayList<>();
            s.filter(
                p -> p.toString()
                    .endsWith(".class")
                    && !p.getFileName()
                        .toString()
                        .contains("$"))
                .forEach(files::add);
        }
        ClassLoader loader = getClass().getClassLoader();
        for (Path f : files) {
            String name = root.relativize(f)
                .toString()
                .replace(java.io.File.separatorChar, '/')
                .replace('/', '.')
                .replaceAll("[.]class$", "");
            if (!name.startsWith("com.fluxecho.")) continue;
            Class<?> c;
            try {
                c = Class.forName(name, false, loader);
                if (!TemplateRecipeHandler.class.isAssignableFrom(c)) continue;
            } catch (LinkageError e) {
                continue; // a class of an optional mod's module
            }
            seen.add(name);
            boolean noArgs;
            try {
                c.getConstructor();
                noArgs = true;
            } catch (NoSuchMethodException e) {
                noArgs = false;
            }
            boolean ownCopy;
            try {
                ownCopy = c.getDeclaredMethod("newInstance")
                    .getDeclaringClass() == c;
            } catch (NoSuchMethodException e) {
                ownCopy = false;
            }
            if (!noArgs && !ownCopy) broken.add(name);
        }
        assertTrue(seen.contains("com.fluxecho.nei.FluxRecipeHandler"), "the echo machines' handler was checked");
        assertTrue(broken.isEmpty(), "handlers NEI cannot copy: " + broken);
    }
}
