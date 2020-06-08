package life.catalogue.api.model;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.jackson.SerdeTestBase;
import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NamePart;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

/**
 *
 */
public class TaxonTest extends SerdeTestBase<Taxon> {
  
  public TaxonTest() {
    super(Taxon.class);
  }
  
  @Override
  public Taxon genTestValue() throws Exception {
    return TestEntityGenerator.newTaxon("alpha7");
  }

  @Test
  public void conversionAndFormatting() throws Exception {
    Name n = new Name();
    Taxon t = new Taxon();
    t.setName(n);

    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setNotho(NamePart.SPECIFIC);
    n.setRank(Rank.SUBSPECIES);
    assertEquals("Abies × alba ssp.", t.getLabel());

    n.setInfraspecificEpithet("alpina");
    n.setCombinationAuthorship(Authorship.yearAuthors("1999", "L.","DC."));
    n.setBasionymAuthorship(Authorship.yearAuthors("1899","Lin.","Deca."));
    assertEquals("Abies × alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999", t.getName());
    assertEquals("Abies × alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999", t.getLabel());

    n.setRemarks("nom.illeg.");
    assertEquals("Abies × alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999, nom.illeg.", t.getLabel());

    t.setNamePhrase("bla bla");
    assertEquals("Abies × alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999, nom.illeg. bla bla", t.getLabel());
  }
}



