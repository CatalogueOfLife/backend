package life.catalogue.matching.authorship.corpus;

import life.catalogue.common.io.Resources;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * The export script is run by hand on a database nobody can test against, so the little that can be
 * checked without one is checked here: that it selects exactly the columns {@link ExportRow} reads.
 */
public class AuthorCorpusExportSqlTest {
  private static final Pattern OUTER_SELECT = Pattern.compile("COPY \\(\\s*SELECT (.+?)\\s+FROM \\(", Pattern.DOTALL);

  @Test
  public void selectsTheContractColumns() throws Exception {
    String sql = Resources.toString(CorpusIO.EXPORT_SQL);
    Matcher m = OUTER_SELECT.matcher(sql);
    assertTrue("outer select not found", m.find());
    List<String> cols = Arrays.stream(m.group(1).split(",")).map(String::trim).toList();
    assertEquals(ExportRow.COLUMNS, cols);
  }

  /**
   * An IN list does not propagate across a join, so without the filter on both partitioned tables
   * postgres scans every partition of the one it is missing on.
   */
  @Test
  public void filtersEveryPartitionedTable() throws Exception {
    String sql = Resources.toString(CorpusIO.EXPORT_SQL);
    assertTrue(sql.contains("n.dataset_key IN (:keys)"));
    assertTrue(sql.contains("nm.dataset_key IN (:keys)"));
  }
}
