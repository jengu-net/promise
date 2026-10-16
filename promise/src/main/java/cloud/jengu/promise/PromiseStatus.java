package cloud.jengu.promise;

/**
 * What stands behind a promise. The MODEL of status lives here; the
 * DERIVATION — reading proof sites and deciding which status a promise has
 * earned — is the scanner's, and status is always derived, never asserted.
 */
public enum PromiseStatus {

    /** Cited by passing tests: the promise is proven automatically. */
    PROVEN,

    /** Declared, cited by nothing yet: intent, parked where it is visible. */
    PLANNED,

    /** Assured by recorded review rather than by an executable test. */
    ASSURED,

    /**
     * Cited by no test of its own, and kept by the promises it is based on —
     * every one of them proven, assured or based in turn. Kept, and told apart
     * from a promise its own tests prove.
     */
    BASED,

    /** Ground nobody has stated: a {@link Promise#gap(String)}. */
    GAP;

    /** Whether a promise with this status is kept: proven, assured, or based on kept ground. */
    public boolean kept() {
        return this == PROVEN || this == ASSURED || this == BASED;
    }
}
