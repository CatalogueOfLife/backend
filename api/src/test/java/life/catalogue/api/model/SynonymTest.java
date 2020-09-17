package life.catalogue.api.model;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SynonymTest {

  @Test
  public void label() throws Exception {
    Name tn = new Name();
    tn.setGenus("Abies");
    tn.setSpecificEpithet("alba");
    tn.setRank(Rank.SPECIES);
    tn.setCombinationAuthorship(Authorship.yearAuthors("1899", "Miller"));
    tn.rebuildScientificName();
    tn.rebuildAuthorship();
    Taxon t = new Taxon();
    t.setName(tn);

    Name sn = new Name();
    sn.setGenus("Abies");
    sn.setSpecificEpithet("alba");
    sn.setInfraspecificEpithet("alpina");
    sn.setRank(Rank.SUBSPECIES);
    sn.setCode(NomCode.BOTANICAL);
    sn.setCombinationAuthorship(Authorship.yearAuthors("1999", "L.","DC."));
    sn.setBasionymAuthorship(Authorship.yearAuthors("1899","Lin.","Deca."));
    sn.rebuildScientificName();
    sn.rebuildAuthorship();

    Synonym s = new Synonym();
    s.setName(sn);
    s.setAccepted(t);

    assertEquals("Abies alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999", s.getLabel());

    s.setNamePhrase("non Miller 1899");
    assertEquals("Abies alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999 non Miller 1899", s.getLabel());

    t.setExtinct(true);
    assertEquals("†Abies alba subsp. alpina (Lin. & Deca., 1899) L. & DC., 1999 non Miller 1899", s.getLabel());
    assertEquals("†<i>Abies alba</i> subsp. <i>alpina</i> (Lin. & Deca., 1899) L. & DC., 1999 non Miller 1899", s.getLabelHtml());
  }
}