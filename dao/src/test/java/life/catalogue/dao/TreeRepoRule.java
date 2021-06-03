package life.catalogue.dao;

import java.io.File;
import java.io.IOException;

import org.junit.rules.ExternalResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.io.Files;


public class TreeRepoRule extends ExternalResource {
  private static final Logger LOG = LoggerFactory.getLogger(TreeRepoRule.class);
  File repo;
  
  public File getRepo() {
    return repo;
  }
  
  @Override
  protected void before() throws Throwable {
    super.before();
    repo = Files.createTempDir();
  }
  
  
  @Override
  public void after() {
    LOG.info("run TreeRepoRule at {}", repo.getAbsolutePath());
    super.after();
    try {
      if (repo != null && repo.exists()) {
        org.apache.commons.io.FileUtils.deleteDirectory(repo);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }
  }
  
}
