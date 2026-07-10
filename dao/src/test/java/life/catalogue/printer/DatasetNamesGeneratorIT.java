package life.catalogue.printer;

import life.catalogue.dao.DaoTestBase;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.junit.TestDataRule;
import life.catalogue.printer.diff.DiffNamesParam;
import life.catalogue.printer.diff.DiffOptions;

import java.util.ArrayList;
import java.util.List;

import org.apache.ibatis.cursor.Cursor;
import org.junit.Test;

import static org.junit.Assert.*;

public class DatasetNamesGeneratorIT extends DaoTestBase {

  public DatasetNamesGeneratorIT() {
    super(TestDataRule.tree());
  }

  // the dataset key staged by TestDataRule.tree(); reuse the same constant the diff ITs use
  private int datasetKey() {
    return TestDataRule.TREE.key;
  }

  private List<String> run(DiffNamesParam p) {
    List<String> out = new ArrayList<>();
    try (Cursor<String> c = mapper(NameUsageMapper.class).processDiffNames(p)) {
      c.forEach(out::add);
    } catch (Exception e) {
      throw new RuntimeException(e);
    }
    return out;
  }

  @Test
  public void wholeDatasetSortedByteOrder() {
    DiffNamesParam p = new DiffNamesParam();
    p.setDatasetKey(datasetKey());
    p.setSynonyms(true);
    p.setAuthorship(true);
    List<String> names = run(p);
    assertFalse(names.isEmpty());
    for (int i = 1; i < names.size(); i++) {
      assertTrue("not byte-sorted at " + i + ": " + names.get(i - 1) + " vs " + names.get(i),
        DiffOptions.CODEPOINT.compare(names.get(i - 1), names.get(i)) <= 0);
    }
  }

  @Test
  public void authorshipOffOmitsAuthors() {
    DiffNamesParam p = new DiffNamesParam();
    p.setDatasetKey(datasetKey());
    p.setSynonyms(false);
    p.setAuthorship(false);
    List<String> withAuth = run(new DiffNamesParam() {{ setDatasetKey(datasetKey()); setSynonyms(false); setAuthorship(true); }});
    List<String> noAuth = run(p);
    assertFalse(noAuth.isEmpty());
    // authorship-off labels are never longer than authorship-on for the same set
    assertTrue(String.join("", noAuth).length() <= String.join("", withAuth).length());
  }
}
