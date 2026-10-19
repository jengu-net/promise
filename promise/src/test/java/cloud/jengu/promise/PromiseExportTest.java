package cloud.jengu.promise;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.tools.JavaFileObject;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static cloud.jengu.promise.TwoIndexesTest.compile;
import static cloud.jengu.promise.TwoIndexesTest.loader;
import static cloud.jengu.promise.TwoIndexesTest.source;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The export is the same reading as the ledger, written for a reader that
 * cannot load the catalogues: areas, their groups, and every promise they
 * reach with what stands behind it.
 */
class PromiseExportTest {

    private static final List<JavaFileObject> SHOP = List.of(
            source("shop.ShopPromises", """
                    package shop;
                    import cloud.jengu.promise.Catalogue;
                    import cloud.jengu.promise.Promise;
                    import java.util.List;
                    @Catalogue(namespace = "REQ-SHOP")
                    public enum ShopPromises implements Promise {
                        TOTAL_IS_THE_SUM("An order's total is the sum of its \\"lines\\""),
                        LINES_ARE_PRICED("Every line carries its price"),
                        REFUND_NEVER_EXCEEDS("A refund never exceeds what was paid") {
                            @Override public List<Promise> basis() { return List.of(LINES_ARE_PRICED); }
                        };
                        private final String text;
                        ShopPromises(String text) { this.text = text; }
                        @Override public String text() { return text; }
                    }
                    """),
            source("shop.ShopStories", """
                    package shop;
                    import cloud.jengu.promise.Catalogue;
                    import cloud.jengu.promise.Promise;
                    import cloud.jengu.promise.Story;
                    import java.util.List;
                    @Catalogue(namespace = "US-SHOP")
                    public enum ShopStories implements Story {
                        CHECKOUT;
                        @Override public String title() { return "Somebody buys what is in their basket"; }
                        @Override public String document() { return "docs/stories/checkout.md"; }
                        @Override public List<Promise> promises() {
                            return List.of(ShopPromises.TOTAL_IS_THE_SUM, ShopPromises.REFUND_NEVER_EXCEEDS,
                                    Promise.gap("A basket survives a closed browser"));
                        }
                    }
                    """),
            source("shop.ShopAreas", """
                    package shop;
                    import cloud.jengu.promise.Area;
                    import cloud.jengu.promise.Catalogue;
                    import cloud.jengu.promise.Classified;
                    import java.util.List;
                    @Catalogue(namespace = "AREA")
                    public enum ShopAreas implements Area {
                        SELLING;
                        @Override public String title() { return "Selling"; }
                        @Override public String description() { return "From the shelf to a paid order."; }
                        @Override public List<Classified> covers() { return List.of(ShopStories.CHECKOUT); }
                    }
                    """),
            source("shop.OtherAreas", """
                    package shop;
                    import cloud.jengu.promise.Area;
                    import cloud.jengu.promise.Catalogue;
                    import cloud.jengu.promise.Classified;
                    import java.util.List;
                    @Catalogue(namespace = "AREA")
                    public enum OtherAreas implements Area {
                        SHIPPING;
                        @Override public String title() { return "Shipping"; }
                        @Override public List<Classified> covers() { return List.of(); }
                    }
                    """),
            source("shop.Proving", """
                    package shop;
                    import cloud.jengu.promise.Cites;
                    import java.lang.annotation.*;
                    @Cites @Retention(RetentionPolicy.RUNTIME) @Target(ElementType.METHOD)
                    public @interface Proving { ShopPromises[] value(); }
                    """));

    private static final JavaFileObject TESTS = source("shop.CheckoutTest", """
            package shop;
            public class CheckoutTest {
                @Proving({ShopPromises.TOTAL_IS_THE_SUM, ShopPromises.LINES_ARE_PRICED})
                void totals() {}
            }
            """);

    private static Path shop() throws Exception {
        Path jar = Files.createTempDirectory("promise-shop");
        compile(jar, null, SHOP, List.of());
        compile(jar, "tested", List.of(), List.of(TESTS));
        return jar;
    }

