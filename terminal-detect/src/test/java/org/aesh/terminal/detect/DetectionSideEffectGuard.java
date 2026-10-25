package org.aesh.terminal.detect;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassVisitor;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

/**
 * Instruments only isolated test copies. JVM class loading and this harness's
 * own resource reads are outside the library-operation budget.
 */
final class DetectionSideEffectGuard extends URLClassLoader {

    private static final String PACKAGE = "org.aesh.terminal.detect.";
    private final String fixture;
    final Set<String> instrumented = new HashSet<>();

    DetectionSideEffectGuard(Class<?> fixture) {
        super(new URL[] {
                fixture.getProtectionDomain().getCodeSource().getLocation(),
                TerminalCapabilities.class.getProtectionDomain().getCodeSource().getLocation()
        }, fixture.getClassLoader());
        this.fixture = fixture.getName();
    }

    Runnable probe(String scenario) throws Exception {
        return (Runnable) loadClass(fixture, false).getConstructor(String.class).newInstance(scenario);
    }

    @Override
    protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
        if (!name.startsWith(PACKAGE) || name.equals(Effects.class.getName())
                || (name.contains("Test$") && !name.startsWith(fixture))) {
            return super.loadClass(name, resolve);
        }
        Class<?> type = findLoadedClass(name);
        if (type == null) {
            type = findClass(name);
        }
        if (resolve) {
            resolveClass(type);
        }
        return type;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        URL resource = findResource(name.replace('.', '/') + ".class");
        if (resource == null) {
            throw new ClassNotFoundException(name);
        }
        try (InputStream input = resource.openStream();
                ByteArrayOutputStream bytes = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) != -1) {
                bytes.write(buffer, 0, count);
            }
            byte[] code = instrument(name, bytes.toByteArray());
            instrumented.add(name);
            return defineClass(name, code, 0, code.length);
        } catch (IOException e) {
            throw new ClassNotFoundException(name, e);
        }
    }

    private static byte[] instrument(String name, byte[] code) {
        ClassReader reader = new ClassReader(code);
        ClassWriter writer = new ClassWriter(reader, ClassWriter.COMPUTE_MAXS);
        reader.accept(new ClassVisitor(Opcodes.ASM9, writer) {
            @Override
            public MethodVisitor visitMethod(int access, String method, String descriptor, String signature,
                    String[] exceptions) {
                MethodVisitor delegate = super.visitMethod(access, method, descriptor, signature, exceptions);
                return new MethodVisitor(Opcodes.ASM9, delegate) {
                    @Override
                    public void visitCode() {
                        super.visitCode();
                        if ("<clinit>".equals(method) && (name.startsWith(PACKAGE + "Ffm")
                                || name.startsWith(PACKAGE + "Win32"))) {
                            check("native");
                        }
                    }

                    @Override
                    public void visitMethodInsn(int opcode, String owner, String target, String desc, boolean iface) {
                        String kind = sideEffect(owner, target);
                        if (kind != null) {
                            check(kind);
                        }
                        super.visitMethodInsn(opcode, owner, target, desc, iface);
                    }

                    private void check(String kind) {
                        super.visitLdcInsn(kind);
                        super.visitMethodInsn(Opcodes.INVOKESTATIC, Effects.class.getName().replace('.', '/'),
                                "check", "(Ljava/lang/String;)V", false);
                    }
                };
            }
        }, 0);
        return writer.toByteArray();
    }

    /** The operation catalog is checked by executable canaries in the test. */
    private static String sideEffect(String owner, String method) {
        String kind = controlEffect(owner, method);
        if (kind == null) {
            kind = ioEffect(owner, method);
        }
        return kind == null ? asyncEffect(owner, method) : kind;
    }

    private static String controlEffect(String owner, String method) {
        if ("java/lang/System".equals(owner)) {
            if ("getenv".equals(method))
                return "environment";
            if ("getProperty".equals(method) || "getProperties".equals(method))
                return "properties";
            if ("load".equals(method) || "loadLibrary".equals(method))
                return "native";
        }
        if ("org/aesh/terminal/detect/TerminalCapabilities".equals(owner)) {
            if ("awaitColors".equals(method))
                return "wait";
            if ("getInstance".equals(method) || "setInstance".equals(method) || "invalidate".equals(method)
                    || "detectFull".equals(method) || "detectAsync".equals(method) || "onThemeChanged".equals(method)
                    || "setProbeTransport".equals(method))
                return "shared";
        }
        return null;
    }

    private static String ioEffect(String owner, String method) {
        if (owner.equals("java/lang/ProcessBuilder") && method.startsWith("start"))
            return "process";
        if (owner.equals("org/aesh/terminal/detect/ProcessRunner") && method.equals("execute"))
            return "process";
        if (owner.equals("java/lang/Runtime")) {
            if (method.equals("exec"))
                return "process";
            if (method.equals("load") || method.equals("loadLibrary"))
                return "native";
        }
        if (owner.startsWith("java/lang/foreign/"))
            return "native";
        if (owner.equals("org/aesh/terminal/detect/TerminalProbeTransport")
                || owner.equals("org/aesh/terminal/detect/TerminalProbeSession")
                || (owner.equals("org/aesh/terminal/detect/TerminalColorQuery")
                        && (method.startsWith("query") || method.startsWith("probe") || method.startsWith("load"))))
            return "terminal";
        if (isFileAccess(owner, method))
            return "filesystem";
        return isStreamAccess(owner, method) ? "terminal" : null;
    }

    private static boolean isFileAccess(String owner, String method) {
        return owner.startsWith("java/nio/file/Files") || owner.equals("java/nio/file/FileSystems")
                || owner.equals("java/nio/channels/FileChannel") || owner.equals("java/nio/channels/AsynchronousFileChannel")
                || (owner.equals("java/io/File") && !method.equals("<init>"))
                || owner.equals("java/io/FileInputStream") || owner.equals("java/io/FileOutputStream")
                || owner.equals("java/io/FileReader") || owner.equals("java/io/FileWriter")
                || owner.equals("java/io/RandomAccessFile")
                || ((owner.equals("java/lang/Class") || owner.equals("java/lang/ClassLoader"))
                        && method.contains("Resource"));
    }

    private static boolean isStreamAccess(String owner, String method) {
        return (owner.equals("java/io/InputStream") || owner.equals("java/io/Reader")
                || owner.equals("java/io/OutputStream") || owner.equals("java/io/Writer")
                || owner.equals("java/io/PrintStream") || owner.equals("java/io/PrintWriter"))
                && (method.startsWith("read") || method.startsWith("write") || method.startsWith("print")
                        || method.equals("append") || method.equals("flush") || method.equals("close"));
    }

    private static String asyncEffect(String owner, String method) {
        if ((owner.equals("java/lang/Thread") && (method.equals("<init>") || method.equals("start")))
                || owner.equals("java/util/concurrent/Executors")
                || (owner.equals("java/util/concurrent/ThreadFactory") && method.equals("newThread"))
                || (owner.startsWith("java/util/concurrent/") && (method.equals("execute") || method.equals("submit")
                        || method.equals("runAsync") || method.equals("supplyAsync"))))
            return "worker";
        if ((owner.equals("java/lang/Thread") && (method.equals("sleep") || method.equals("join")))
                || (owner.equals("java/lang/Object") && method.equals("wait"))
                || (owner.startsWith("java/util/concurrent/") && (method.equals("await")
                        || method.equals("acquire") || method.equals("lock") || method.equals("lockInterruptibly")))
                || ((owner.equals("java/util/concurrent/Future") || owner.equals("java/util/concurrent/FutureTask")
                        || owner.equals("java/util/concurrent/CompletableFuture")
                        || owner.equals("java/util/concurrent/ForkJoinTask"))
                        && (method.equals("get") || method.equals("join"))))
            return "wait";
        return null;
    }

    /** Public so instrumented classes in another loader can invoke the spy. */
    public static final class Effects {
        private static final ThreadLocal<Effects> ACTIVE = new ThreadLocal<>();
        final List<String> violations = new ArrayList<>();
        private final boolean environment;
        private final boolean properties;

        private Effects(boolean environment, boolean properties) {
            this.environment = environment;
            this.properties = properties;
        }

        static Effects begin(boolean environment, boolean properties) {
            Effects effects = new Effects(environment, properties);
            ACTIVE.set(effects);
            return effects;
        }

        static void end() {
            ACTIVE.remove();
        }

        /** Fail before an operation; retain evidence even if callers catch it. */
        public static void check(String kind) {
            Effects effects = ACTIVE.get();
            if (effects == null || ("environment".equals(kind) && effects.environment)
                    || ("properties".equals(kind) && effects.properties)) {
                return;
            }
            effects.violations.add(kind);
            throw new AssertionError("forbidden " + kind);
        }
    }
}
