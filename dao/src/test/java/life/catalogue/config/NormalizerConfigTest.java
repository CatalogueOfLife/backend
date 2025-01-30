package life.catalogue.config;

import java.io.File;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NormalizerConfigTest {

  @Test
  public void attemptFromArchive() {
    assertEquals(2, NormalizerConfig.attemptFromArchive(new File("0002.archive")));
    assertEquals(2, NormalizerConfig.attemptFromArchive(new File("2.archive")));
    assertEquals(1002, NormalizerConfig.attemptFromArchive(new File("001002.archive")));
  }
}