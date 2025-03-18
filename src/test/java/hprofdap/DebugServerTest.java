
package hprofdap;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import javax.management.MBeanServer;

import org.eclipse.lsp4j.debug.EvaluateArguments;
import org.eclipse.lsp4j.debug.InitializeRequestArguments;
import org.eclipse.lsp4j.debug.Variable;
import org.eclipse.lsp4j.debug.VariablesArguments;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

public class DebugServerTest {

    record TestRecord(List<String> items, int x) {
    }

    @Test
    public void test_debugserver(@TempDir Path tmpfile) throws Exception {
        final TestRecord testRecord = new TestRecord(List.of("Arthur", "Trillian"), 10);
        assertThat(testRecord).isNotNull();

        MBeanServer server = ManagementFactory.getPlatformMBeanServer();
        com.sun.management.HotSpotDiagnosticMXBean mxBean = ManagementFactory.newPlatformMXBeanProxy(
            server,
            "com.sun.management:type=HotSpotDiagnostic",
            com.sun.management.HotSpotDiagnosticMXBean.class
        );

        Path hprofPath = tmpfile.resolve("dummy.hprof");
        mxBean.dumpHeap(hprofPath.toString(), false);
        DebugServer debugServer = new DebugServer();
        InitializeRequestArguments args = new InitializeRequestArguments();
        debugServer.initialize(args);
        debugServer.launch(Map.of(
            "name", "hprof",
            "request", "launch",
            "type", "hprof",
            "filepath", hprofPath.toString()
        )).get();

        var evalArgs = new EvaluateArguments();
        evalArgs.setExpression("select x from hprofdap.DebugServerTest$TestRecord x");
        var evaluateResponse = debugServer.evaluate(evalArgs).get();
        assertThat(evaluateResponse.getResult()).isEqualTo("hprofdap.DebugServerTest$TestRecord");
        assertThat(evaluateResponse.getVariablesReference()).isEqualTo(1);

        var variablesArguments = new VariablesArguments();
        variablesArguments.setVariablesReference(1);
        var variablesResponse = debugServer.variables(variablesArguments).get();
        Variable itemsVar = new Variable();
        itemsVar.setName("items");
        itemsVar.setValue("java.util.ImmutableCollections$List12");
        itemsVar.setVariablesReference(2);
        Variable xVar = new Variable();
        xVar.setName("x");
        xVar.setValue("10");
        xVar.setType("Integer");
        xVar.setVariablesReference(0);
        assertThat(variablesResponse.getVariables()).containsExactly(
            itemsVar,
            xVar
        );
    }
}
