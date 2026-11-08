package th.ac.kmitl.connect.storage;

import java.util.UUID;
import java.util.regex.Pattern;

/** Object names are random UUIDs, so users can neither guess nor choose them. */
public final class ObjectNames {

    private static final Pattern VALID =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\\.(jpg|png)$");

    private ObjectNames() {}

    public static String random(String extension) {
        return UUID.randomUUID() + "." + extension;
    }

    public static boolean isValid(String name) {
        return name != null && VALID.matcher(name).matches();
    }
}
