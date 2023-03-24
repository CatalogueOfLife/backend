package life.catalogue.api.model;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

public class SimpleNameTest {
  
  @Test
  public void compareTo() {
    List<SimpleName> names = new ArrayList<>();
    names.add(sn(Rank.SPECIES, "Abiès alba", "Mill."));
    names.add(sn(Rank.SPECIES, "Abiès alba", "Miller"));
    names.add(sn(Rank.SPECIES, "Pinus alba", null));
    names.add(sn(Rank.SPECIES, "Abieta alba", ""));
    names.add(sn(Rank.SUBGENUS, "Pomela", null));
    names.add(sn(Rank.GENUS, "Pomela", null));
    names.add(sn(Rank.GENUS, "Pomela", "Karl"));
    names.add(sn(null, "Pomela", null));
    names.add(sn(null, "", null));
    
    names.sort(SimpleName.NATURAL_ORDER);
    
    for (SimpleName sn : names) {
      System.out.println(sn);
    }
  }

  @Test
  public void to_String() {
    SimpleName sn = new SimpleName("atc", "Cichorieae", Rank.TRIBE);
    Assert.assertEquals("TRIBE Cichorieae [atc]", sn.toString());

    sn.setParent("Asteraceae");
    Assert.assertEquals("TRIBE Cichorieae [atc parent=Asteraceae]", sn.toString());
  }

  static SimpleName sn(Rank rank, String name, String author) {
    return new SimpleName(null, name, author, rank);
  }
}