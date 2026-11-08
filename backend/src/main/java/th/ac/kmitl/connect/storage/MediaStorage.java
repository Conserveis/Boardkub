package th.ac.kmitl.connect.storage;

/**
 * Where uploaded images are kept.
 * <ul>
 *   <li>{@code gcs}   — Google Cloud Storage (staging / production on GKE)</li>
 *   <li>{@code local} — a folder on disk (docker compose / minikube only)</li>
 * </ul>
 * Selected with {@code app.storage.type} (env {@code STORAGE_TYPE}).
 */
public interface MediaStorage {

    /** Stores the bytes under {@code objectName}, e.g. {@code 3f2c...e1.jpg}. */
    void put(String objectName, byte[] data, String contentType);

    /** Deletes the object; an object that is already gone is not an error. */
    void delete(String objectName);

    /** URL the browser uses in {@code <img src>}. */
    String publicUrl(String objectName);
}
