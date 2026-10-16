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

/**
 * The composition seam: catalogues in, one graph out.
 *
 * <p>{@link #load(ClassLoader)} walks every {@link #INDEX} resource the
 * loader can see — one product alone while it is developed, every product's
 * jar once they are composed — and {@link #of(Class[])} stays for tools and tests
 * that want to be explicit. Either way a catalogue is read WHOLE via
 * {@code getEnumConstants()}: proven, planned and gap constants alike, never
 * a side effect of what happened to be class-loaded
 * (REQ-PROMISE-CATALOGUE-READ-WHOLE).
 */
public final class Registry {

    /** Where the processor writes and the registry reads: one name per line. */
    public static final String INDEX = "META-INF/promise/catalogues";

    private final List<Class<?>> catalogues;

    private Registry(List<Class<?>> catalogues) {
        this.catalogues = List.copyOf(catalogues);
    }

    /** Every catalogue registered on {@code loader}'s classpath. */
    public static Registry load(ClassLoader loader) {
        LinkedHashSet<Class<?>> found = new LinkedHashSet<>();
        for (URL index : indexes(loader)) {
            found.addAll(read(loader, index));
        }
        return new Registry(new ArrayList<>(found));
    }

    /** Every catalogue index the loader can see, in the loader's order. */
    static List<URL> indexes(ClassLoader loader) {
        List<URL> found = new ArrayList<>();
        try {
            Enumeration<URL> indexes = loader.getResources(INDEX);
            while (indexes.hasMoreElements()) {
                found.add(indexes.nextElement());
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not list " + INDEX, e);
        }
        return found;
    }

    /** The catalogues one index names, each resolved; throws as {@link #load} does. */
    static List<Class<?>> read(ClassLoader loader, URL index) {
        List<Class<?>> found = new ArrayList<>();
        try (BufferedReader in = new BufferedReader(
                new InputStreamReader(index.openStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = in.readLine()) != null) {
                if (!line.isBlank()) {
                    found.add(catalogueClass(loader, line.strip(), index));
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException("could not read " + index, e);
        }
        return found;
    }

    /** The catalogues given, already resolved. */
    static Registry over(java.util.Collection<Class<?>> catalogues) {
        return new Registry(new ArrayList<>(new LinkedHashSet<>(catalogues)));
    }

    /** The explicit form, for tools and tests. */
    public static Registry of(Class<?>... catalogues) {
        List<Class<?>> validated = new ArrayList<>();
        for (Class<?> catalogue : catalogues) {
            validated.add(validated(catalogue,
                    "registered explicitly as " + catalogue.getName()));
        }
        return new Registry(validated);
    }

    public List<Class<?>> catalogues() {
        return catalogues;
    }

    /** The composed graph over every registered catalogue, citations unknown. */
    public Model model() {
        return new Model(catalogues, Proofs.of(java.util.Map.of()));
    }

    /** The composed graph joined against the citation index. */
    public Model model(Proofs proofs) {
        return new Model(catalogues, proofs);
    }

    /**
     * A catalogue's constants, built. A constant naming ground the classpath
     * does not carry in its constructor fails as its class initialises, and is
     * refused here by name rather than escaping as a linkage error.
     */
    static Object[] constantsOf(Class<?> catalogue) {
        try {
            return catalogue.getEnumConstants();
        } catch (LinkageError stale) {
            throw new IllegalStateException(catalogue.getName() + " cannot be built: a promise "
                    + "it declares is based on one this classpath does not carry ("
                    + stale.getMessage() + ") — a stale basis is a build defect, not a "
                    + "skippable line", stale);
        }
    }

    private static Class<?> catalogueClass(ClassLoader loader, String name, URL index) {
        try {
            return validated(Class.forName(name, false, loader), "named by " + index);
        } catch (ClassNotFoundException | LinkageError e) {
            throw new IllegalStateException(index + " names '" + name
                    + "', which this classpath does not carry — a stale index is a build "
                    + "defect, not a skippable line", e);
        }
    }

    private static Class<?> validated(Class<?> catalogue, String origin) {
        if (!catalogue.isEnum()) {
            throw new IllegalArgumentException(catalogue.getName() + " (" + origin
                    + ") is not an enum — the constant's name is the code, and only an enum "
                    + "has constants to name");
        }
        if (catalogue.getAnnotation(Catalogue.class) == null) {
            throw new IllegalArgumentException(catalogue.getName() + " (" + origin
                    + ") carries no @Catalogue — the namespace half of every code is missing");
        }
        if (!Coded.class.isAssignableFrom(catalogue)) {
            throw new IllegalArgumentException(catalogue.getName() + " (" + origin
                    + ") implements none of the model interfaces — nothing here knows how "
                    + "to read it");
        }
        return catalogue;
    }

    /**
     * The composed graph: every promise, classification and area of every
     * registered catalogue, areas merged by code, the classification →
     * promise links inverted once so a promise's classifications are derived,
     * never declared (REQ-PROMISE-DOWN-LINKS-ONLY).
     */
    public static final class Model {

        private final List<Promise> promises = new ArrayList<>();
        private final List<Classified> classifications = new ArrayList<>();
        private final Map<String, MergedArea> areas = new LinkedHashMap<>();
        private final Map<Promise, List<Classified>> declaring = new LinkedHashMap<>();
        private final Map<Promise, String> namespaces = new LinkedHashMap<>();
        private final Map<Promise, List<Promise>> basedOn = new LinkedHashMap<>();
        private final Proofs proofs;

        private Model(List<Class<?>> catalogues, Proofs proofs) {
            this.proofs = proofs;
            for (Class<?> catalogue : catalogues) {
                String namespace = catalogue.getAnnotation(Catalogue.class).namespace();
                for (Object constant : constantsOf(catalogue)) {
                    if (constant instanceof Promise promise) {
                        promises.add(promise);
                    }
                    if (constant instanceof Classified classified) {
                        classifications.add(classified);
                        for (Promise declared : classified.promises()) {
                            declaring.computeIfAbsent(declared, p -> new ArrayList<>())
                                    .add(classified);
                            if (declared.gap()) {
                                // A gap registers through the classification
                                // that noticed the hole; the namespace it is
                                // reported under is that catalogue's.
                                promises.add(declared);
                                namespaces.put(declared, namespace);
                            }
                        }
                    }
                    if (constant instanceof Area area) {
                        areas.merge(area.code(), new MergedArea(area.code(), area.title(),
                                new ArrayList<>(area.covers())), MergedArea::merged);
                    }
                }
            }
            // The basis, inverted once — and refused whole when a promise names
            // ground this classpath cannot carry, or rests on itself.
            for (Promise promise : promises) {
                for (Promise ground : basisOf(promise)) {
                    basedOn.computeIfAbsent(ground, p -> new ArrayList<>()).add(promise);
                }
            }
            List<Promise> cycle = cycle(promises);
            if (cycle != null) {
                throw new IllegalStateException(cycleMessage(cycle));
            }
        }

        /**
         * What a promise is based on, as it declares it.
         *
         * @throws IllegalStateException when it names a constant the classpath
         *         does not carry, or one not built yet — a stale basis is a
         *         build defect, as a stale citation is
         */
        static List<Promise> basisOf(Promise promise) {
            List<Promise> basis;
            try {
                basis = promise.basis();
            } catch (LinkageError stale) {
                throw new IllegalStateException(promise.code() + " is based on a promise this "
                        + "classpath does not carry (" + stale.getMessage() + ") — a stale "
                        + "basis is a build defect, not a skippable line", stale);
            }
            for (Promise ground : basis) {
                if (ground == null) {
                    throw new IllegalStateException(promise.code() + " is based on a promise "
                            + "that is not built yet — a constant of its own catalogue declared "
                            + "after it, named from its constructor; return its basis from an "
                            + "overriding method instead");
                }
            }
            return List.copyOf(basis);
        }

        /** A cycle among the bases reachable from these promises, in order, or null. */
        static List<Promise> cycle(java.util.Collection<Promise> promises) {
            Map<Promise, Integer> state = new java.util.HashMap<>();
            java.util.Deque<Promise> path = new java.util.ArrayDeque<>();
            for (Promise promise : promises) {
                List<Promise> found = cycleFrom(promise, state, path);
                if (found != null) {
                    return found;
                }
            }
            return null;
        }

        private static List<Promise> cycleFrom(Promise promise, Map<Promise, Integer> state,
                java.util.Deque<Promise> path) {
            Integer seen = state.get(promise);
            if (seen != null && seen == 2) {
                return null;
            }
            if (seen != null) {
                List<Promise> cycle = new ArrayList<>();
                boolean in = false;
                for (java.util.Iterator<Promise> it = path.descendingIterator(); it.hasNext(); ) {
                    Promise step = it.next();
                    in = in || step == promise;
                    if (in) {
                        cycle.add(step);
                    }
                }
                cycle.add(promise);
                return cycle;
            }
            state.put(promise, 1);
            path.push(promise);
            for (Promise ground : basisOf(promise)) {
                List<Promise> found = cycleFrom(ground, state, path);
                if (found != null) {
                    return found;
                }
            }
            path.pop();
            state.put(promise, 2);
            return null;
        }

        static String cycleMessage(List<Promise> cycle) {
            StringBuilder names = new StringBuilder();
            for (Promise step : cycle) {
                names.append(names.length() == 0 ? "" : " → ").append(step.code());
            }
            return "these promises are each other's basis, so none of them rests on anything: "
                    + names;
        }

        /** What {@code promise} is based on, as it declares it. Empty for one based on nothing. */
        public List<Promise> basis(Promise promise) {
            return basisOf(promise);
        }

        /** The derived inverse: the promises of the graph based on {@code promise}. */
        public List<Promise> basedOn(Promise promise) {
            return List.copyOf(basedOn.getOrDefault(promise, List.of()));
        }

        public List<Promise> promises() {
            return List.copyOf(promises);
        }

        public List<Classified> classifications() {
            return List.copyOf(classifications);
        }

        /** Same-code areas merged; conflicting titles refused, both named. */
        public List<Area> areas() {
            return List.copyOf(areas.values());
        }

        /** The derived inverse of the down-links. Empty for the undeclared. */
        public List<Classified> declaring(Promise promise) {
            return List.copyOf(declaring.getOrDefault(promise, List.of()));
        }

        /**
         * Derived, never asserted (REQ-PROMISE-STATUS-IS-DERIVED): cited is
         * proven — the report is only ever generated from a green build, the
         * same trust the close-after-CI gate encodes — an assurance note on the
         * constant is review-based, a promise cited by nothing of its own whose
         * every basis is kept is kept by that basis, named-uncited is planned,
         * and a gap is a gap.
         */
        public PromiseStatus statusOf(Promise promise) {
            if (promise.gap()) {
                return PromiseStatus.GAP;
            }
            if (proofs.cited(promise)) {
                return PromiseStatus.PROVEN;
            }
            if (promise.assurance() != null) {
                return PromiseStatus.ASSURED;
            }
            List<Promise> basis = basisOf(promise);
            if (!basis.isEmpty() && basis.stream().allMatch(ground -> statusOf(ground).kept())) {
                return PromiseStatus.BASED;
            }
            return PromiseStatus.PLANNED;
        }

        /** The tests proving {@code promise}; empty is the answer "nobody". */
        public java.util.Set<String> citing(Promise promise) {
            return proofs.citing(promise);
        }

        /**
         * The production code enforcing {@code promise}. Never a proof: code
         * that refuses what a promise forbids says where it is kept, and only
         * a test says that it holds.
         */
        public java.util.Set<String> enforcing(Promise promise) {
            return proofs.enforcing(promise);
        }

        /** Every citation of {@code promise}, each saying whether it proves or enforces. */
        public List<Proofs.Citation> citations(Promise promise) {
            return proofs.citations(promise);
        }

        /**
         * The fold (REQ-PROMISE-COVERAGE-IS-A-FOLD): the statuses of the
         * promises a classification declares, gaps included.
         */
        public Map<PromiseStatus, Long> coverage(Classified classified) {
            Map<PromiseStatus, Long> counts = new java.util.EnumMap<>(PromiseStatus.class);
            for (Promise promise : classified.promises()) {
                counts.merge(statusOf(promise), 1L, Long::sum);
            }
            return counts;
        }

        /** An area's coverage: the fold of its classifications' folds. */
        public Map<PromiseStatus, Long> coverage(Area area) {
            Map<PromiseStatus, Long> counts = new java.util.EnumMap<>(PromiseStatus.class);
            for (Classified classified : area.covers()) {
                coverage(classified).forEach((status, n) -> counts.merge(status, n, Long::sum));
            }
            return counts;
        }

        /**
         * The promise's code as the composed report says it: a named
         * constant answers for itself; a gap is qualified by the namespace
         * of the catalogue whose classification declared it.
         */
        public String codeOf(Promise promise) {
            String namespace = namespaces.get(promise);
            return namespace == null ? promise.code() : namespace + "-" + promise.code();
        }

        private record MergedArea(String code, String title, List<Classified> covers)
                implements Area {

            MergedArea merged(MergedArea other) {
                if (!title.equals(other.title)) {
                    throw new IllegalStateException("area '" + code + "' is declared with two "
                            + "titles — '" + title + "' and '" + other.title + "' — and picking "
                            + "one would silently overrule the other catalogue");
                }
                List<Classified> union = new ArrayList<>(covers);
                union.addAll(other.covers);
                return new MergedArea(code, title, union);
            }
        }
    }
}
