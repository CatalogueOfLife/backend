package life.catalogue.postgres;

import life.catalogue.api.model.Name;
import life.catalogue.pgcopy.CsvFunction;
import life.catalogue.pgcopy.PgCopyUtils;

import java.util.LinkedHashMap;
import java.util.List;

import com.google.common.collect.Lists;

/**
 * Normalizes a parsed authorship to be used for indexing in postgres.
 */
public class PgAuthorshipNormalizer implements CsvFunction {
  private static final String COLUMN = "authorship_normalized";
  private int startIdx = -1;
  // optional, not all data carries the anonymous flags
  private int basAnonIdx = -1;
  private int combAnonIdx = -1;

  @Override
  public void init(List<String> headers) {
    startIdx = headers.indexOf("basionym_authors");
    if (startIdx < 0) throw new IllegalStateException("Cannot find parsed author columns");
    basAnonIdx = headers.indexOf("basionym_anonymous");
    combAnonIdx = headers.indexOf("combination_anonymous");
  }

  @Override
  public List<String> columns() {
    return List.of(COLUMN);
  }

  @Override
  public LinkedHashMap<String, String> apply(String[] row) {
    Name n = new Name();
    n.getBasionymAuthorship().setAuthors(Lists.newArrayList(PgCopyUtils.splitPgArray(row[startIdx])));
    n.getBasionymAuthorship().setExAuthors(Lists.newArrayList(PgCopyUtils.splitPgArray(row[startIdx+1])));
    n.getBasionymAuthorship().setYear(row[startIdx+2]);
    n.getCombinationAuthorship().setAuthors(Lists.newArrayList(PgCopyUtils.splitPgArray(row[startIdx+3])));
    n.getCombinationAuthorship().setExAuthors(Lists.newArrayList(PgCopyUtils.splitPgArray(row[startIdx+4])));
    n.getCombinationAuthorship().setYear(row[startIdx+5]);
    n.getBasionymAuthorship().setAnonymous(bool(row, basAnonIdx));
    n.getCombinationAuthorship().setAnonymous(bool(row, combAnonIdx));

    var data = new LinkedHashMap<String, String>();
    data.put(COLUMN, PgCopyUtils.buildPgArray( life.catalogue.common.tax.AuthorshipNormalizer.INSTANCE.normalizeName(n).toArray(new String[0]) ));
    return data;
  }

  private static boolean bool(String[] row, int idx) {
    return idx >= 0 && ("t".equalsIgnoreCase(row[idx]) || "true".equalsIgnoreCase(row[idx]));
  }
}

