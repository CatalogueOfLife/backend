package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.api.vocab.Issue;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import static org.junit.Assert.*;

public class VerbatimSourceMapperTest extends MapperTestBase<VerbatimSourceMapper> {

  int datasetKey = testDataRule.testData.key;

  public VerbatimSourceMapperTest() {
    super(VerbatimSourceMapper.class);
  }

  static VerbatimSource create(){
    VerbatimSource v = new VerbatimSource();
    v.setKey(TestEntityGenerator.TAXON1);
    v.setSourceId("source77");
    v.setSourceDatasetKey(77);
    v.addIssues(Issue.AUTHORSHIP_CONTAINS_TAXONOMIC_NOTE, Issue.BASIONYM_DERIVED);
    return v;
  }

  @Test
  public void roundtrip() {
    VerbatimSource v1 = create();
    mapper().create(v1);

    commit();

    VerbatimSource v2 = mapper().get(v1);

    assertEquals(v1, v2);
  }

  @Test
  public void issues() {
    VerbatimSource v1 = create();
    mapper().create(v1);

    var iss = mapper().getIssues(v1).getIssues();
    assertEquals(iss, Set.copyOf(v1.getIssues()));

    iss = Set.of(Issue.INCONSISTENT_NAME, Issue.BASIONYM_DERIVED, Issue.WRONG_MONOMIAL_CASE);
    mapper().updateIssues(v1, iss);
    assertEquals(iss, mapper().getIssues(v1).getIssues());
  }

  @Test
  public void addIssue() {
    VerbatimSource v1 = create();
    mapper().create(v1);

    var issues = new HashSet<>(v1.getIssues());
    var iss = mapper().getIssues(v1).getIssues();
    assertEquals(iss, issues);

    mapper().addIssue(v1, Issue.BASIONYM_DERIVED);
    issues.add(Issue.BASIONYM_DERIVED);

    iss = mapper().getIssues(v1).getIssues();
    assertEquals(iss, issues);

    // now without a pre-existing record
    DSID<String> key = DSID.of(datasetKey, "s2");
    mapper().addIssue(key, Issue.BASIONYM_DERIVED);
    iss = mapper().getIssues(key).getIssues();
    assertEquals(iss, Set.of(Issue.BASIONYM_DERIVED));
  }

  @Test
  public void delete() {
    int datasetKey = TestEntityGenerator.TAXON1.getDatasetKey();
    Taxon t = new Taxon(TestEntityGenerator.TAXON1);

    VerbatimSource v1 = new VerbatimSource();
    v1.setKey(t);
    v1.setSourceId("source77");
    v1.setSourceDatasetKey(77);
    mapper().create(v1);

    commit();
    assertNotNull(mapper().get(v1));

    // non existing sector
    mapper().deleteBySector(DSID.of(datasetKey, 1));
    assertNotNull(mapper().get(v1));

    Sector s = new Sector();
    s.setDatasetKey(datasetKey);
    s.setSubjectDatasetKey(datasetKey);
    s.setTarget(SimpleNameLink.of(t));
    s.setMode(Sector.Mode.ATTACH);
    TestEntityGenerator.setUser(s);
    mapper(SectorMapper.class).create(s);

    t.setSectorKey(s.getId());
    mapper(TaxonMapper.class).update(t);
    assertNotNull(mapper().get(v1));

    mapper().deleteBySector(s);
    assertNull(mapper().get(v1));

    mapper().delete(v1);
    assertNull(mapper().get(v1));
  }

  @Test
  public void copyDataset() throws Exception {
    CopyDatasetTestComponent.copy(mapper(), datasetKey, true);
  }


  @Test
  public void secondarySources() {
    int datasetKey = TestEntityGenerator.TAXON1.getDatasetKey();
    Taxon t = new Taxon(TestEntityGenerator.TAXON1);

    VerbatimSource v1 = create();
    mapper().create(v1);
    commit();

    // non existing vsource
    mapper().deleteSources(DSID.of(datasetKey, "notThere"));
    assertNotNull(mapper().get(v1));

    // get
    var srcs = mapper().getSources(v1);
    var v2 = mapper().get(v1);
    assertEquals(Collections.EMPTY_MAP, v2.getSecondarySources());
    assertEquals(v2, v1);

    // add sources
    var groups = Set.of(InfoGroup.NAME, InfoGroup.PUBLISHED_IN, InfoGroup.AUTHORSHIP);
    final var srcKey = DSID.of(34, "dtfgzhn");
    mapper().insertSources(v1, srcKey, groups);

    srcs = mapper().getSources(v1);
    var k = srcs.keySet();
    assertEquals(3, k.size());
    var v = srcs.values();
    assertEquals(3, v.size());
    assertEquals(VerbatimSourceMapper.SecondarySource.class, v.iterator().next().getClass());
    assertTrue(DSID.equals(srcKey, srcs.get(InfoGroup.NAME)));
    assertTrue(DSID.equals(srcKey, srcs.get(InfoGroup.PUBLISHED_IN)));
    assertTrue(DSID.equals(srcKey, srcs.get(InfoGroup.AUTHORSHIP)));

    v2 = mapper().getWithSources(v1);
    assertNotNull(v2.getSecondarySources());
    assertEquals(groups, v2.getSecondarySources().keySet());
  }
}