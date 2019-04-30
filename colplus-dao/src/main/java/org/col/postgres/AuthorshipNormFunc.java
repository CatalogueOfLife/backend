package org.col.postgres;

import com.google.common.collect.Lists;
import org.col.api.model.Name;
import org.col.common.tax.AuthorshipNormalizer;

public class AuthorshipNormFunc {
  private final AuthorshipNormalizer anorm;
  private final int startIdx;
  
  public AuthorshipNormFunc(AuthorshipNormalizer anorm, int startIdx) {
    this.startIdx = startIdx;
    this.anorm = anorm;
  }

  public AuthorshipNormFunc(int startIdx) {
    this.startIdx = startIdx;
    anorm = AuthorshipNormalizer.createWithAuthormap();
  }
  
  public String normAuthorship (String[] row) {
    Name n = new Name();
    n.getBasionymAuthorship().setAuthors(Lists.newArrayList(PgCopyUtils.splitPgArray(row[startIdx])));
    n.getBasionymAuthorship().setExAuthors(Lists.newArrayList(PgCopyUtils.splitPgArray(row[startIdx+1])));
    n.getBasionymAuthorship().setYear(row[startIdx+2]);
    n.getCombinationAuthorship().setAuthors(Lists.newArrayList(PgCopyUtils.splitPgArray(row[startIdx+3])));
    n.getCombinationAuthorship().setExAuthors(Lists.newArrayList(PgCopyUtils.splitPgArray(row[startIdx+4])));
    n.getCombinationAuthorship().setYear(row[startIdx+5]);
    return PgCopyUtils.buildPgArray( anorm.normalizeName(n).toArray(new String[0]) );
  }
}

