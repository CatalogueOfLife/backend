package life.catalogue.db.mapper;

import life.catalogue.api.RandomUtils;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SimpleDecision;
import life.catalogue.api.vocab.*;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import life.catalogue.coldp.ColdpTerm;

import life.catalogue.common.text.StringUtils;
import life.catalogue.dao.DatasetInfoCache;

import org.apache.ibatis.cursor.Cursor;

import org.gbif.nameparser.api.Rank;

import org.junit.Assert;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.*;
import static org.junit.Assert.*;


public class NameUsageWrapperMapperTest extends MapperTestBase<NameUsageWrapperMapper> {
  
  public NameUsageWrapperMapperTest() {
    super(NameUsageWrapperMapper.class);
  }
  
  private final AtomicInteger counter = new AtomicInteger(0);

  @Test
  public void processDatasetBareNames() throws Exception {
    Cursor<NameUsageWrapper> c = mapper().processDatasetBareNames(NAME4.getDatasetKey(), null);
    c.forEach(obj -> {
        counter.incrementAndGet();
        assertNotNull(obj);
        assertNotNull(obj.getUsage());
        assertNotNull(obj.getUsage().getName());
    });
    Assert.assertEquals(1, counter.get());
  }

  private Taxon createTaxon(int datasetKey) {
    Taxon tax = TestEntityGenerator.newTaxon(datasetKey);
    tax.setDatasetKey(datasetKey);
    var n = tax.getName();
    n.setRank(Rank.SPECIES);
    n.setScientificName(RandomUtils.randomSpecies());
    n.setAuthorship(RandomUtils.randomAuthor());

    NamesIndexMapper nim = mapper(NamesIndexMapper.class);
    NameMapper nm = mapper(NameMapper.class);
    NameMatchMapper nmm = mapper(NameMatchMapper.class);
    TaxonMapper tm = mapper(TaxonMapper.class);

    IndexName inc = new IndexName();
    inc.setScientificName(n.getScientificName());
    inc.setRank(n.getRank());
    nim.create(inc);

    IndexName in = new IndexName(inc);
    in.setAuthorship(n.getAuthorship());
    in.setCanonicalId(inc.getKey());
    nim.create(in);

    nm.create(n);

    nmm.create(n, tax.getSectorKey(), in.getKey(), MatchType.EXACT);

    tm.create(tax);

    return tax;
  }

