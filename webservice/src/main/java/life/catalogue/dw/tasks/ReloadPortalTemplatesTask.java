package life.catalogue.dw.tasks;

import life.catalogue.portal.PortalPageRenderer;

import java.io.PrintWriter;
import java.util.List;
import java.util.Map;

import io.dropwizard.servlets.tasks.Task;

/**
 * Reloads all portal templates from disk.
 */
public class ReloadPortalTemplatesTask extends Task {
  private final PortalPageRenderer renderer;

  public ReloadPortalTemplatesTask(PortalPageRenderer renderer) {
    super("reload-portal-templates");
    this.renderer = renderer;
  }

  @Override
  public void execute(Map<String, List<String>> parameters, PrintWriter output) throws Exception {
    renderer.loadTemplates();
    output.println("Reloaded portal templates.");
    output.flush();
  }
}