package life.catalogue.resources;

import life.catalogue.api.model.Name;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import javax.ws.rs.core.GenericType;

import org.junit.Test;

import static life.catalogue.ApiUtils.userCreds;
import static org.junit.Assert.assertEquals;

public class NameParserResourceIT extends ResourceITBase {

  GenericType<Name> PARSER_TYPE = new GenericType<Name>() {};

  public NameParserResourceIT() {
    super("/parser/name");
  }

  @Test
  public void parseGet() {
    Name resp = userCreds(base.queryParam("name", "Abies alba")
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
    assertEquals(abies, resp);
  }
}