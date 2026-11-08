package th.ac.kmitl.connect.controller;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import th.ac.kmitl.connect.storage.LocalMediaStorage;

import java.nio.file.Path;
import java.time.Duration;

/**
 * Serves images when STORAGE_TYPE=local (docker compose / minikube).
 * On Google Cloud this controller does not exist: browsers load images straight
 * from Cloud Storage and the backend never spends CPU on them.
 */
@RestController
@ConditionalOnProperty(name = "app.storage.type", havingValue = "local", matchIfMissing = true)
public class LocalMediaController {

    private final LocalMediaStorage storage;

    public LocalMediaController(LocalMediaStorage storage) {
        this.storage = storage;
    }

    @GetMapping("/api/media/{name:.+}")
    public ResponseEntity<Resource> get(@PathVariable String name) {
        Path file = storage.find(name);
        if (file == null) {
            return ResponseEntity.notFound().build();
        }
        MediaType type = name.endsWith(".png") ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
        return ResponseEntity.ok()
                .contentType(type)
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header("X-Content-Type-Options", "nosniff")
                .body(new FileSystemResource(file));
    }
}
