package org.col.admin.resources;

import javax.ws.rs.client.Client;

import io.dropwizard.client.JerseyClientBuilder;
import io.dropwizard.testing.ResourceHelpers;
import org.col.admin.AdminServer;
import org.col.admin.config.AdminServerConfig;
import org.col.api.model.Name;
import org.col.api.model.NameMatch;
import org.col.api.vocab.MatchType;
import org.col.dw.DropwizardPgAppRule;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

public class MatchingResourceTest {


  @ClassRule
  public static final DropwizardPgAppRule<AdminServerConfig> RULE =
      new DropwizardPgAppRule<>(AdminServer.class, ResourceHelpers.resourceFilePath("config-test.yaml"));

  @Test
  public void match() {
    Client client = new JerseyClientBuilder(RULE.getEnvironment()).build("test client");

    NameMatch match = client.target(
        String.format("http://localhost:%d/name/matching", RULE.getLocalPort()))
        .queryParam("q", "Abies alba Mill.")
        .request()
        .get(NameMatch.class);

    Name abies = new Name();
    abies.setGenus("Abies");
    abies.setSpecificEpithet("alba");
    abies.getCombinationAuthorship().getAuthors().add("Mill.");
    abies.setType(NameType.SCIENTIFIC);
    abies.setRank(Rank.SPECIES);
    abies.updateScientificName();

    assertNotNull(match);
    assertEquals(MatchType.NONE, match.getType());
  }
}