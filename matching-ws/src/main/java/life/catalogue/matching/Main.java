package life.catalogue.matching;

import com.beust.jcommander.JCommander;
import com.beust.jcommander.Parameter;
import com.beust.jcommander.ParameterException;
import com.beust.jcommander.Parameters;

import org.springframework.boot.SpringApplication;

import java.util.ArrayList;
import java.util.List;

/**
 * Main application class for the matching-ws module.
 * This is the class to run from the command line, or from within
 * a docker container.
 */
@Parameters(separators = "=")
public class Main {

  public static final String CLB_DATASET_ID = "clb.dataset.id";
  public static final String CLB_IDENTIFIER_DATASET_IDS = "clb.identifier.dataset.ids";
  public static final String CLB_IUCN_DATASET_ID = "clb.iucn.dataset.id";
  public static final String EXPORT_PATH = "export.path";
  public static final String INDEX_PATH = "index.path";
  public static final String V_1_ENABLED = "v1.enabled";
  public static final String MODE = "mode";

  @Parameter(names = {"--" + MODE}, order = 1, description = "The " + MODE + " to use, Defaults to WEB_APP, which will run the web services and will attempt to read the index" +
    " from the --" + INDEX_PATH + " ", converter = ExecutionModeConverter.class)
  private ExecutionMode mode = ExecutionMode.WEB_APP;

  @Parameter(names = {"--clb.url"}, description = "ChecklistBank JDBC URL")
  private String clbUrl = "jdbc:postgresql://localhost:5432/clb";

  @Parameter(names = {"--clb.user"}, description = "ChecklistBank database username")
  private String clbUsername;

  @Parameter(names = {"--clb.password"}, description = "ChecklistBank database password")
  private String clbPassword;

  @Parameter(names = {"--" + CLB_DATASET_ID}, description = "ChecklistBank dataset ID to create an index for or to export to CSV. " +
    "Required for INDEX_DB and EXPORT_CSV " + MODE + "s")
  private String datasetId;

  @Parameter(names = {"--" + CLB_IUCN_DATASET_ID}, description = "ChecklistBank dataset ID for the IUCN checklist.")
  private String statusDatasetId;

  @Parameter(names = {"--" + CLB_IDENTIFIER_DATASET_IDS}, description = "ChecklistBank dataset IDs to index for identifier matching.", arity = 1)
  private List<String> identifierDatasetIds = new ArrayList<>();

  @Parameter(names = {"--" + INDEX_PATH}, description = "File system path to the pre-generated lucene index")
  private String indexPath = "/data/matching-ws/index";

  @Parameter(names = {"--" + EXPORT_PATH}, description = "File system path to write exports from ChecklistBank to")
  private String exportPath = "/data/matching-ws/export";

  @Parameter(names = {"--" + V_1_ENABLED}, description = "Enable v1 support for the web service", arity = 1)
  private boolean v1Enabled = false;

  @Parameter(names = {"--server.port"}, description = "Enable v1 support for the web service", arity = 1)
  private Integer serverPort = 8080;

  @Parameter(names = {"--working.dir"}, description = "Working directory used to store system metadata files", arity = 1)
  private String workingDir = "/tmp";

  @Parameter(names = "--help", help = true, description = "Print help options")
  private boolean help;

  public static void main(String[] args) throws Exception {
    System.setProperty("java.util.logging.SimpleFormatter.format", ""); // hides the tomcat startup logs
    Main app = new Main();
    JCommander commander = JCommander.newBuilder()
      .addObject(app)
      .build();

    try {
      commander.parse(args);
    } catch (ParameterException e) {
      System.err.println(e.getMessage());
      commander.usage();
    }

    if ((app.mode == ExecutionMode.INDEX_DB
        || app.mode == ExecutionMode.EXPORT_CSV) && app.datasetId == null) {
      System.err.println("Missing required parameter for " + MODE + " " + app.mode + " --" + CLB_DATASET_ID);
      commander.usage();
      return;
    }

    if (app.help) {
      commander.usage();
    } else {

      SpringApplication springApplication;
      switch (app.mode) {
        case BUILD_INDEX:
          startIndexing(args);
          break;
        case EXPORT_CSV:
          startIndexing(args);
          break;
        case INDEX_CSV:
          startIndexing(args);
          break;
        case INDEX_DB:
          startIndexing(args);
          break;
        case INDEX_IDENTIFIER_CSV:
          startIndexing(args);
          break;
        case INDEX_IUCN_CSV:
          startIndexing(args);
          break;
        case WEB_APP:
          SpringApplication webApp = new SpringApplication(MatchingApplication.class);
          webApp.setAdditionalProfiles("web");
          webApp.run( args);
          break;
      }
    }
  }

  private static void startIndexing( String[] args) {
    SpringApplication springApplication = new SpringApplication(IndexingApplication.class);
    springApplication.setAdditionalProfiles("indexing");
    springApplication.run(args).close();
  }

  enum ExecutionMode {
    BUILD_INDEX,
    EXPORT_CSV,
    INDEX_IUCN_CSV,
    INDEX_IDENTIFIER_CSV,
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
