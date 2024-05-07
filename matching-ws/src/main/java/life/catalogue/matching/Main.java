package life.catalogue.matching;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import org.springframework.boot.SpringApplication;

/**
 * Main application class for the matching-ws module.
 */
@Parameters(separators = "=")
public class Main {

  @Parameter(names = {"--mode"}, order = 1, description = "The mode to use, Defaults to WEB_APP, which will run the web services and will attempt to read the index" +
    " from the --index.path ", converter = ExecutionModeConverter.class)
  private ExecutionMode mode = ExecutionMode.WEB_APP;

  @Parameter(names = {"--clb.url"}, description = "ChecklistBank JDBC URL")
  private String clbUrl = "jdbc:postgresql://localhost:5432/clb";

  @Parameter(names = {"--clb.user"}, description = "ChecklistBank database username")
  private String clbUsername;

  @Parameter(names = {"--clb.password"}, description = "ChecklistBank database password")
  private String clbPassword;

  @Parameter(names = {"--clb.dataset.id"}, description = "ChecklistBank dataset ID to create an index for or to export to CSV. " +
    "Required for INDEX_DB and EXPORT_CSV modes")
  private String datasetId;

  @Parameter(names = {"--index.path"}, description = "File system path to the pre-generated lucene index")
  private String indexPath = "/data/matching-ws/index";

  @Parameter(names = {"--export.path"}, description = "File system path to write exports from ChecklistBank to")
  private String exportPath = "/data/matching-ws/export";

  @Parameter(names = "--help", help = true, description = "Print help options")
  private boolean help;

  public static void main(String[] args) throws Exception {
    Main app = new Main();
    JCommander commander = JCommander.newBuilder()
      .addObject(app)
      .build();

    try {
      commander.parse(args);
    } catch (ParameterException e) {
      commander.usage();
    }

    if ((app.mode == ExecutionMode.INDEX_DB || app.mode == ExecutionMode.EXPORT_CSV) && app.datasetId == null) {
      System.err.println("Missing required parameter for mode " + app.mode + " --clb.dataset.id");
      commander.usage();
      return;
    }

    if (app.help) {
      commander.usage();
    } else {

      SpringApplication springApplication;
      switch (app.mode) {
        case EXPORT_CSV, INDEX_CSV, INDEX_DB:
          springApplication = new SpringApplication(IndexingApplication.class);
          springApplication.setAdditionalProfiles("indexing");
          springApplication.run( args).close();
          break;
        case WEB_APP:
          SpringApplication webApp = new SpringApplication(MatchingApplication.class);
          webApp.setAdditionalProfiles("web");
          webApp.run( args);
          break;
      }
    }
  }

  enum ExecutionMode {
    EXPORT_CSV,
    INDEX_CSV,
    INDEX_DB,
    WEB_APP
  }

  static class ExecutionModeConverter implements com.beust.jcommander.IStringConverter<ExecutionMode> {
    @Override
    public ExecutionMode convert(String value) {
      return ExecutionMode.valueOf(value.toUpperCase());
    }
  }
}
