package org.col.resources;

import java.util.List;
import javax.ws.rs.core.GenericType;

import io.dropwizard.testing.ResourceHelpers;
import org.col.WsServer;
import org.col.config.WsServerConfig;
import org.col.api.model.Name;
import org.col.api.model.NameAccordingTo;
import org.col.dw.DropwizardPgAppRule;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.Rank;
import org.junit.ClassRule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ParserResourceTest {
  
  @ClassRule
  public static final DropwizardPgAppRule<WsServerConfig> RULE =
      new DropwizardPgAppRule<>(WsServer.class, ResourceHelpers.resourceFilePath("config-test.yaml"));
  
  GenericType<List<NameAccordingTo>> PARSER_TYPE = new GenericType<List<NameAccordingTo>>() {
  };
  
  @Test
  public void parseGet() {
    List<NameAccordingTo> resp = RULE.client().target(
        String.format("http://localhost:%d/parser/name", RULE.getLocalPort()))
        .queryParam("name", "Abies alba Mill.")
        .request()
        .get(PARSER_TYPE);
    
    Name abies = new Name();
    abies.setGenus("Abies");
    abies.setSpecificEpithet("alba");
    abies.getCombinationAuthorship().getAuthors().add("Mill.");
    abies.setType(NameType.SCIENTIFIC);
    abies.setRank(Rank.SPECIES);
    abies.updateNameCache();
    
    assertEquals(1, resp.size());
    assertEquals(abies, resp.get(0).getName());
  }
}