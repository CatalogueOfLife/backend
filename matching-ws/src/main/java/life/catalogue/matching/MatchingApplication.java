package life.catalogue.matching;

import java.time.Duration;
import java.time.LocalTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MatchingApplication implements CommandLineRunner {

  private static Logger LOG = LoggerFactory.getLogger(MatchingApplication.class);

  @Autowired IndexingService datasetIndexingService;

  public static void main(String[] args) {

    // FIXME: needs to check the index exists and if it doesnt exist, create it

    WebApplicationType appType;
    if (args.length > 0) {
      appType = WebApplicationType.NONE;
    } else {
      appType = WebApplicationType.SERVLET;
    }

    SpringApplication application = new SpringApplication(MatchingApplication.class);
    application.setWebApplicationType(appType);
    application.run(args);
  }

  public void run(String... args) throws Exception {
    if (args.length > 0) {
      LocalTime start = LocalTime.now();
      LOG.info("Starting indexing...");
      datasetIndexingService.runDatasetIndexing(args[0]);
      LocalTime end = LocalTime.now();
      Duration duration = Duration.between(start, end);
      LOG.info(
          "Indexing finished in {} min, {} secs",
          duration.toMinutes() % 60,
          duration.getSeconds() % 60);
    }
  }
}
