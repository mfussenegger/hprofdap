
package hprofdap;

import java.util.concurrent.Future;

import org.eclipse.lsp4j.debug.launch.DSPLauncher;
import org.eclipse.lsp4j.debug.services.IDebugProtocolClient;
import org.eclipse.lsp4j.jsonrpc.Launcher;

public class Main {

    public static void main(String[] args) throws Exception {
        DebugServer debugServer = new DebugServer();
        Launcher<IDebugProtocolClient> serverLauncher = DSPLauncher.createServerLauncher(
            debugServer,
            System.in,
            System.out
        );
        debugServer.setClientProxy(serverLauncher.getRemoteProxy());
        Future<Void> startListening = serverLauncher.startListening();
        startListening.get();
    }
}
