package life.catalogue.postgres;

import life.catalogue.api.model.Name;

import com.google.common.collect.Lists;

import life.catalogue.pgcopy.CsvFunction;
import life.catalogue.pgcopy.PgCopyUtils;

import java.util.LinkedHashMap;
import java.util.List;

/**
 * Normalizes a parsed authorship to be used for indexing in postgres.
 */
public class PgAuthorshipNormalizer implements CsvFunction {
  private static final String COLUMN = "authorship_normalized";
  private int startIdx = -1;

  @Override
  public void init(List<String> headers) {
    int idx = 0;
    for (var h : headers) {
      if (h.equals("basionym_authors")) {
        startIdx = idx;
        break;
      }
      idx++;
    }
    if (startIdx < 0) throw new IllegalStateException("Cannot find parsed author columns");
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

    var data = new LinkedHashMap<String, String>();
    data.put(COLUMN, PgCopyUtils.buildPgArray( life.catalogue.common.tax.AuthorshipNormalizer.INSTANCE.normalizeName(n).toArray(new String[0]) ));
    return data;
  }
}

