package life.catalogue.matching;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration;
import org.springframework.context.annotation.Profile;

/**
 * Main application class for the matching-ws module.
 */
@SpringBootApplication(exclude = {DataSourceAutoConfiguration.class})
@Profile("web")
public class MatchingApplication implements ApplicationRunner {

  private static final Logger LOG = LoggerFactory.getLogger(MatchingService.class);
  @Autowired protected MatchingService matchingService;

  @Override
  public void run(ApplicationArguments args) {
    // generate the index metadata if not present
    IndexMetadata metadata = matchingService.getIndexMetadata();
    LOG.info("Web services started. Index size: {} taxa", metadata.getTaxonCount());
  }
}