    private static Path run() throws Exception {
        Path run = Files.createTempDirectory("run");
        Files.writeString(run.resolve("TEST-shop.CheckoutTest.xml"),
                "<testsuite name=\"shop.CheckoutTest\" tests=\"1\"><testcase name=\"totals\" "
                        + "classname=\"shop.CheckoutTest\"/></testsuite>");
        return run;
    }

    @Test
    @DisplayName("the export holds each area with its groups, each group with its kind and page, and "
            + "every promise they reach with its status, standing, citing tests and basis")
    @ProvingToolset(ToolsetPromises.THE_GRAPH_IS_EXPORTED_AS_ONE_DOCUMENT)
    void theExportHoldsTheGraphAndItsEvidence() throws Exception {
        try (URLClassLoader loader = loader(shop())) {
            Area selling = (Area) Class.forName("shop.ShopAreas", true, loader).getEnumConstants()[0];
            String json = PromiseExport.json(Evidence.read(loader, List.of(run())), List.of(selling));

            assertTrue(json.contains("\"code\": \"AREA-SELLING\",\n   \"title\": \"Selling\",\n"
                    + "   \"description\": \"From the shelf to a paid order.\",\n"
                    + "   \"groups\": [\"US-SHOP-CHECKOUT\"]"), json);
            assertTrue(json.contains("\"kind\": \"story\""), json);
            assertTrue(json.contains("\"document\": \"docs/stories/checkout.md\""), json);
            assertTrue(json.contains("\"area\": \"AREA-SELLING\""), json);
            assertTrue(json.contains("\"text\": \"An order's total is the sum of its \\\"lines\\\"\""), json);
            assertTrue(json.contains("\"status\": \"PROVEN\",\n   \"standing\": \"PASSING\",\n"
                    + "   \"citingTests\": [\"shop.CheckoutTest#totals\"]"), json);
            assertTrue(json.contains("\"code\": \"REQ-SHOP-LINES-ARE-PRICED\""),
                    "a basis the groups do not list is still exported: " + json);
            assertTrue(json.contains("\"basis\": [\"REQ-SHOP-LINES-ARE-PRICED\"]"), json);
            assertTrue(json.contains("\"basedOnBy\": [\"REQ-SHOP-REFUND-NEVER-EXCEEDS\"]"), json);
            assertTrue(json.contains("\"text\": \"A basket survives a closed browser\""), json);
            assertTrue(json.contains("\"status\": \"GAP\""), json);
            assertFalse(json.contains("AREA-SHIPPING"), "an area not asked for was exported: " + json);
        }
    }

    @Test
    @DisplayName("without areas named, every area of the graph is exported, and the command writes "
            + "the document where it is told")
    @ProvingToolset(ToolsetPromises.THE_GRAPH_IS_EXPORTED_AS_ONE_DOCUMENT)
    void everyAreaUnlessAProductNamesItsOwn() throws Exception {
        try (URLClassLoader loader = loader(shop())) {
            String json = PromiseExport.json(Evidence.read(loader, List.of()));
            assertTrue(json.contains("AREA-SELLING") && json.contains("AREA-SHIPPING"), json);
            assertTrue(json.contains("\"standing\": \"UNCITED\"") || json.contains("\"standing\": \"NO_RESULT\""),
                    "without a run nothing stands as passing: " + json);
            assertFalse(json.contains("\"standing\": \"PASSING\""), json);
            Path out = Files.createTempDirectory("export").resolve("nested/export.json");
            assertTrue(PromiseExport.run(new String[] {"--out", out.toString(),
                    "--areas", "shop.OtherAreas"}, loader));
            String written = Files.readString(out);
            assertTrue(written.startsWith("{\n \"generatedAt\": "), written);
            assertTrue(written.contains("AREA-SHIPPING") && !written.contains("AREA-SELLING"), written);
        }
    }
}
