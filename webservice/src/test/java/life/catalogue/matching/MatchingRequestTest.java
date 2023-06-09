package life.catalogue.matching;

import life.catalogue.api.vocab.TabularFormat;

import org.junit.Test;

import java.io.File;

import static org.junit.Assert.*;

public class MatchingRequestTest {

  @Test
  public void resultFileName() {
    var req = new MatchingRequest();
    assertEquals("match-dataset-null.csv", req.resultFileName());

    req.setSourceDatasetKey(1234);
    assertEquals("match-dataset-1234.csv", req.resultFileName());

    req = new MatchingRequest();
    req.setUpload(new File("/tmp/3456dfgh.csv"));
    assertEquals("match-3456dfgh.csv", req.resultFileName());
    req.setFormat(TabularFormat.TSV);
    assertEquals("match-3456dfgh.tsv", req.resultFileName());
  }
}