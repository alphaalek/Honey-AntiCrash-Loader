package me.alek.honey;

import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.UUID;

public final class Boot extends JavaPlugin {

    private static final String METHOD_HANDLE_ORIGIN_NAME = "me.alek.honey.linker.HoneyVerifier";
    private static final MethodHandles.Lookup LOOKUP = MethodHandles.lookup();
    private static final ParserClassLoader PARSER = new ParserClassLoader(Boot.class.getClassLoader());
    private static Plugin PLUGIN;

    private String LICENSE;
    private String VERSION;
    private boolean VERBOSE;

    private MethodHandle cachedLoadingInvoker;

    @Override
    public void onEnable() {
        PLUGIN = this;

        PLUGIN.saveDefaultConfig();
        LICENSE = PLUGIN.getConfig().getString("license");
        VERSION = PLUGIN.getConfig().getString("version");
        VERBOSE = PLUGIN.getConfig().getBoolean("verbose");

        setup();
    }

    public static Plugin getPlugin() {
        return PLUGIN;
    }

    public static ParserClassLoader getParserClassLoader() {
        return PARSER;
    }

    public static class ParserClassLoader extends URLClassLoader {


        public ParserClassLoader(ClassLoader parent) {
            super(new URL[]{}, parent);
        }

        public Class<?> defineLibraryClass(String className, byte[] data) {
            return defineClass(className, data, 0, data.length);
        }
    }

    private static class InvalidLicenseException extends RuntimeException {

        @Override
        public String getMessage() {
            return "You have an invalid license in your config.yml! Remember to put in your license key to load the plugin.";
        }
    }

    public static class InvalidSaltException extends Exception {

    }

    public void setup() {
        HttpURLConnection connection = getConnection();
        try {
            if (connection != null) {

                if (connection.getResponseCode() != HttpURLConnection.HTTP_OK) {
                    throw new InvalidLicenseException();
                }

                UUID salt = UUID.fromString(connection.getHeaderField("salt"));

                PLUGIN.getLogger().info("Authorized access! You have a valid license key.");
                PLUGIN.getLogger().info("Key: " + LICENSE + " Verification: " + salt);
                PLUGIN.getLogger().info("Loading libraries, please wait...");

                defineAndExecuteInvoker(connection.getInputStream(), salt);

                return;
            }
        } catch (Throwable ex) {
            ex.printStackTrace();
        }
        PLUGIN.getLogger().severe("Error occurred when downloading linker jar.");
    }

    private HttpURLConnection getConnection() {
        if (LICENSE.isEmpty()) throw new InvalidLicenseException();

        try {
            URL url = new URL("http://178.128.196.32:4000/api/v1/license/download?key=" + LICENSE);

            return (HttpURLConnection) url.openConnection();
        } catch (IOException ex) {
            ex.printStackTrace();
            PLUGIN.getLogger().severe("Error occurred when connection to license auth servers!");
        }
        return null;
    }

    public synchronized void defineAndExecuteInvoker(InputStream stream, UUID salt) throws Throwable {
        if (cachedLoadingInvoker == null) {
            setupInvoker(stream);
        }

        cachedLoadingInvoker.invokeExact(salt, VERSION, VERBOSE);
    }

    public void setupInvoker(InputStream stream) throws IOException, NoSuchMethodException, IllegalAccessException {
        if (cachedLoadingInvoker != null) return;

        if (VERBOSE) PLUGIN.getLogger().info("Loading verification class data...");
        byte[] data = loadClassData(stream);

        if (VERBOSE) PLUGIN.getLogger().info("Parsing method handle...");
        cachedLoadingInvoker = parseDataToMethodHandle(METHOD_HANDLE_ORIGIN_NAME, data);
    }

    public MethodHandle parseDataToMethodHandle(String className, byte[] data) throws NoSuchMethodException, IllegalAccessException {
        Class<?> clazz = PARSER.defineLibraryClass(className, data);

        MethodHandles.Lookup lookup = LOOKUP.in(clazz);
        MethodType type = MethodType.methodType(void.class, UUID.class, String.class, boolean.class);

        return lookup.findStatic(clazz, "load", type);
    }

    public byte[] loadClassData(InputStream inputStream) throws IOException {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();

        byte[] buffer = new byte[1024];
        int len;
        while ((len = inputStream.read(buffer)) != -1) {
            byteArrayOutputStream.write(buffer, 0, len);
        }
        return byteArrayOutputStream.toByteArray();
    }
}
