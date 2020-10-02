package life.catalogue.api.search;

import life.catalogue.api.jackson.SerdeTestBase;

public class NameUsageSuggestRequestTest extends SerdeTestBase<NameUsageSuggestRequest> {

  public NameUsageSuggestRequestTest() {
    super(NameUsageSuggestRequest.class);
  }

  @Override
  public NameUsageSuggestRequest genTestValue() throws Exception {
    NameUsageSuggestRequest req = new NameUsageSuggestRequest();
    req.setQ("Abies");
    req.setAccepted(true);
    req.setFuzzy(true);
    return req;
  }
}