package th.ac.kmitl.connect.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Keeps images in a folder. Good enough for one backend container on a laptop,
 * but it breaks as soon as there are two Pods (each would see different files)
 * — which is exactly why the cloud deployment uses Object Storage instead.
 */
@Component
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalMediaStorage implements MediaStorage {

    private final Path root;

    public LocalMediaStorage(@Value("${app.storage.local-dir}") String dir) {
        this.root = Path.of(dir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot create media folder " + root, e);
        }
    }

    @Override
    public void put(String objectName, byte[] data, String contentType) {
        Path target = resolve(objectName);
        try {
            Path tmp = Files.createTempFile(root, ".upload-", ".tmp");
            Files.write(tmp, data);
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot store " + objectName, e);
        }
    }

    @Override
    public void delete(String objectName) {
        try {
            Files.deleteIfExists(resolve(objectName));
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot delete " + objectName, e);
        }
    }

    @Override
    public String publicUrl(String objectName) {
        return "/api/media/" + objectName;
    }

    /** Returns the file for a valid object name, or null. */
    public Path find(String objectName) {
        if (!ObjectNames.isValid(objectName)) return null;
        Path p = resolve(objectName);
        return Files.isRegularFile(p) ? p : null;
    }

    private Path resolve(String objectName) {
        if (!ObjectNames.isValid(objectName)) {
            throw new IllegalArgumentException("Invalid object name");
        }
        Path p = root.resolve(objectName).normalize();
        if (!p.startsWith(root)) {
            throw new IllegalArgumentException("Invalid object name");
        }
        return p;
    }
}
