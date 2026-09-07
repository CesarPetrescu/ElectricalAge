package mods.eln.transparentnode.evaporative;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import java.util.Collection;
@RunWith(Parameterized.class)
public class EvaporationModelTest {
    @Parameterized.Parameters(name="{0}") public static Collection<Object[]> cases() {
        return EvaporationRegression.cases().stream().map(c -> new Object[]{c.name(),c.body()}).toList();
    }
    private final Runnable body;
    public EvaporationModelTest(String name, Runnable body) { this.body = body; }
    @Test public void regression() { body.run(); }
}
