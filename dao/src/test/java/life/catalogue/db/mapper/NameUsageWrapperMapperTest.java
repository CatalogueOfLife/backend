package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.*;
import life.catalogue.api.search.NameUsageWrapper;
import life.catalogue.api.search.SimpleDecision;
import life.catalogue.api.vocab.*;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import life.catalogue.coldp.ColdpTerm;

import org.apache.ibatis.cursor.Cursor;
import org.junit.Assert;
import org.junit.Test;

import static life.catalogue.api.TestEntityGenerator.*;
import static org.junit.Assert.*;


public class NameUsageWrapperMapperTest extends MapperTestBase<NameUsageWrapperMapper> {
  
  public NameUsageWrapperMapperTest() {
    super(NameUsageWrapperMapper.class);
  }
  
  private AtomicInteger counter = new AtomicInteger(0);
  
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

  @Test
  public void testGetComplete() throws Exception {
    Taxon tax2 = new Taxon(TAXON2);
    DatasetMapper dm = mapper(DatasetMapper.class);
    Dataset d = dm.get(Datasets.COL);
    d.setGbifPublisherKey(UUID.randomUUID());
    dm.update(d);

    Taxon t = TestEntityGenerator.newTaxon(d.getKey());
    t.getName().setAuthorship("Miller");

    NamesIndexMapper nim = mapper(NamesIndexMapper.class);
    IndexName inc = new IndexName();
    inc.setScientificName(t.getName().getScientificName());
    inc.setRank(t.getName().getRank());
    nim.create(inc);

    IndexName in = new IndexName(inc);
    in.setAuthorship(t.getName().getAuthorship());
    in.setCanonicalId(inc.getKey());
    nim.create(in);

    NameMapper nm = mapper(NameMapper.class);
    nm.create(t.getName());

    NameMatchMapper nmm = mapper(NameMatchMapper.class);
    nmm.create(t.getName(), t.getSectorKey(), in.getKey(), MatchType.EXACT);

    TaxonMapper tm = mapper(TaxonMapper.class);
    tm.create(t);

    SectorMapper sm = mapper(SectorMapper.class);
    Sector s = new Sector();
    s.setSubjectDatasetKey(tax2.getDatasetKey());
    s.setSubject(tax2.toSimpleNameLink());
    s.setDatasetKey(d.getKey());
    s.setTarget(t.toSimpleNameLink());
    s.applyUser(TestEntityGenerator.USER_USER);
    sm.create(s);

    t.setSectorKey(s.getId());
    tm.update(t);

    commit();

    NameUsageWrapper w = mapper().get(Datasets.COL, true, t.getId());
    assertNotNull(w);
    assertNotNull(w.getUsage());
    Taxon wt = (Taxon) w.getUsage();
    assertNotNull(wt.getName());
    assertEquals(s.getId(), wt.getSectorKey());

    // sector props and main publisher key is not set!
    assertNull(w.getSectorDatasetKey());
    assertNull(w.getUsage().getSectorMode());
    assertNull(w.getPublisherKey());

    //
    // try with sector source record after adding a decision and some issues
    DecisionMapper dem = mapper(DecisionMapper.class);
    EditorialDecision ed = new EditorialDecision();
    ed.setDatasetKey(d.getKey());
    ed.setMode(EditorialDecision.Mode.BLOCK);
    ed.setSubjectDatasetKey(tax2.getDatasetKey());
    ed.setSubject(tax2.toSimpleNameLink());
    ed.applyUser(TestEntityGenerator.USER_USER);
    dem.create(ed);

    // setup verbatim records - requires a sequence in pg which we normally only create during imports
    var dpm = mapper(DatasetPartitionMapper.class);
    dpm.createSequences(tax2.getDatasetKey());

    var vm = mapper(VerbatimRecordMapper.class);
    var vn = new VerbatimRecord();
    vn.setDatasetKey(tax2.getDatasetKey());
    vn.setType(ColdpTerm.Name);
    vn.setIssues(Set.of(Issue.INCONSISTENT_NAME, Issue.UNPARSABLE_NAME));
    vm.create(vn);

    var n1 = nm.get(tax2.getName());
    n1.setVerbatimKey(vn.getId());
    nm.update(n1);

    var vu = new VerbatimRecord();
    vu.setDatasetKey(tax2.getDatasetKey());
    vu.setType(ColdpTerm.Taxon);
    vu.setIssues(Set.of(Issue.PARTIAL_DATE));
    vm.create(vu);

    var t1 = tm.get(tax2);
    t1.setVerbatimKey(vu.getId());
    tm.update(t1);

    // there are also verbatim source records in the test data - remove it now that we have v records
    var vsm = mapper(VerbatimSourceMapper.class);
    vsm.deleteByDataset(tax2.getDatasetKey());
    var vs = vsm.getByUsage(tax2);
    assertNull(vs);

    // add secondary sources
    vs = new VerbatimSource(tax2.getDatasetKey(), null, appleKey, "not-there", EntityType.NAME_USAGE);
    vsm. create(vs);
    vsm.insertSources(vs.getKey(), TAXON1, Set.of(InfoGroup.PUBLISHED_IN, InfoGroup.AUTHORSHIP));
    tax2.setVerbatimSourceKey(vs.getId());
    tm.update(tax2);
    commit();

    // test secondary sources
    w = mapper().get(tax2.getDatasetKey(), true, tax2.getId());
    assertNotNull(w);
    assertNotNull(w.getUsage());
    wt = (Taxon) w.getUsage();
    assertNotNull(wt.getName());
    // compare with taxon via taxon mapper
    var t2 = tm.get(tax2);
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

    // secondary sources
    assertEquals(Set.of(TAXON1.getDatasetKey()), w.getSecondarySourceKeys());
    assertEquals(Set.of(InfoGroup.PUBLISHED_IN, InfoGroup.AUTHORSHIP), w.getSecondarySourceGroups());
  }
}
