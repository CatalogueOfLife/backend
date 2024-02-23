package life.catalogue.resources;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameMatch;
import life.catalogue.api.vocab.MatchType;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;

import javax.ws.rs.ServiceUnavailableException;

import org.junit.Test;

import static life.catalogue.ApiUtils.userCreds;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class NamesIndexResourceTest extends ResourceTestBase {

  public NamesIndexResourceTest() {
    super("/nidx/match");
  }

  @Test
  public void match() throws Exception {
    RULE.startNamesIndex();
    NameMatch match = userCreds(base.queryParam("q", "Abies alba Mill."))
      .get(NameMatch.class);

    Name abies = new Name();
    abies.setGenus("Abies");
    abies.setSpecificEpithet("alba");
    abies.getCombinationAuthorship().getAuthors().add("Mill.");
    abies.setType(NameType.SCIENTIFIC);
    abies.setRank(Rank.SPECIES);
    abies.rebuildScientificName();
    abies.rebuildAuthorship();

    assertNotNull(match);
    assertEquals(MatchType.NONE, match.getType());
  }
}