package life.catalogue.matching;

import com.beust.jcommander.*;

import org.springframework.boot.SpringApplication;

import sun.misc.Unsafe;

import java.lang.reflect.Field;
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
  public static final String MODE = "mode";

  @Parameter(names = {"--" + MODE}, order = 1, description = "The " + MODE + " to use, Defaults to INDEX_RUN", converter = ExecutionModeConverter.class)
  private ExecutionMode mode = ExecutionMode.INDEX_AND_RUN;

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

  @Parameter(names = {"--server.port"}, description = "Enable v1 support for the web service", arity = 1)
  private Integer serverPort = 8080;

  @Parameter(names = {"--working.dir"}, description = "Working directory used to store system metadata files", arity = 1)
  private String workingDir = "/tmp";

  @Parameter(names = "--help", help = true, description = "Print help options")
  private boolean help;

  public static void main(String[] args) throws Exception {
    disableWarning();
    System.setProperty("java.util.logging.SimpleFormatter.format", ""); // hides the tomcat startup logs
    Main app = new Main();
    JCommander commander = JCommander.newBuilder()
      .addObject(app)
      .acceptUnknownOptions(true)
      .build();

    try {
      commander.parseWithoutValidation(args);
    } catch (ParameterException e) {
      System.err.println(e.getMessage());
      commander.usage();
    }

    if (app.help) {
      commander.usage();
    } else {
      SpringApplication webApp = new SpringApplication(MatchingApplication.class);
      webApp.setAdditionalProfiles("web");
      webApp.run(args);
    }
  }

  public static void disableWarning() {
    try {
      Field theUnsafe = Unsafe.class.getDeclaredField("theUnsafe");
      theUnsafe.setAccessible(true);
      Unsafe u = (Unsafe) theUnsafe.get(null);
      Class cls = Class.forName("jdk.internal.module.IllegalAccessLogger");
      Field logger = cls.getDeclaredField("logger");
      u.putObjectVolatile(cls, u.staticFieldOffset(logger), null);
    } catch (Exception e) {
      // ignore
    }
  }

  enum ExecutionMode {
    RUN,
    INDEX,
    INDEX_AND_RUN
  }

  static class ExecutionModeConverter implements com.beust.jcommander.IStringConverter<ExecutionMode> {
    @Override
    public ExecutionMode convert(String value) {
      return ExecutionMode.valueOf(value.toUpperCase());
    }
  }
}
