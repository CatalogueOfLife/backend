package life.catalogue.command;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.CitationFormatter;

import java.util.Map;

import org.junit.After;
import org.junit.Test;

import io.dropwizard.core.setup.Bootstrap;
import net.sourceforge.argparse4j.inf.Namespace;

import static org.junit.Assert.assertNotNull;
import static org.mockito.Mockito.mock;

/**
 * Verifies that {@link AbstractPromptCmd#run} registers the citeproc-backed
 * {@link CitationFormatter} before any command's {@code execute()} runs, so that all CLI
 * commands (export, etc.) render citations the same way the server does. See WsServer.run()
 * for the equivalent registration on the server entry point.
 */
public class AbstractPromptCmdTest {

  @After
  public void reset() {
    CitationFormatter.INSTANCE.set(null);
  }

  @Test
  public void registersCitationFormatterBeforeExecute() throws Exception {
    AbstractPromptCmd cmd = new AbstractPromptCmd("test", "test command") {
      @Override
      public void execute(Bootstrap<WsServerConfig> bootstrap, Namespace namespace, WsServerConfig cfg) {
        // the formatter must already be registered by the time execute() is called
        assertNotNull(CitationFormatter.get());
      }
    };

    Bootstrap<WsServerConfig> bootstrap = mock(Bootstrap.class);
    Namespace ns = new Namespace(Map.of("prompt", 0));
    WsServerConfig cfg = new WsServerConfig();

    cmd.run(bootstrap, ns, cfg);

    assertNotNull(CitationFormatter.get());
  }
}
