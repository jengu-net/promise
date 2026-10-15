package cloud.jengu.promise;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The toolset holds itself to what it asks of a product: its promises are
 * declared in a catalogue of its own, every one is proven by a test citing it,
 * and their page is generated from that, never written by hand.
 */
class TheToolsetKeepsItsPromisesTest {

    private static final String GENERATED = "<!-- Generated from the toolset's own catalogue by "
            + "TheToolsetKeepsItsPromisesTest. Never edit it: change a promise or a citation "
            + "and run ./gradlew :promise:test -Ppromise.rerecord -->\n\n";

    private static Registry.Model model() {
        ClassLoader loader = TheToolsetKeepsItsPromisesTest.class.getClassLoader();
        return Registry.of(ToolsetFeatures.class, ToolsetPromises.class)
                .model(Proofs.load(loader));
    }

    @Test
    @DisplayName("the toolset's jar registers its own catalogues, by an index written by hand "
            + "because no processor runs over the compilation that builds the processor")
    void theToolsetsCataloguesAreRegistered() throws IOException {
        List<String> named = new ArrayList<>();
        for (URL index : Collections.list(getClass().getClassLoader()
                .getResources(Registry.INDEX))) {
            try (InputStream in = index.openStream()) {
                named.addAll(new String(in.readAllBytes(), StandardCharsets.UTF_8).lines()
                        .map(String::strip).filter(line -> !line.isEmpty()).toList());
            }
        }
        assertTrue(named.containsAll(Set.of(ToolsetPromises.class.getName(),
                ToolsetFeatures.class.getName())), "the toolset's catalogues are unregistered: "
                + named);
    }

    @Test
    @DisplayName("every promise the toolset makes is proven by a test citing it, and the ones "
            + "its plugin's own tests prove say so where they cannot be read")
    void everyPromiseIsProven() {
        Registry.Model model = model();
        List<String> unproven = new ArrayList<>();
        for (ToolsetPromises promise : ToolsetPromises.values()) {
            // Proven in the plugin's module, whose citations this classpath does
            // not hold: assured, by a note naming the test, and only then.
            PromiseStatus expected = promise.assurance() != null
                    ? PromiseStatus.ASSURED : PromiseStatus.PROVEN;
            if (model.statusOf(promise) != expected) {
                unproven.add(promise.code() + " reads " + model.statusOf(promise));
            }
        }
        assertEquals(List.of(), unproven);
        for (ToolsetPromises promise : ToolsetPromises.values()) {
            assertTrue(!model.declaring(promise).isEmpty(),
                    promise.code() + " belongs to no feature, and the page would not show it");
        }
    }

    @Test
    @DisplayName("the page of the toolset's promises is generated from its catalogue and its "
            + "citations, and a page that differs is refused")
    void thePageIsCurrent() throws IOException {
        Path page = Path.of(System.getProperty("promise.page"));
        String rendered = GENERATED + Report.render(model());
        if (Boolean.getBoolean("promise.rerecord")) {
            Files.writeString(page, rendered);
        }
        assertEquals(rendered, Files.exists(page) ? Files.readString(page) : "",
                "PROMISES.md differs from the catalogue — run ./gradlew :promise:test "
                        + "-Ppromise.rerecord, never edit it by hand");
    }
}
