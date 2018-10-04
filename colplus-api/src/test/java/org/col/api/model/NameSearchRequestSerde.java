package org.col.api.model;

import org.col.api.jackson.SerdeTestBase;
import org.col.api.search.NameSearchParameter;
import org.col.api.search.NameSearchRequest;
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
    s.addFilter(NameSearchParameter.NOM_STATUS, NomStatus.MANUSCRIPT);
    s.addFilter(NameSearchParameter.NOM_STATUS, Rank.VARIETY);
    s.addFilter(NameSearchParameter.NOM_STATUS, Issue.ESCAPED_CHARACTERS);
    return s;
  }
}