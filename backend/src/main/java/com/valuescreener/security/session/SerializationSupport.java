package com.valuescreener.security.session;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InvalidClassException;
import java.io.ObjectInputFilter;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.UncheckedIOException;

/**
 * Java serialization for values that only this backend writes and reads (session attributes, throttle
 * state). Reading is restricted by an allow-list, so a manipulated item cannot instantiate arbitrary classes.
 */
public final class SerializationSupport {

    /**
     * Size limits first (a real security context is about 1.3 KB), then the allow-list: JDK types, Spring Security
     * types (security context, authentication, authorities) and our own.
     */
    private static final int MAX_BYTES = 16_384;
    private static final ObjectInputFilter FILTER = ObjectInputFilter.Config.createFilter(
            "maxdepth=20;maxrefs=1000;maxbytes=" + MAX_BYTES + ";maxarray=4096;"
                    + "java.**;org.springframework.security.**;com.valuescreener.**;!*");

    private SerializationSupport() {
    }

    public static byte[] serialize(Object value) {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream out = new ObjectOutputStream(bytes)) {
            out.writeObject(value);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return bytes.toByteArray();
    }

    public static Object deserialize(byte[] data) throws IOException, ClassNotFoundException {
        // The filter's maxbytes is only checked when a class or reference is read, not for a long string at the end.
        if (data.length > MAX_BYTES) {
            throw new InvalidClassException("serialized data larger than " + MAX_BYTES + " bytes");
        }
        try (ObjectInputStream in = new ObjectInputStream(new ByteArrayInputStream(data))) {
            in.setObjectInputFilter(FILTER);
            return in.readObject();
        }
    }
}
