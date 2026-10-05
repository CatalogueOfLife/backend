package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.DatasetType;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Users;

import org.gbif.nameparser.api.Rank;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class SectorProfileMapperTest extends CRUDPageableTestBase<Integer, SectorProfile, SectorProfileMapper> {
  static final int SUBJECT = TestEntityGenerator.DATASET11.getKey();

  public SectorProfileMapperTest() {
    super(SectorProfileMapper.class);
  }

  @Override
  SectorProfile createTestEntity(int datasetKey) {
    var p = new SectorProfile();
    p.setDatasetKey(datasetKey);
    p.setTitle("Plazi");
    p.setDescription("All treatment bank articles");
    p.setPosition(3);
    p.getSelector().setModes(EnumSet.of(Sector.Mode.MERGE));
    p.getSelector().setDatasetTypes(EnumSet.of(DatasetType.ARTICLE));
    p.getSelector().setPublisherKeys(Set.of(UUID.randomUUID()));
    p.getSelector().setAnySectorPublisher(true);
    p.getSelector().setSubjectDatasetKeys(Set.of(1001, 1002));
    p.getSelector().setSectorKeys(Set.of(7));
    p.getSettings().setRanks(EnumSet.of(Rank.GENUS, Rank.SPECIES));
    p.getSettings().setEntities(EnumSet.of(EntityType.NAME_USAGE));
    p.getSettings().setCreateImplicitNames(false);
    p.getSettings().setIssueExclusion(EnumSet.of(Issue.DOUBTFUL_NAME));
    p.getSettings().setBlockedNamePatterns(Set.of("^Incertae"));
    p.applyUser(Users.TESTER);
    return p;
  }

  @Override
  SectorProfile createTestEntityIncId(int datasetKey) {
    return createTestEntity(datasetKey);
  }

  @Override
  void updateTestObj(SectorProfile p) {
    p.setTitle("Plazi & friends");
    p.getSelector().setModes(EnumSet.noneOf(Sector.Mode.class));
    p.getSettings().setRanks(EnumSet.of(Rank.SPECIES));
  }

  private SectorProfile profile(String title, int position, Consumer<SectorSelector> selector) {
    var p = new SectorProfile();
    p.setDatasetKey(Datasets.COL);
    p.setTitle(title);
    p.setPosition(position);
    selector.accept(p.getSelector());
    p.applyUser(Users.TESTER);
    mapper().create(p);
    return p;
  }

  private static List<String> titles(List<SectorProfile> profiles) {
    return profiles.stream().map(SectorProfile::getTitle).toList();
  }

  private Sector mergeSector(int datasetKey) {
    Sector s = SectorMapperTest.create(DSID.of(datasetKey, UUID.randomUUID().toString()), DSID.of(SUBJECT, UUID.randomUUID().toString()));
    s.setMode(Sector.Mode.MERGE);
    mapper(SectorMapper.class).create(s);
    return s;
  }

  @Test
  public void listMatching() throws Exception {
    final UUID publisher = UUID.randomUUID();
    try (var st = connection().createStatement()) {
      st.execute("UPDATE dataset SET type='ARTICLE', gbif_publisher_key='" + publisher + "' WHERE key=" + SUBJECT);
    }
    Sector s = mergeSector(Datasets.COL);

    profile("all", 5, sel -> {});
    profile("merge", 1, sel -> sel.setModes(EnumSet.of(Sector.Mode.MERGE)));
    profile("attach", 2, sel -> sel.setModes(EnumSet.of(Sector.Mode.ATTACH)));
    profile("article", 3, sel -> sel.setDatasetTypes(EnumSet.of(DatasetType.ARTICLE)));
    profile("taxonomic", 3, sel -> sel.setDatasetTypes(EnumSet.of(DatasetType.TAXONOMIC)));
    profile("publisher", 4, sel -> sel.setPublisherKeys(Set.of(publisher)));
    profile("other publisher", 4, sel -> sel.setPublisherKeys(Set.of(UUID.randomUUID())));
    profile("any sector publisher", 6, sel -> sel.setAnySectorPublisher(true));
    profile("source", 7, sel -> sel.setSubjectDatasetKeys(Set.of(SUBJECT)));
    profile("sector", 8, sel -> sel.setSectorKeys(Set.of(s.getId())));
    profile("merge of another source", 9, sel -> {
      sel.setModes(EnumSet.of(Sector.Mode.MERGE));
      sel.setSubjectDatasetKeys(Set.of(SUBJECT + 1));
    });
    commit();

    // the publisher is no sector publisher of the project yet
    assertEquals(List.of("merge", "article", "publisher", "all", "source", "sector"), titles(mapper().listMatching(s)));

    var sp = new SectorPublisher();
    sp.setId(publisher);
    sp.setDatasetKey(Datasets.COL);
    sp.setAlias("P");
    sp.setTitle("Publisher");
    sp.applyUser(Users.TESTER);
    mapper(SectorPublisherMapper.class).create(sp);
    commit();
    assertEquals(List.of("merge", "article", "publisher", "all", "any sector publisher", "source", "sector"),
      titles(mapper().listMatching(s)));
  }

  @Test
  public void samePositionCascadesById() {
    Sector s = mergeSector(Datasets.COL);
    var first = profile("first", 1, sel -> {});
    var second = profile("second", 1, sel -> {});
    commit();
    assertTrue(first.getId() < second.getId());
    assertEquals(List.of("first", "second"), titles(mapper().listMatching(s)));
  }

  @Test
  public void profilesStayInTheirDataset() {
    profile("COL wide", 0, sel -> {});
    int other = newDataset();
    Sector s = mergeSector(other);
    commit();
    assertTrue(mapper().listMatching(s).isEmpty());
  }

  @Test
  public void unknownSettingsAreIgnored() throws Exception {
    var p = createTestEntity(Datasets.COL);
    mapper().create(p);
    commit();
    try (var st = connection().createStatement()) {
      st.execute("UPDATE sector_profile SET settings = settings || '{\"someFutureSetting\": true}' WHERE id=" + p.getId());
    }
    commit();
    assertEquals(p.getSettings(), mapper().get(p.getKey()).getSettings());
  }

  /**
   * Nulls in stored integer or uuid arrays must not make every read of the profile throw.
   */
  @Test
  public void nullSelectorValuesAreSkipped() {
    var p = createTestEntity(Datasets.COL);
    p.getSelector().setSectorKeys(new java.util.HashSet<>(java.util.Arrays.asList(7, null)));
    p.getSelector().setPublisherKeys(new java.util.HashSet<>(java.util.Arrays.asList(UUID.randomUUID(), null)));
    mapper().create(p);
    commit();
    var p2 = mapper().get(p.getKey());
    assertEquals(Set.of(7), p2.getSelector().getSectorKeys());
    assertEquals(1, p2.getSelector().getPublisherKeys().size());
  }

  @Test
  public void copyDataset() {
    var p = createTestEntity(Datasets.COL);
    mapper().create(p);
    commit();
    int release = newDataset();
    mapper().copyDataset(Datasets.COL, release, false);
    commit();

    var copies = mapper().listAll(release);
    assertEquals(1, copies.size());
    var c = copies.get(0);
    assertEquals(p.getId(), c.getId());
    assertEquals(p.getTitle(), c.getTitle());
    assertEquals(p.getSelector(), c.getSelector());
    assertEquals(p.getSettings(), c.getSettings());
  }
}
