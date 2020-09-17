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
    n.setGenus("Abies");
    n.setSpecificEpithet("alba");
    n.setNotho(NamePart.SPECIFIC);
    n.setRank(Rank.SUBSPECIES);
    n.setInfraspecificEpithet("alpina");
    n.setCombinationAuthorship(Authorship.yearAuthors("1999", "L.","DC."));
    n.setBasionymAuthorship(Authorship.yearAuthors("1899","Lin.","Deca."));
    n.setNomenclaturalNote("nom.illeg.");
    n.rebuildScientificName();
    n.rebuildAuthorship();

    Taxon t = new Taxon();
    t.setName(n);

    assertEquals("Abies × alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999 nom.illeg.", t.getLabel());

    t.setNamePhrase("sensu lato");
    assertEquals("Abies × alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999 nom.illeg. sensu lato", t.getLabel());

    t.setExtinct(true);
    assertEquals("†Abies × alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999 nom.illeg. sensu lato", t.getLabel());
    assertEquals("†<i>Abies × alba</i> subsp. <i>alpina</i> (Lin. & Deca., 1899) L. & DC., 1999 nom.illeg. sensu lato", t.getLabelHtml());
  }
}



