package cloud.jengu.promise;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The citation index: which sites cite which promises, and as what.
 *
 * <p>Written at each compilation's own compile time by {@link CatalogueProcessor}
 * (REQ-PROMISE-PROOFS-INDEXED-AT-COMPILE-TIME) — one line per site,
 * {@code binary.Class#member=enum.Class:CONSTANT,CONSTANT} — and read here by
 * resolving each named constant against the loaded enum, so a citation that
 * outlives its declaration fails the load rather than reporting quietly.
 *
 * <p><b>Two indexes, two claims.</b> A test citing a promise claims to prove
 * it; production code citing one says it enforces it. They are written under
 * different names — {@link #TESTED_INDEX} by a test compilation, {@link #INDEX}
 * by a main one — so both can travel in one jar without either overwriting the
 * other, and they are read back apart: only a tested citation makes a promise
 * proven.
 */
public final class Proofs {

    /** What production code cites: where it enforces a promise. */
    public static final String INDEX = "META-INF/promise/proofs";

    /** What tests cite: the promises they claim to prove. */
    public static final String TESTED_INDEX = "META-INF/promise/proofs-tested";

    /** Which claim a citation makes. */
    public enum Kind {

        /** A test that claims to prove the promise. */
        TESTED(TESTED_INDEX),

        /** Production code that enforces the promise. */
        ENFORCED(INDEX);

        private final String index;

        Kind(String index) {
            this.index = index;
        }

        /** The resource this kind of citation is written under and read from. */
        public String index() {
            return index;
        }
    }

    /** One site citing a promise, and what its citing claims. */
    public record Citation(String site, Kind kind) {
    }

    private final Map<Kind, Map<String, Set<String>>> sitesByCode = new LinkedHashMap<>();

    private Proofs() {
        for (Kind kind : Kind.values()) {
            sitesByCode.put(kind, new LinkedHashMap<>());
        }
    }

    /**
     * Every citation registered on {@code loader}'s classpath, both kinds, from
     * every jar and directory that carries an index.
     *
     * @throws IllegalStateException for an index that names a constant the
     *         classpath does not carry, or cannot be read at all
     */
    public static Proofs load(ClassLoader loader) {
        Proofs proofs = new Proofs();
        for (Kind kind : Kind.values()) {
            for (URL index : indexes(loader, kind)) {
                proofs.read(loader, kind, index);
            }
        }
        return proofs;
    }

    /** Every index of that kind the loader can see, in the loader's order. */
    static List<URL> indexes(ClassLoader loader, Kind kind) {
        List<URL> found = new ArrayList<>();
        try {
            Enumeration<URL> indexes = loader.getResources(kind.index());
            while (indexes.hasMoreElements()) {
                found.add(indexes.nextElement());
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not list " + kind.index(), e);
        }
        return found;
    }

    /** One index read into these proofs; throws as {@link #load} does. */
    void read(ClassLoader loader, Kind kind, URL index) {
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(index.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isBlank()) {
                    take(loader, kind, line.strip(), index);
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + index, e);
        }
    }

    /** Proofs that cite nothing, to read indexes into one by one. */
    static Proofs none() {
        return new Proofs();
    }

    /** Takes in every citation {@code other} holds — an index read whole on its own first. */
    void absorb(Proofs other) {
        other.sitesByCode.forEach((kind, byCode) -> byCode.forEach((code, sites) ->
                sites.forEach(site -> add(kind, code, site))));
    }

    /** The explicit form, for tests: each {@code site} is a test citing {@code promises}. */
    public static Proofs of(Map<String, List<Promise>> citations) {
        Proofs proofs = new Proofs();
        citations.forEach((site, promises) -> promises.forEach(promise ->
                proofs.add(Kind.TESTED, promise.code(), site)));
        return proofs;
    }

    /** The tests citing {@code promise}: what claims to prove it. Empty is the answer "nobody". */
    public Set<String> citing(Promise promise) {
        return sites(Kind.TESTED, promise);
    }

    /** The production code citing {@code promise}: where it is enforced. */
    public Set<String> enforcing(Promise promise) {
        return sites(Kind.ENFORCED, promise);
    }

    /** Every citation of {@code promise}, tested first, each saying which it is. */
    public List<Citation> citations(Promise promise) {
        List<Citation> all = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            sites(kind, promise).stream().sorted()
                    .forEach(site -> all.add(new Citation(site, kind)));
        }
        return List.copyOf(all);
    }

    /** Whether a test cites {@code promise} — the only citation that proves one. */
    public boolean cited(Promise promise) {
        return sitesByCode.get(Kind.TESTED).containsKey(promise.code());
    }

    /** Sorted, so a report drawn from them reads the same on every machine and every run. */
    private Set<String> sites(Kind kind, Promise promise) {
        return java.util.Collections.unmodifiableSortedSet(new java.util.TreeSet<>(
                sitesByCode.get(kind).getOrDefault(promise.code(), Set.of())));
    }

    private void add(Kind kind, String code, String site) {
        sitesByCode.get(kind).computeIfAbsent(code, c -> new LinkedHashSet<>()).add(site);
    }

    private void take(ClassLoader loader, Kind kind, String line, URL index) {
        int eq = line.indexOf('=');
        int colon = line.indexOf(':', eq);
        if (eq < 0 || colon < 0) {
            throw new IllegalStateException(index + " carries an unreadable line: " + line);
        }
        String site = line.substring(0, eq);
        String enumName = line.substring(eq + 1, colon);
        Class<?> catalogue;
        try {
            catalogue = Class.forName(enumName, false, loader);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException(index + " cites '" + enumName
                    + "', which this classpath does not carry — a stale index is a build "
                    + "defect, not a skippable line", e);
        }
        Object[] values = catalogue.getEnumConstants();
        for (String constant : line.substring(colon + 1).split(",")) {
            Promise found = null;
            for (Object value : values == null ? new Object[0] : values) {
                if (value instanceof Promise promise
                        && ((Enum<?>) value).name().equals(constant.strip())) {
                    found = promise;
                }
            }
            if (found == null) {
                throw new IllegalStateException(index + " cites " + enumName + "."
                        + constant + ", which the enum does not declare");
            }
            add(kind, found.code(), site);
        }
    }
}
