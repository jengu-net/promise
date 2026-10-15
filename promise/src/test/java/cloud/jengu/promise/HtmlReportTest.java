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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ledger is one page drawn from the same reading as every other report:
 * the composed model and, given a run, the evidence behind it.
 */
class HtmlReportTest {

    /** A text a catalogue might hold that would close the page's script and run as markup. */
    private static final String HOSTILE = "</script><script>alert(1)</script>";

    private static final List<JavaFileObject> SHOP = List.of(
            source("shop.ShopPromises", """
                    package shop;
                    import cloud.jengu.promise.Catalogue;
                    import cloud.jengu.promise.Promise;
                    @Catalogue(namespace = "REQ-SHOP")
                    public enum ShopPromises implements Promise {
                        TOTAL_IS_THE_SUM("An order's total is the sum of its lines %s"),
                        REFUND_NEVER_EXCEEDS("A refund never exceeds what was paid");
                        private final String text;
                        ShopPromises(String text) { this.text = text; }
                        @Override public String text() { return text; }
                    }
                    """.formatted(HOSTILE.replace("\"", "\\\""))),
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
                        @Override public String description() { return "From the basket to a paid order."; }
                        @Override public List<Promise> promises() {
                            return List.of(ShopPromises.TOTAL_IS_THE_SUM, ShopPromises.REFUND_NEVER_EXCEEDS);
                        }
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
                @Proving(ShopPromises.TOTAL_IS_THE_SUM)
                void totals() {}
            }
            """);

    private static Path shop() throws Exception {
        Path jar = Files.createTempDirectory("promise-shop");
        compile(jar, null, SHOP, List.of());
        compile(jar, "tested", List.of(), List.of(TESTS));
        return jar;
    }

    private static Path run(String testClass) throws Exception {
        Path run = Files.createTempDirectory("run");
        Files.writeString(run.resolve("TEST-" + testClass + ".xml"),
                "<testsuite name=\"" + testClass + "\" tests=\"1\"><testcase name=\"totals\" classname=\""
                        + testClass + "\"/></testsuite>");
        return run;
    }

    /** The JSON the page reads, as the page holds it. */
    private static String model(String page) {
        String open = "<script type=\"application/json\" id=\"model\">";
        int start = page.indexOf(open) + open.length();
        return page.substring(start, page.indexOf("</script>", start));
    }

    @Test
    @DisplayName("the ledger carries the composed model and the run: every promise with its "
            + "status and standing, each group with its description, each test class's result")
    @ProvingToolset(ToolsetPromises.THE_LEDGER_IS_ONE_PAGE)
    void theLedgerCarriesTheModelAndTheRun() throws Exception {
        try (URLClassLoader loader = loader(shop())) {
            String page = HtmlReport.render(Evidence.read(loader, List.of(run("shop.CheckoutTest"))),
                    "The shop & its promises");
            assertTrue(page.startsWith("<!doctype html>"), "not a document of its own");
            assertTrue(page.contains("<title>The shop &amp; its promises</title>"), page.substring(0, 400));
            String model = model(page);
            assertTrue(model.contains("\"REQ-SHOP-TOTAL-IS-THE-SUM\":{"), model);
            assertTrue(model.contains("\"status\":\"PROVEN\",\"gap\":false,\"catalogue\":\"REQ-SHOP\","
                    + "\"standing\":\"PASSING\""), model);
            assertTrue(model.contains("\"REQ-SHOP-REFUND-NEVER-EXCEEDS\":{\"text\":\"A refund never "
                    + "exceeds what was paid\",\"status\":\"PLANNED\""), model);
            assertTrue(model.contains("\"kind\":\"story\",\"code\":\"US-SHOP-CHECKOUT\""), model);
            assertTrue(model.contains("\"description\":\"From the basket to a paid order.\""), model);
            assertTrue(model.contains("\"shop.CheckoutTest\":{\"tests\":1,\"failures\":0"), model);
            assertTrue(model.contains("\"tested\":[\"shop.CheckoutTest#totals\"]"), model);
        }
    }

    @Test
    @DisplayName("nothing a catalogue holds can close the page's script or run as markup")
    @ProvingToolset(ToolsetPromises.THE_LEDGER_IS_ONE_PAGE)
    void aCatalogueCannotWriteMarkupIntoThePage() throws Exception {
        try (URLClassLoader loader = loader(shop())) {
            String page = HtmlReport.render(Evidence.read(loader, List.of()), "<b>shop</b>");
            assertFalse(page.contains(HOSTILE), "a promise's text was written into the page as markup");
            assertFalse(page.contains("<title><b>"), "the title was written into the page as markup");
            assertEquals(2, page.split("</script>", -1).length - 1,
                    "the page holds two script elements and nothing else may close one");
            assertTrue(model(page).contains("\\u003c/script\\u003e"), model(page));
        }
    }

    @Test
    @DisplayName("the summary leads with what is kept and the last run, and names the groups "
            + "that are not kept")
    @ProvingToolset(ToolsetPromises.THE_LEDGER_IS_ONE_PAGE)
    void theSummaryLeadsWithWhatIsKept() throws Exception {
        try (URLClassLoader loader = loader(shop())) {
            String summary = HtmlReport.summary(
                    Evidence.read(loader, List.of(run("shop.CheckoutTest"))), "Shop");
            assertTrue(summary.startsWith("## Shop: 50% kept\n"), summary);
            assertTrue(summary.contains("**1 of 2** promises kept · last run: 1 passing, 0 failing, "
                    + "0 without a result, 1 uncited"), summary);
            assertTrue(summary.contains("| Stories | 1 | 1 / 2 | 1 / 2 |"), summary);
            assertTrue(summary.contains("- `US-SHOP-CHECKOUT` Somebody buys what is in their "
                    + "basket — 1 / 2 kept"), summary);
        }
    }

    @Test
    @DisplayName("the badge says how much is kept and how the run went, amber while a promise "
            + "waits for a test, red when a citing test failed, and claims no run it was not given")
    @ProvingToolset(ToolsetPromises.THE_BADGE_SAYS_WHAT_THE_LEDGER_SAYS)
    void theBadgeSaysWhatTheLedgerSays() throws Exception {
        try (URLClassLoader loader = loader(shop())) {
            String waiting = HtmlReport.badge(
                    Evidence.read(loader, List.of(run("shop.CheckoutTest"))), true);
            assertTrue(waiting.startsWith("<svg xmlns=\"http://www.w3.org/2000/svg\""), waiting);
            assertTrue(waiting.contains("aria-label=\"promises: 50% kept · 1/2 passing\""), waiting);
            assertTrue(waiting.contains("fill=\"#b9802f\""), "a ledger with a promise waiting "
                    + "for a test is not amber: " + waiting);

            Path failed = Files.createTempDirectory("run");
            Files.writeString(failed.resolve("TEST-shop.CheckoutTest.xml"),
                    "<testsuite name=\"shop.CheckoutTest\" tests=\"1\"><testcase name=\"totals\" "
                            + "classname=\"shop.CheckoutTest\"><failure message=\"no\"/></testcase></testsuite>");
            String red = HtmlReport.badge(Evidence.read(loader, List.of(failed)), true);
            assertTrue(red.contains("aria-label=\"promises: 50% kept · 1 failing\""), red);
            assertTrue(red.contains("fill=\"#b4433a\""), "a failing ledger is not red: " + red);

            String noRun = HtmlReport.badge(Evidence.read(loader, List.of()), true);
            assertTrue(noRun.contains("aria-label=\"promises: 50% kept\""), noRun);
        }
    }

    @Test
    @DisplayName("without a run, the ledger shows status alone and claims no standing")
    @ProvingToolset(ToolsetPromises.THE_LEDGER_IS_ONE_PAGE)
    void withoutARunThereIsNoStanding() throws Exception {
        try (URLClassLoader loader = loader(shop())) {
            String page = HtmlReport.render(Registry.load(loader).model(Proofs.load(loader)), "Shop");
            assertFalse(model(page).contains("\"standing\""), model(page));
            assertTrue(model(page).contains("\"status\":\"PROVEN\""), model(page));
        }
    }
}