  @Test
  public void testGetComplete() throws Exception {
    final int projectKey = Datasets.COL;
    final int sourceDatasetKey = 12;

    // mapper
    DatasetMapper dm = mapper(DatasetMapper.class);
    NameMapper nm = mapper(NameMapper.class);
    TaxonMapper tm = mapper(TaxonMapper.class);
    SectorMapper sm = mapper(SectorMapper.class);
    var vsm = mapper(VerbatimSourceMapper.class);

    // datasets
    Dataset src = dm.get(sourceDatasetKey);
    Dataset proj = dm.get(projectKey);
    assertEquals(DatasetOrigin.PROJECT, proj.getOrigin());
    assertEquals(DatasetOrigin.EXTERNAL, src.getOrigin());
    proj.setGbifPublisherKey(UUID.randomUUID());
    dm.update(proj);

    // external source taxon
    Taxon srcTax = createTaxon(sourceDatasetKey);

    // project taxon
    Taxon projTax = createTaxon(projectKey);

    Sector s = new Sector();
    s.setSubjectDatasetKey(sourceDatasetKey);
    s.setSubject(srcTax.toSimpleNameLink());
    s.setDatasetKey(projectKey);
    s.setTarget(projTax.toSimpleNameLink());
    s.applyUser(TestEntityGenerator.USER_USER);
    sm.create(s);

    projTax.setSectorKey(s.getId());
    tm.update(projTax);

    commit();

    NameUsageWrapper w = mapper().get(projectKey, true, projTax.getId());
    assertNotNull(w);
    assertNotNull(w.getUsage());
    Taxon wt = (Taxon) w.getUsage();
    assertNotNull(wt.getName());
    assertEquals(s.getId(), wt.getSectorKey());
    assertTrue(w.getIssues().isEmpty());

    // sector props and main publisher key is not set!
    assertNull(w.getSectorDatasetKey());
    assertNull(w.getUsage().getSectorMode());
    assertNull(w.getPublisherKey());

    //
    // try with sector source record after adding a decision and some issues
    DecisionMapper dem = mapper(DecisionMapper.class);
    EditorialDecision ed = new EditorialDecision();
    ed.setDatasetKey(projectKey);
    ed.setMode(EditorialDecision.Mode.BLOCK);
    ed.setSubjectDatasetKey(sourceDatasetKey);
    ed.setSubject(srcTax.toSimpleNameLink());
    ed.applyUser(TestEntityGenerator.USER_USER);
    dem.create(ed);

    // setup verbatim records for the source - requires a sequence in pg which we normally only create during imports
    var dpm = mapper(DatasetPartitionMapper.class);
    dpm.createSequences(sourceDatasetKey);

    var vm = mapper(VerbatimRecordMapper.class);
    var vn = new VerbatimRecord();
    vn.setDatasetKey(sourceDatasetKey);
    vn.setType(ColdpTerm.Name);
    vn.setIssues(Set.of(Issue.INCONSISTENT_NAME, Issue.UNPARSABLE_NAME));
    vm.create(vn);

    var n1 = nm.get(srcTax.getName());
    n1.setVerbatimKey(vn.getId());
    nm.update(n1);

    var vu = new VerbatimRecord();
    vu.setDatasetKey(sourceDatasetKey);
    vu.setType(ColdpTerm.Taxon);
    vu.setIssues(Set.of(Issue.PARTIAL_DATE));
    vm.create(vu);

    srcTax.setVerbatimKey(vu.getId());
    tm.update(srcTax);

    // add secondary sources to taxon & name of project
    var vs = new VerbatimSource(projectKey, 10, null, appleKey, "not-there", EntityType.NAME_USAGE);
    vs.setIssues(Set.of(Issue.UNPARSABLE_NAME));
    vsm. create(vs);
    vsm.insertSources(vs.getKey(), TAXON1, Set.of(InfoGroup.PUBLISHED_IN, InfoGroup.AUTHORSHIP));
    projTax.setVerbatimSourceKey(vs.getId());
    tm.update(projTax);

    vs = new VerbatimSource(projectKey, 11, null, appleKey, "not-there", EntityType.NAME_USAGE);
    vs.setIssues(Set.of(Issue.WRONG_MONOMIAL_CASE, Issue.UNPARSABLE_NAME));
    vsm. create(vs);
    projTax.getName().setVerbatimSourceKey(vs.getId());
    nm.update(projTax.getName());

    commit();

    // TEST
    // wrapped src usage
    w = mapper().get(sourceDatasetKey, false, srcTax.getId());
    assertNotNull(w);
    assertNotNull(w.getUsage());
    wt = (Taxon) w.getUsage();
    assertNotNull(wt.getName());
    // compare with taxon via taxon mapper
    var t2 = tm.get(srcTax);
    assertEquals(t2, wt);
    assertNull(w.getUsage().getSectorKey());

    assertEquals(Set.of(Issue.INCONSISTENT_NAME, Issue.UNPARSABLE_NAME, Issue.PARTIAL_DATE), w.getIssues());
    // decision props
    assertEquals(1, w.getDecisions().size());
    assertEquals(new SimpleDecision(ed), w.getDecisions().get(0));

    // sector props and main publisher key is not set!
    assertNull(w.getSectorDatasetKey());
    assertNull(w.getUsage().getSectorMode());
    assertNull(w.getPublisherKey());


    // test wrapped project usage
    w = mapper().get(projectKey, true, projTax.getId());
    assertNotNull(w);
    assertNotNull(w.getUsage());
    wt = (Taxon) w.getUsage();
    assertNotNull(wt.getName());
    // compare with taxon via taxon mapper
    t2 = tm.get(projTax);
    assertEquals(t2, wt);
    assertEquals(s.getId(), w.getUsage().getSectorKey());

    assertEquals(Set.of(Issue.WRONG_MONOMIAL_CASE, Issue.UNPARSABLE_NAME), w.getIssues());

    // project secondary sources
    assertEquals(Set.of(TAXON1.getDatasetKey()), w.getSecondarySourceKeys());
    assertEquals(Set.of(InfoGroup.PUBLISHED_IN, InfoGroup.AUTHORSHIP), w.getSecondarySourceGroups());
  }
}
