package org.col.api.model;

import org.col.api.jackson.SerdeTestBase;
import org.col.api.vocab.Issue;
import org.col.api.vocab.NomStatus;
import org.gbif.nameparser.api.Rank;

/**
 *
 */
public class NameSearchRequestSerde extends SerdeTestBase<NameSearchRequest> {

  public NameSearchRequestSerde() {
    super(NameSearchRequest.class);
  }

  @Override
  public NameSearchRequest genTestValue() throws Exception {
    NameSearchRequest s = new NameSearchRequest();
    s.setQ("Abies");
    s.setNomStatus(NomStatus.MANUSCRIPT);
    s.setRank(Rank.VARIETY);
    s.setIssue(Issue.ESCAPED_CHARACTERS);
    return s;
  }
}