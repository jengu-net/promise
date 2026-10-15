package cloud.jengu.promise;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;

/**
 * A class loader over a fixture's own output: the framework's classes from the
 * test's loader, and indexes and reports from the fixture alone.
 *
 * <p>The test's own classpath carries indexes of its own — the toolset's
 * catalogue, the citations of these very tests, the fixture catalogues they
 * compile beside — and a fixture read through a loader that also saw those
 * would be measuring the test suite rather than the fixture.
 */
final class Isolated extends URLClassLoader {

    Isolated(URL... urls) {
        super(urls, Isolated.class.getClassLoader());
    }

    @Override
    public Enumeration<URL> getResources(String name) throws IOException {
        return findResources(name);
    }

    @Override
    public URL getResource(String name) {
        return findResource(name);
    }
}
