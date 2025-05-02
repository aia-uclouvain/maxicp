package org.maxicp.xcsp3;

import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.maxicp.search.DFSearch;
import org.maxicp.util.ImmutableSet;
import org.maxicp.util.exception.InconsistencyException;
import org.maxicp.util.exception.NotImplementedException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.junit.Assume.assumeNoException;
import static org.maxicp.search.Searches.EMPTY;

@RunWith(Parameterized.class)
public class CheckReadMini {

    @Parameterized.Parameters(name = "{0}")
    public static String[] data() {
        try {
            return Files.walk(Paths.get("data/xcsp3/tests/mini")).filter(Files::isRegularFile)
                    .filter(x -> x.toString().contains("xml"))
                    .map(Path::toString).toArray(String[]::new);
        }
        catch (IOException ex) {
            assumeNoException(ex);
            return new String[]{};
        }
    }

    @Parameterized.Parameter
    public String filename;

    public static final ImmutableSet<String> ignored = ImmutableSet.of(
            "AircraftAssemblyLine-3-628-000-0_c24.xml"
    );

    public void checkIgnored() {
        String[] fname = filename.split("/");
        Assume.assumeTrue("Instance has been blacklisted", !ignored.contains(fname[fname.length-1]));
        Assume.assumeTrue("Instance has been blacklisted", !fname[fname.length-1].contains("Subisomorphism"));
        Assume.assumeTrue("Instance has been blacklisted", !fname[fname.length-1].contains("OpenStacks-m2c"));
    }

    @Test
    public void checkRead() throws Exception {
        checkIgnored();
        try (XCSP3.XCSP3LoadedInstance instance = XCSP3.load(filename)) {
            instance.md().runCP((cp) -> {
                DFSearch search = cp.dfSearch(() -> EMPTY);
                search.solve();
            });
        }
        catch (NotImplementedException ex) {
            Assume.assumeNoException(ex);
        }
        catch (InconsistencyException ex) {
            //ignore
        }
        finally {
            System.gc();
        }
    }
}
