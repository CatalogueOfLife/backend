package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Issue;

import org.gbif.nameparser.api.Rank;

import java.beans.Introspector;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.junit.Test;

import static org.junit.Assert.*;

public class SectorSettingsResolverTest {

  static Sector sector(Sector.Mode mode) {
    var s = new Sector();
    s.setDatasetKey(3);
    s.setId(1);
    s.setMode(mode);
    return s;
  }

  static SectorProfile profile(int id, Consumer<SectorSettings> settings) {
    var p = new SectorProfile();
    p.setDatasetKey(3);
    p.setId(id);
    p.setTitle("p" + id);
    p.setPosition(id);
    settings.accept(p.getSettings());
    return p;
  }

  @Test
  public void builtInDefaultsPerMode() {
    var merge = SectorSettingsResolver.resolve(sector(Sector.Mode.MERGE), List.of());
    assertEquals(SectorSettingsResolver.MERGE_RANKS_DEFAULT, merge.getSettings().getRanks());
    assertEquals(EnumSet.allOf(EntityType.class), merge.getSettings().getEntities());
    assertEquals(Boolean.TRUE, merge.getSettings().getCreateImplicitNames());
    assertEquals(Boolean.FALSE, merge.getSettings().getCopyAccordingTo());
    assertEquals(Boolean.FALSE, merge.getSettings().getRemoveOrdinals());
    assertEquals(Sector.AuthorshipUpdate.NONE, merge.getSettings().getAuthorshipUpdate());
    assertNull(merge.getSettings().getNameFilter());
    assertNull(merge.getSettings().getCode());
    assertTrue(merge.getSettings().getIssueExclusion().isEmpty());
    assertEquals(SectorSettingsResolver.DEFAULT, merge.getSources().get("ranks"));
    assertEquals(SectorSettingsResolver.DEFAULT, merge.getSources().get("nameFilter"));

    var attach = SectorSettingsResolver.resolve(sector(Sector.Mode.ATTACH), List.of());
    assertEquals(EnumSet.allOf(Rank.class), attach.getSettings().getRanks());
  }

  @Test
  public void nearestLevelWins() {
    var p1 = profile(1, s -> s.setRanks(EnumSet.of(Rank.GENUS)));
    var p2 = profile(2, s -> s.setRanks(EnumSet.of(Rank.SPECIES)));
    var sec = sector(Sector.Mode.MERGE);

    var eff = SectorSettingsResolver.resolve(sec, List.of(p1, p2));
    assertEquals(EnumSet.of(Rank.SPECIES), eff.getSettings().getRanks());
    assertEquals("profile:2", eff.getSources().get("ranks"));

    sec.setRanks(EnumSet.of(Rank.FAMILY));
    eff = SectorSettingsResolver.resolve(sec, List.of(p1, p2));
    assertEquals(EnumSet.of(Rank.FAMILY), eff.getSettings().getRanks());
    assertEquals(SectorSettingsResolver.SECTOR, eff.getSources().get("ranks"));
  }

  @Test
  public void emptySetInherits() {
    var p = profile(1, s -> s.setEntities(EnumSet.of(EntityType.VERNACULAR)));
    var sec = sector(Sector.Mode.MERGE);
    sec.setEntities(EnumSet.noneOf(EntityType.class));
    var eff = SectorSettingsResolver.resolve(sec, List.of(p));
    assertEquals(EnumSet.of(EntityType.VERNACULAR), eff.getSettings().getEntities());
  }

  @Test
  public void explicitFalseOverrides() {
    var p = profile(1, s -> s.setCreateImplicitNames(true));
    var sec = sector(Sector.Mode.ATTACH);
    sec.setCreateImplicitNames(false);
    assertEquals(Boolean.FALSE, SectorSettingsResolver.resolve(sec, List.of(p)).getSettings().getCreateImplicitNames());
  }

  @Test
  public void cascadeFieldByField() {
    var p1 = profile(1, s -> {
      s.setRanks(EnumSet.of(Rank.GENUS));
      s.setCopyAccordingTo(true);
    });
    var p2 = profile(2, s -> s.setCopyAccordingTo(false));
    var eff = SectorSettingsResolver.resolve(sector(Sector.Mode.MERGE), List.of(p1, p2));
    assertEquals(EnumSet.of(Rank.GENUS), eff.getSettings().getRanks());
    assertEquals("profile:1", eff.getSources().get("ranks"));
    assertEquals(Boolean.FALSE, eff.getSettings().getCopyAccordingTo());
    assertEquals("profile:2", eff.getSources().get("copyAccordingTo"));
  }

  @Test
  public void blocklistsAddUp() {
    var p1 = profile(1, s -> {
      s.setIssueExclusion(EnumSet.of(Issue.DOUBTFUL_NAME));
      s.setBlockedNames(Set.of("Aus"));
    });
    var p2 = profile(2, s -> s.setBlockedNames(Set.of("Bus")));
    var sec = sector(Sector.Mode.MERGE);
    sec.setIssueExclusion(EnumSet.of(Issue.UNPARSABLE_NAME));

    var eff = SectorSettingsResolver.resolve(sec, List.of(p1, p2));
    assertEquals(Set.of(Issue.DOUBTFUL_NAME, Issue.UNPARSABLE_NAME), eff.getSettings().getIssueExclusion());
    assertEquals("profile:1,sector", eff.getSources().get("issueExclusion"));
    assertEquals(Set.of("Aus", "Bus"), eff.getSettings().getBlockedNames());
    assertEquals("profile:1,profile:2", eff.getSources().get("blockedNames"));
  }

  @Test
  public void resultDoesNotShareSetsWithProfiles() {
    var p = profile(1, s -> s.setRanks(EnumSet.of(Rank.GENUS)));
    var eff = SectorSettingsResolver.resolve(sector(Sector.Mode.MERGE), List.of(p));
    eff.getSettings().getRanks().add(Rank.SPECIES);
    assertEquals(EnumSet.of(Rank.GENUS), p.getSettings().getRanks());
  }

  /**
   * A setting added to SyncSettings but forgotten in the resolver would silently never be inherited.
   */
  @Test
  public void resolverCoversEverySetting() {
    Set<String> props = Arrays.stream(SyncSettings.class.getMethods())
      .filter(m -> m.getName().startsWith("get") && m.getParameterCount() == 0)
      .map(m -> Introspector.decapitalize(m.getName().substring(3)))
      .collect(Collectors.toSet());
    assertEquals(props, SectorSettingsResolver.fieldNames());
  }
}
