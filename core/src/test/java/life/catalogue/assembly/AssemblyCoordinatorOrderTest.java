package life.catalogue.assembly;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SimpleNameLink;

import org.gbif.nameparser.api.Rank;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static life.catalogue.api.model.Sector.Mode;

public class AssemblyCoordinatorOrderTest {

  @Test
  public void compareTo() {
    List<Sector> secs = new ArrayList<>();
    secs.add(sec(Mode.ATTACH, sn(Rank.SPECIES, "Abiès alba", "Mill.")));
    secs.add(sec(Mode.UNION, sn(Rank.SPECIES, "Abiès alba", "Miller")));
    secs.add(sec(null, sn(Rank.GENUS, "Pomela", null)));
    secs.add(sec(Mode.ATTACH, sn(null, "", null)));
    secs.add(sec(Mode.ATTACH, null));
    secs.add(sec(Mode.ATTACH, sn(Rank.SPECIES, "Abieta alba", "")));
    secs.add(sec(Mode.ATTACH, sn(Rank.SUBGENUS, "Pomela", null)));
    secs.add(sec(Mode.UNION, sn(Rank.GENUS, "Pomela", null)));
    secs.add(sec(null, sn(Rank.SPECIES, "Pinus alba", null)));
    secs.add(sec(null, null));
    secs.add(sec(Mode.ATTACH, sn(Rank.GENUS, "Pomela", "Karl")));
    secs.add(sec(Mode.ATTACH, sn(null, "Pomela", null)));
    
    secs.sort(SyncManager.SECTOR_ORDER);
    
    for (Sector sn : secs) {
      System.out.println(sn);
    }
  }
  
  static Sector sec(Mode mode, SimpleNameLink sn) {
    Sector sector = new Sector();
    sector.setMode(mode);
    sector.setTarget(sn);
    sector.setSubject(sn);
    sector.applyUser(TestEntityGenerator.USER_EDITOR);
    return sector;
  }
  
  static SimpleNameLink sn(Rank rank, String name, String author) {
    return SimpleNameLink.of(name, author, rank);
  }
  
}