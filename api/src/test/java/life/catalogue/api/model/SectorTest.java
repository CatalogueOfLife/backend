package life.catalogue.api.model;

import life.catalogue.api.jackson.ApiModule;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomStatus;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.util.EnumSet;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

public class SectorTest {

  static Sector full() {
    Sector s = new Sector();
    s.setDatasetKey(3);
    s.setId(17);
    s.setSubjectDatasetKey(1010);
    s.setMode(Sector.Mode.MERGE);
    s.setRanks(EnumSet.of(Rank.GENUS, Rank.SPECIES));
    s.setEntities(EnumSet.of(EntityType.NAME_USAGE, EntityType.VERNACULAR));
    s.setNameTypes(EnumSet.of(NameType.SCIENTIFIC));
    s.setNameStatusExclusion(EnumSet.of(NomStatus.CHRESONYM));
    s.setNameFilter("BOLD:.*");
    s.setExtinctFilter(false);
    s.setCopyAccordingTo(true);
    s.setRemoveOrdinals(true);
    s.setCreateImplicitNames(false);
    s.setCode(NomCode.ZOOLOGICAL);
    s.setAuthorshipUpdate(Sector.AuthorshipUpdate.MISSING);
    s.setIssueExclusion(EnumSet.of(Issue.DOUBTFUL_NAME));
    s.setBlockedNames(Set.of("Aus bus"));
    s.setBlockedNamePatterns(Set.of("^Incertae"));
    return s;
  }

  @Test
  public void copyConstructorKeepsEverySetting() {
    var s = full();
    var copy = new Sector(s);
    assertEquals(s, copy);
    assertEquals(SectorSettings.of(s), SectorSettings.of(copy));
    // a deep copy: changing the copy leaves the original alone
    copy.getRanks().add(Rank.FAMILY);
    assertFalse(s.getRanks().contains(Rank.FAMILY));
  }

  @Test
  public void equalsSeesEverySetting() {
    var a = full();
    var b = full();
    b.setCopyAccordingTo(false);
    assertNotEquals(a, b);
    b = full();
    b.setRemoveOrdinals(null);
    assertNotEquals(a, b);
    b = full();
    b.setBlockedNames(Set.of("Cus dus"));
    assertNotEquals(a, b);
    b = full();
    b.setIssueExclusion(null);
    assertNotEquals(a, b);
  }

  @Test
  public void unsetFlagsStayNull() {
    var s = new Sector();
    assertNull(s.getCopyAccordingTo());
    assertNull(s.getRemoveOrdinals());
    assertNull(s.getCreateImplicitNames());
    assertNull(s.getAuthorshipUpdate());
    var built = Sector.newBuilder().build();
    assertNull(built.getCreateImplicitNames());
    assertNull(built.getAuthorshipUpdate());
  }

  @Test
  public void jsonStaysFlat() throws Exception {
    String json = ApiModule.MAPPER.writeValueAsString(full());
    var tree = ApiModule.MAPPER.readTree(json);
    assertTrue(tree.has("copyAccordingTo"));
    assertTrue(tree.has("blockedNamePatterns"));
    assertFalse(tree.has("settings"));
    assertEquals(full(), ApiModule.MAPPER.readValue(json, Sector.class));
  }

  @Test
  public void settingsJsonIgnoresUnknownProperties() throws Exception {
    var s = ApiModule.MAPPER.readValue("{\"ranks\":[\"genus\"],\"someFutureSetting\":true}", SectorSettings.class);
    assertEquals(EnumSet.of(Rank.GENUS), s.getRanks());
  }
}
