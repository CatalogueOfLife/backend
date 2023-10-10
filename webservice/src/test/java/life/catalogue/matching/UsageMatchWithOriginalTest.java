package life.catalogue.matching;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.jackson.SerdeTestBase;
import life.catalogue.api.model.IssueContainer;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameClassified;

import org.gbif.nameparser.api.Rank;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import static org.junit.Assert.assertFalse;

public class UsageMatchWithOriginalTest extends SerdeTestBase<UsageMatchWithOriginal> {

  public UsageMatchWithOriginalTest() {
    super(UsageMatchWithOriginal.class);
  }

  @Override
  public UsageMatchWithOriginal genTestValue() throws Exception {
    UsageMatch m = UsageMatchWithOriginal.empty(9923);
    SimpleNameClassified<SimpleName> orig = new SimpleNameClassified<>(SimpleName.sn(Rank.SPECIES, "Calonarius splendens", "(Rob. Henry) Niskanen"));
    UsageMatchWithOriginal nu = new UsageMatchWithOriginal(m, IssueContainer.simple(), orig);

    return nu;
  }

  @Test
  public void testSerialisation2() throws Exception {
    String json = ApiModule.MAPPER.writeValueAsString(genTestValue());
    assertFalse(StringUtils.isBlank(json));
    System.out.println(json);
    assertSerialisation(json);
  }

  public void testRoundtrip() throws Exception {
    // nothing to test, this is a read only class!
  }
}