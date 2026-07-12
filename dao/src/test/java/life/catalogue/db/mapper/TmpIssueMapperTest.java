package life.catalogue.db.mapper;

import life.catalogue.api.vocab.Datasets;
import org.junit.Test;

/**
 * Just doing sql sanity checks, no data assertions
 */
public class TmpIssueMapperTest extends MapperTestBase<TmpIssueMapper> {

  public TmpIssueMapperTest() {
    super(TmpIssueMapper.class);
  }

  @Test
  public void external() throws Exception {
    mapper().createTmpIssuesTable(12, null);
    mapper().processIssues();
  }

  @Test
  public void project() throws Exception {
    mapper().createTmpIssuesTable(Datasets.COL, null);
    mapper().processIssues();
  }

  @Test
  public void streamExternal() throws Exception {
    try (var c = mapper().streamIssues(12, null)) {
      c.forEach(i -> {});
    }
  }

  @Test
  public void streamProject() throws Exception {
    try (var c = mapper().streamIssues(Datasets.COL, null)) {
      c.forEach(i -> {});
    }
  }
}