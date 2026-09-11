package com.company.taskmanagementplatform.attachments;

/**
 * Makes an uploaded filename safe to store and to hand back.
 *
 * <p>The name is never part of the storage key, so nothing here is what stops a traversal reaching
 * the filesystem. The key is {@code workspace/<id>/task/<id>/<uuid>} and contains nothing a user
 * supplied. This class exists for the other end of the file's life: the name is echoed in a
 * {@code Content-Disposition} header and rendered in a list, and both of those have their own ways
 * of being abused.
 *
 * <p>Directory parts are dropped rather than escaped, because a browser sends the whole path on some
 * platforms and none of it is information anybody wants to see.
 */
final class Filenames {

    static final int MAX_LENGTH = 255;

    private static final String FALLBACK = "file";

    private Filenames() {}

    static String sanitise(String raw) {
        if (raw == null) {
            return FALLBACK;
        }

        String name = raw;

        // Whatever the client sent as a path, keep only the last part.
        int lastSlash = Math.max(name.lastIndexOf('/'), name.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            name = name.substring(lastSlash + 1);
        }

        // Control characters would let a name inject a line into a header or a log.
        StringBuilder cleaned = new StringBuilder(name.length());
        name.chars().filter(ch -> !Character.isISOControl(ch)).forEach(ch -> cleaned.append((char) ch));

        name = cleaned.toString().replace('"', '\'').trim();

        if (name.isEmpty() || ".".equals(name) || "..".equals(name)) {
            return FALLBACK;
        }
        if (name.length() > MAX_LENGTH) {
            name = name.substring(0, MAX_LENGTH).trim();
        }
        return name.isEmpty() ? FALLBACK : name;
    }
}
