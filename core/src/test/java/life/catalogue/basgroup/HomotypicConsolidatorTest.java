package life.catalogue.basgroup;

import life.catalogue.api.model.LinneanNameUsage;
import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class HomotypicConsolidatorTest {

  @Test
  public void findPrimaryUsage() {
    var hc = HomotypicConsolidator.forTaxa(null, 3, List.of(), u -> 1);

    var bg = new HomotypicGroup<LinneanNameUsage>(null, "sapiens", Authorship.authors("Linnaeus"), NomCode.ZOOLOGICAL);
    bg.setBasionym(lnu("1", Rank.SUBSPECIES, "Nasua olivacea quitensis", "Lönnberg, 1913"));
    bg.addRecombination(lnu("2", Rank.SUBSPECIES, "Nasuella olivacea quitensis", "(Lönnberg, 1913)", TaxonomicStatus.SYNONYM, "1"));
    var primary = hc.findPrimaryUsage(bg);
    assertEquals("1", primary.getId());
  }

  public static LinneanNameUsage lnu(String id, Rank rank, String name, String authorship) {
    return lnu(id, rank, name, authorship, TaxonomicStatus.ACCEPTED, null);
  }
  public static LinneanNameUsage lnu(String id, Rank rank, String name, String authorship, TaxonomicStatus status, String parentID) {
    LinneanNameUsage lnu = new LinneanNameUsage();
    lnu.setId(id);
    lnu.setParentId(parentID);
    lnu.setRank(rank);
    lnu.setAuthorship(authorship);
    lnu.setScientificName(name);
    lnu.setStatus(status);
    return lnu;
  }
}