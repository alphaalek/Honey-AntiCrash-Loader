package me.alek.honey;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public final class Boot extends JavaPlugin {

    private static Plugin plugin;

    private static String LICENSE;
    private static String VERSION;

    @Override
    public void onEnable() {
        plugin = this;

        plugin.saveDefaultConfig();
        LICENSE = plugin.getConfig().getString("license");
        VERSION = plugin.getConfig().getString("version");

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

    private static class InvalidLicenseException extends RuntimeException {

        @Override
        public String getMessage() {
            return "You have an invalid license in your config.yml! Remember to put in your license key to load the plugin.";
        }
    }

    private static final String LINKER_CLASS = "me.alek.honey.linker.HoneyLinker";

    public static void setup(ClassLoader classLoader) {
        HttpURLConnection connection = getConnection();
        try {
            if (connection != null) {

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new InvalidLicenseException();
                }

                UUID salt = UUID.fromString(connection.getHeaderField("salt"));
                InputStream stream = connection.getInputStream();

                plugin.getLogger().info("Authorized access! You have a valid license key.");
                plugin.getLogger().info("Key: " + LICENSE + " Verification: " + salt);
                plugin.getLogger().info("Invoking honey linker...");

                JavaPluginLoader loader = load(stream, classLoader);
                invokeLinkerClass(loader, salt);

                return;
            }
        } catch (IOException ex) {
            ex.printStackTrace();
        }
        plugin.getLogger().severe("Error occurred when downloading linker jar.");
    }

    private static HttpURLConnection getConnection() {
        if (LICENSE.isEmpty()) throw new InvalidLicenseException();

        try {
            URL url = new URL("http://178.128.196.32:4000/api/v1/license/download?key=" + LICENSE);

            return (HttpURLConnection) url.openConnection();
        } catch (IOException ex) {
            ex.printStackTrace();
            plugin.getLogger().severe("Error occurred when connection to license auth servers!");
        }
        return null;
    }

    public static JavaPluginLoader load(InputStream stream, ClassLoader currentLoader) {
        Map<String, byte[]> classes = loadClasses(stream);

        return new JavaPluginLoader(currentLoader, classes);
    }

    public static void invokeLinkerClass(JavaPluginLoader loader, UUID salt) {
        try {
            Class<?> mainClass = loader.loadClass(LINKER_CLASS);

            Object main = mainClass.getDeclaredConstructor(Plugin.class, UUID.class).newInstance(plugin, salt);
            mainClass.getMethod("execute", String.class).invoke(main, VERSION);

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
