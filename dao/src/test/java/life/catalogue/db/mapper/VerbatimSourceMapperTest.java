package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.InfoGroup;
import life.catalogue.api.vocab.Issue;
import life.catalogue.junit.TestDataRule;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import org.junit.Test;

import static life.catalogue.api.vocab.Issue.DOI_NOT_FOUND;
import static org.junit.Assert.*;

public class VerbatimSourceMapperTest extends MapperTestBase<VerbatimSourceMapper> {

  int datasetKey = testDataRule.testData.key;

  public VerbatimSourceMapperTest() {
    super(VerbatimSourceMapper.class, TestDataRule.fish());
  }

  VerbatimSource create(){
    VerbatimSource v = new VerbatimSource();
    v.setDatasetKey(datasetKey);
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
    assertEquals(issues, iss);

    // add already existing issue - no change
    mapper().addIssue(v1, Issue.BASIONYM_DERIVED);
    iss = mapper().getIssues(v1).getIssues();
    assertEquals(issues, iss);

    // new issue
    mapper().addIssue(v1, DOI_NOT_FOUND);
    iss = mapper().getIssues(v1).getIssues();
    issues.add(DOI_NOT_FOUND);
    assertEquals(issues, iss);
  }

  @Test
  public void delete() {
    var t = createTaxon();

    Sector s = new Sector();
    s.setDatasetKey(datasetKey);
    s.setSubjectDatasetKey(datasetKey);
    s.setTarget(SimpleNameLink.of(t));
    s.setMode(Sector.Mode.ATTACH);
    TestEntityGenerator.setUser(s);
    mapper(SectorMapper.class).create(s);

    VerbatimSource v1 = new VerbatimSource();
    v1.setDatasetKey(datasetKey);
    v1.setSectorKey(s.getId());
    v1.setSourceId("source77");
    v1.setSourceDatasetKey(77);
    mapper().create(v1);

    commit();
    assertNotNull(mapper().get(v1));

    // non existing sector
    mapper().deleteBySector(DSID.of(datasetKey, 789456));
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

  Taxon createTaxon() {
    Taxon t = TestEntityGenerator.newTaxon(datasetKey);
    mapper(NameMapper.class).create(t.getName());
    mapper(TaxonMapper.class).create(t);
    return t;
  }

  @Test
  public void secondarySources() {
    var t = createTaxon();
    VerbatimSource v1 = create();
    mapper().create(v1);
    commit();

    // non existing vsource
    assertNotNull(mapper().get(v1));
    mapper().deleteSources(DSID.of(datasetKey, 123456789));
    assertNotNull(mapper().get(v1));

    // get
    var srcs = mapper().getSources(v1);
    var v2 = mapper().get(v1);
    assertEquals(Collections.EMPTY_MAP, v2.getSecondarySources());
    assertEquals(v2, v1);

    // add sources
    var groups = Set.of(InfoGroup.PARENT, InfoGroup.PUBLISHED_IN, InfoGroup.AUTHORSHIP);
    final var srcKey = DSID.of(34, "dtfgzhn");
    mapper().insertSources(v1, srcKey, groups);

    srcs = mapper().getSources(v1);
    var k = srcs.keySet();
    assertEquals(3, k.size());
    var v = srcs.values();
    assertEquals(3, v.size());
    assertEquals(SecondarySource.class, v.iterator().next().getClass());
    assertTrue(DSID.equals(srcKey, srcs.get(InfoGroup.PARENT)));
    assertTrue(DSID.equals(srcKey, srcs.get(InfoGroup.PUBLISHED_IN)));
    assertTrue(DSID.equals(srcKey, srcs.get(InfoGroup.AUTHORSHIP)));

    v2 = mapper().getWithSources(v1);
    assertNotNull(v2.getSecondarySources());
    assertEquals(groups, v2.getSecondarySources().keySet());
  }
}