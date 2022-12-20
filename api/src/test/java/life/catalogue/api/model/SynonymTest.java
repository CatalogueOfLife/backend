package life.catalogue.api.model;

import life.catalogue.api.vocab.TaxonomicStatus;

import org.gbif.nameparser.api.Authorship;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.junit.Assert;
import org.junit.Test;

import javax.validation.constraints.AssertFalse;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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

  @Test
  public void sorting() throws Exception {
    List<Synonym> syns = new ArrayList<>();
    syns.add(syn(1,Rank.SPECIES, "Abies alba", null, 1887));
    syns.add(syn(2,Rank.SPECIES, "Abies alba", "Miller", 1887));
    syns.add(syn(3,Rank.SPECIES, "Abies alba", null, null));
    syns.add(syn(4,Rank.SPECIES, "Picea picea", "(L.) DC.", 1828));
    syns.add(syn(5,Rank.SPECIES, "Abies picea", "L.", 1823));
    syns.add(syn(6,Rank.SPECIES, "Picea zertifa", null, null));
    syns.add(syn(7,Rank.SPECIES, "Picea abia", null, null));

    Collections.sort(syns);
    assertEquals("5", syns.get(0).getId());
    assertEquals("4", syns.get(1).getId());
    assertEquals("1", syns.get(2).getId()); // authorless canonical comes first - maybe not ideal but hey
    assertEquals("2", syns.get(3).getId());
    assertEquals("3", syns.get(4).getId());
    assertEquals("7", syns.get(5).getId());
    assertEquals("6", syns.get(6).getId());
  }

  static Synonym syn(int id, Rank rank, String name, String authorship, Integer year) {
    var sn = new SimpleName(String.valueOf(id), name, authorship, rank);
    sn.setStatus(TaxonomicStatus.SYNONYM);
    var s = new Synonym(sn);
    s.getName().setPublishedInYear(year);
    return s;
  }
}