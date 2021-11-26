package ru.taximaxim.codekeeper.apgdiff;

import org.eclipse.core.runtime.FileLocator;
import org.eclipse.core.runtime.URIUtil;
import ru.taximaxim.codekeeper.apgdiff.log.Log;

import java.io.*;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public final class ApgdiffUtils {

    /**
     * @param url url should NOT be URL-encoded
     */
    public static Path getFileFromOsgiRes(URL url) throws URISyntaxException, IOException {
        return Paths.get(
                URIUtil.toURI("file".equals(url.getProtocol()) ?
                        url : FileLocator.toFileURL(url)));
    }


    public static void serialize(String path, Serializable object) {
        serialize(Paths.get(path), object);
    }

    /**
     * Serializes object
     *
     * @param path - full path to file where the serialized object will be
     * @param object - the object that you want to serialize
     */
    public static void serialize(Path path, Serializable object) {
        try {
            if (Files.notExists(path)) {
                Files.createDirectories(path.getParent());
            }
            try (ObjectOutputStream oos = new ObjectOutputStream(Files.newOutputStream(path))) {
                oos.writeObject(object);
                oos.flush();
            }
        } catch (IOException e) {
            Log.log(Log.LOG_DEBUG, "Error while serialize object!", e);
        }
    }

    /**
     * Deserializes object
     *
     * @param path - full path to the serialized file
     *
     * @return deserialized object or null if not found
     */
    public static Object deserialize(Path path) {
        try {
            if (Files.exists(path)) {
                try (ObjectInputStream oin = new ObjectInputStream(Files.newInputStream(path))) {
                    return oin.readObject();
                }
            }
        } catch (ClassNotFoundException | IOException e) {
            Log.log(Log.LOG_DEBUG, "Error while deserialize object!", e);
        }

        return null;
    }

    public static Object deserialize(InputStream inputStream) {
        if(inputStream == null){
            return null;
        }
        try (ObjectInputStream oin = new ObjectInputStream(inputStream)) {
            return oin.readObject();
        } catch (ClassNotFoundException | IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static boolean isSystemSchema(String schema, boolean isPostgres) {
        return isPostgres ? isPgSystemSchema(schema) : isMsSystemSchema(schema);
    }

    public static boolean isPgSystemSchema(String schema) {
        return ApgdiffConsts.PG_CATALOG.equalsIgnoreCase(schema)
                || ApgdiffConsts.INFORMATION_SCHEMA.equalsIgnoreCase(schema);
    }

    public static boolean isMsSystemSchema(String schema) {
        return ApgdiffConsts.SYS.equalsIgnoreCase(schema);
    }

    private ApgdiffUtils() {
    }
}
