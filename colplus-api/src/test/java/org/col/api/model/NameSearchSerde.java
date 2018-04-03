package org.col.api.model;

import org.col.api.jackson.SerdeTestBase;
import org.col.api.vocab.Issue;
import org.col.api.vocab.NomStatus;
import org.gbif.nameparser.api.Rank;

/**
 *
 */
public class NameSearchSerde extends SerdeTestBase<NameSearch> {

  public NameSearchSerde() {
    super(NameSearch.class);
  }

  @Override
  public NameSearch genTestValue() throws Exception {
    NameSearch s = new NameSearch();
    s.setQ("Abies");
    s.setNomStatus(NomStatus.MANUSCRIPT);
    s.setRank(Rank.VARIETY);
    s.setIssue(Issue.ESCAPED_CHARACTERS);
    return s;
  }
}