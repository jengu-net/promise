package cloud.jengu.promise;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.RoundEnvironment;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedOptions;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import javax.tools.StandardLocation;
import java.io.IOException;
import java.io.Writer;
import java.util.Set;
import java.util.TreeSet;

/**
 * Writes every {@code @Catalogue}-annotated enum's binary name into
 * {@link Registry#INDEX} at the compiling component's own compile time
 * (REQ-PROMISE-REGISTERED-AT-COMPILE-TIME).
 *
 * <p>Discovery is paid where the set of annotated types is already known —
 * the compiler hands them over — so no classpath is ever swept, and an index
 * regenerated on every compile cannot drift or be lost. The registry then
 * reads each catalogue whole; registration is never a side effect of class
 * loading, because the constants most likely to go untouched (gaps, planned
 * promises with no tests yet) are exactly the ones coverage exists to count.
 *
 * <p><b>Which index a citation goes to is the compilation's to say</b>, with
 * the option {@code -Apromise.index=tested} on a test compilation. A
 * processor cannot tell a test source set from a main one, and guessing from
 * paths would make a citation's meaning depend on a directory layout. Without
 * the option the citations are production code's, written to
 * {@link Proofs#INDEX}; with it, a test's, written to
 * {@link Proofs#TESTED_INDEX} — so the two never overwrite each other in a jar
 * that carries both.
 */
@SupportedAnnotationTypes("*")
@SupportedOptions(CatalogueProcessor.INDEX_OPTION)
public final class CatalogueProcessor extends AbstractProcessor {

    /** The option naming which claim this compilation's citations make. */
    public static final String INDEX_OPTION = "promise.index";

    private final Set<String> catalogues = new TreeSet<>();
    private final Set<String> proofs = new TreeSet<>();

    @Override
    public SourceVersion getSupportedSourceVersion() {
        return SourceVersion.latestSupported();
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment round) {
        // The second concern (REQ-PROMISE-PROOFS-INDEXED-AT-COMPILE-TIME):
        // any annotation whose TYPE carries @Cites is a product's own proving
        // annotation, and every site using it is a citation. Read from the
        // mirror, so the index regenerates on every compile — a renamed test
        // method cannot leave a stale citation behind.
        for (TypeElement annotation : annotations) {
            if (annotation.getAnnotation(Cites.class) == null) {
                continue;
            }
            for (Element site : round.getElementsAnnotatedWith(annotation)) {
                indexCitation(annotation, site);
            }
        }
        for (Element element : round.getElementsAnnotatedWith(Catalogue.class)) {
            if (element.getKind() != ElementKind.ENUM) {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                        "@Catalogue marks enums only — the constant's name is the code, and "
                                + "only an enum has constants to name", element);
                continue;
            }
            catalogues.add(processingEnv.getElementUtils()
                    .getBinaryName((TypeElement) element).toString());
        }
        if (round.processingOver()) {
            write(Registry.INDEX, catalogues);
            Proofs.Kind kind = citationKind();
            if (kind != null) {
                write(kind.index(), proofs);
            }
        }
        // Never claimed: other processors may care about the same types.
        return false;
    }

    /** What this compilation's citations claim, or null once an unknown option was refused. */
    private Proofs.Kind citationKind() {
        String asked = processingEnv.getOptions().get(INDEX_OPTION);
        if (asked == null || asked.equals("enforced")) {
            return Proofs.Kind.ENFORCED;
        }
        if (asked.equals("tested")) {
            return Proofs.Kind.TESTED;
        }
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                INDEX_OPTION + " is 'tested' for a compilation of tests, or 'enforced' (the "
                        + "default) for production code, and this one asked for '" + asked
                        + "'");
        return null;
    }

    /**
     * Where a citation stands: a type by its binary name, and a member as its
     * type's binary name, '#', and its own name — a constructor as
     * {@code <init>}, since that is the name the class file gives it.
     */
    private String siteOf(Element site) {
        if (site instanceof TypeElement type) {
            return processingEnv.getElementUtils().getBinaryName(type).toString();
        }
        Element enclosing = site.getEnclosingElement();
        while (!(enclosing instanceof TypeElement)) {
            enclosing = enclosing.getEnclosingElement();
        }
        return processingEnv.getElementUtils().getBinaryName((TypeElement) enclosing)
                + "#" + site.getSimpleName();
    }

    private void indexCitation(TypeElement annotationType, Element site) {
        String where = siteOf(site);
        for (javax.lang.model.element.AnnotationMirror mirror : site.getAnnotationMirrors()) {
            if (!mirror.getAnnotationType().asElement().equals(annotationType)) {
                continue;
            }
            for (var entry : mirror.getElementValues().entrySet()) {
                if (!"value".contentEquals(entry.getKey().getSimpleName())) {
                    continue;
                }
                StringBuilder constants = new StringBuilder();
                String enumName = null;
                Object raw = entry.getValue().getValue();
                for (Object value : raw instanceof java.util.List<?> list
                        ? list : java.util.List.of(raw)) {
                    Object inner = value instanceof javax.lang.model.element.AnnotationValue av
                            ? av.getValue() : value;
                    if (inner instanceof javax.lang.model.element.VariableElement constant) {
                        enumName = processingEnv.getElementUtils()
                                .getBinaryName((TypeElement) constant.getEnclosingElement())
                                .toString();
                        if (constants.length() > 0) {
                            constants.append(',');
                        }
                        constants.append(constant.getSimpleName());
                    }
                }
                if (enumName != null) {
                    proofs.add(where + "=" + enumName + ":" + constants);
                }
            }
        }
    }

    private void write(String resource, Set<String> lines) {
        if (lines.isEmpty()) {
            return;
        }
        try (Writer out = processingEnv.getFiler()
                .createResource(StandardLocation.CLASS_OUTPUT, "", resource)
                .openWriter()) {
            for (String line : lines) {
                out.write(line);
                out.write('\n');
            }
        } catch (IOException e) {
            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                    "could not write " + resource + ": " + e.getMessage());
        }
    }
}
