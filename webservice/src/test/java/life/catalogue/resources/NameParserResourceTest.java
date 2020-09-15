package life.catalogue.resources;

import life.catalogue.api.model.Name;
import life.catalogue.api.model.ParsedNameUsage;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import javax.ws.rs.core.GenericType;

import static life.catalogue.ApiUtils.userCreds;
import static org.junit.Assert.assertEquals;

public class NameParserResourceTest extends ResourceTestBase {

  GenericType<ParsedNameUsage> PARSER_TYPE = new GenericType<ParsedNameUsage>() {};

  public NameParserResourceTest() {
    super("/parser/name");
  }

  @Test
  public void parseGet() {
    ParsedNameUsage resp = userCreds(base.queryParam("name", "Abies alba")
                                         .queryParam("authorship", "Mill.")
                                         .queryParam("code", "botanical")
    ).get(PARSER_TYPE);
    
    Name abies = new Name();
    abies.setGenus("Abies");
    abies.setSpecificEpithet("alba");
    abies.getCombinationAuthorship().getAuthors().add("Mill.");
    abies.setType(NameType.SCIENTIFIC);
    abies.setRank(Rank.SPECIES);
    abies.setCode(NomCode.BOTANICAL);
    abies.rebuildScientificName();
    abies.rebuildAuthorship();
    
    //printDiff(abies, resp.get(0).getName());
    assertEquals(abies, resp.getName());
  }
}