package cloud.jengu.promise;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * One testable business promise — proven, not claimed.
 *
 * <p>Declared exactly once, as a constant of a {@link Catalogue}-annotated
 * enum; the constant's name is the code and the constructor carries the one
 * promise text. Tests cite the constant (each product's own {@code @Proving}
 * annotation is typed to its own enum, so a wrong citation is a compile
 * error), and production code cites the same constant where it enforces the
 * promise — a refusal names what it refuses for.
 *
 * <p>A promise nobody has stated yet is a {@link #gap(String)}: it compiles,
 * registers through the classification that declares it, carries a stable
 * synthetic code, and counts against coverage until promoted to a named
 * constant (REQ-PROMISE-GAP-IS-FIRST-CLASS). Unknown ground is named, never
 * silent.
 */
public interface Promise extends Coded {

    /** The promise, as one business-readable sentence. */
    String text();

    /**
     * The promises this one is based on: the ground that keeps it, worded for
     * whoever builds that ground, where this one is worded for its own readers.
     *
     * <p>Constants of any catalogue the classpath carries, this product's or a
     * composed product's. Links run down only: a promise names what it rests
     * on, never what rests on it, and the inverse is derived. A promise based
     * on others and cited by no test of its own is kept by its basis, and reads
     * so ({@link PromiseStatus#BASED}).
     *
     * <p>Name a constant of the same catalogue declared after this one from an
     * overriding method, not from the constructor: in the constructor it is not
     * built yet, and arrives as null, which the model refuses.
     */
    default java.util.List<Promise> basis() {
        return java.util.List.of();
    }

    /** True for a {@link #gap(String)} — ground nobody has stated yet. */
    default boolean gap() {
        return false;
    }

    /**
     * Why this promise is believed, when the reason is not a citation. Null
     * when there is none.
     *
     * <p>Declared on the CONSTANT rather than at a proof site, because that is
     * where the reason lives: there is no test to hang it on, and status stays
     * derived (REQ-PROMISE-STATUS-IS-DERIVED).
     *
     * <p>Two kinds of ground reach here, and the note has to say which.
     * <b>Review</b> — a property somebody checked and no executable test
     * covers. And <b>proven where this classpath cannot see it</b> — a test
     * exists, passes and cites the constant, from a jar a reader may not hold:
     * the toolset's promise about its Gradle plugin is the standing case,
     * proven by the plugin's tests and carried in the plugin's jar. Calling it
     * PLANNED there would say nobody built it; ASSURED with a note that names
     * the test says the true thing, and a classpath that does hold the
     * citation reads it PROVEN.
     */
    default String assurance() {
        return null;
    }

    /**
     * A promise nobody has stated yet, named by the text of what is missing.
     *
     * <p>Declared inside a classification's promise list — where the hole was
     * noticed — not in the promise enum. The code is synthetic and stable
     * ({@code GAP-} + eight hex digits of the text's hash), so reports diff
     * cleanly and a promotion to a named constant shows as exactly that; the
     * composed model qualifies it with the declaring catalogue's namespace.
     */
    static Promise gap(String text) {
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("a gap needs the text of what is missing — "
                    + "an unnamed unknown is the silence this type exists to end");
        }
        return new Gap(text);
    }
}

/**
 * The unstated promise. Package-private: callers meet it only as a
 * {@link Promise} that answers {@code gap() == true}.
 */
record Gap(String text) implements Promise {

    @Override
    public String code() {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(text.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder("GAP-");
            for (int i = 0; i < 4; i++) {
                hex.append(Character.forDigit((digest[i] >> 4) & 0xf, 16))
                        .append(Character.forDigit(digest[i] & 0xf, 16));
            }
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    @Override
    public boolean gap() {
        return true;
    }
}
