package life.catalogue.db.mapper;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.search.ReferenceSearchRequest;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Issue;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.time.temporal.TemporalUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import com.google.common.collect.Sets;

import static life.catalogue.api.TestEntityGenerator.*;
import static org.junit.Assert.*;

/**
 *
 */
public class ReferenceMapperTest extends CRUDDatasetScopedStringTestBase<Reference, ReferenceMapper> {
  
  public ReferenceMapperTest() {
    super(ReferenceMapper.class);
  }
  
  @Override
  Reference createTestEntity(int dkey) {
    Reference r = newReference();
    r.setDatasetKey(dkey);
    return r;
  }
  
  @Override
  Reference removeDbCreatedProps(Reference obj) {
    return TestEntityGenerator.nullifyUserDate(obj);
  }
  
  @Override
  void updateTestObj(Reference obj) {
    obj.setYear(983);
    obj.setPage("p.19735");
  }

  @Test
  public void copyDataset() throws Exception {
    CopyDatasetTestComponent.copy(mapper(), testDataRule.testData.key, true);
  }

  @Test
  public void sectorProcessable() throws Exception {
    SectorProcessableTestComponent.test(mapper(), DSID.of(testDataRule.testData.key, 1));
  }

  @Test
  public void count2() throws Exception {
    // we start with 3 records in reference table, inserted through
    // apple, only two of which belong to DATASET11.
    mapper().create(newReference());
    mapper().create(newReference());
    mapper().create(newReference());
    generateDatasetImport(DATASET11.getKey());
    commit();
    
    assertEquals(6, mapper().count(DATASET11.getKey()));
  }

  @Test
  public void deleteOrphans() throws Exception {
    LocalDateTime before = LocalDateTime.of(2019,3,21,21,8);
    Reference r = newReference();
    mapper().create(r);
    commit();

    assertNotNull(mapper().get(r));
    mapper().deleteOrphans(r.getDatasetKey(), before);
    commit();

    assertNotNull(mapper().get(r));
    mapper().deleteOrphans(r.getDatasetKey(), null);
    assertNull(mapper().get(r));
  }

  @Test
  public void listOrphans() throws Exception {
    LocalDateTime bFirst = LocalDateTime.now().minus(1, ChronoUnit.MILLIS);

    Reference r = newReference();
    mapper().create(r);
    mapper().create(newReference());
    commit();

    List<Reference> dels = mapper().listOrphans(r.getDatasetKey(), bFirst, new Page());
    assertEquals(1, dels.size());

    dels = mapper().listOrphans(r.getDatasetKey(), null, new Page());
    assertEquals(3, dels.size());
  }

  @Test
  public void list2() throws Exception {
    List<Reference> in = new ArrayList<>();
    in.add(newReference());
    in.add(newReference());
    in.add(newReference());
    in.add(newReference());
    in.add(newReference());
    for (Reference r : in) {
      mapper().create(r);
    }
    commit();
    // list is sorted by id. From apple we get 3 records for dataset 11 that sort last:
    //r10001
    //r10002
    //r10003
    //r10004
    //r10005
    //ref-1
    //ref-1b
    //ref-2
    in.add(new Reference(REF1));
    in.add(new Reference(REF1b));
    in.add(new Reference(REF2));

    List<Reference> out = mapper().list(DATASET11.getKey(), new Page());
    assertEquals(8, out.size());

    TestEntityGenerator.nullifyDate(in);
    TestEntityGenerator.nullifyDate(out);
    assertEquals(in.get(0), out.get(0));
    assertEquals(in.get(1), out.get(1));
    assertEquals(in.get(2), out.get(2));
    assertEquals(in.get(3), out.get(3));
    assertEquals(in.get(4), out.get(4));
    assertEquals(in.get(5), out.get(5));
    assertEquals(in.get(6), out.get(6));
    assertEquals(in.get(7), out.get(7));
    assertEquals(in, out);

    // test draft, the sql is different
    out = mapper().list(Datasets.COL, new Page());
    int cnt = mapper().count(Datasets.COL);
    cnt = mapper().count(DATASET11.getKey());

  }
  
  @Test
  public void search() throws Exception {
    VerbatimRecordMapper vm = mapper(VerbatimRecordMapper.class);
    VerbatimRecord v = new VerbatimRecord();
    v.setDatasetKey(Datasets.COL);
    v.setIssues(Set.of(Issue.INCONSISTENT_NAME, Issue.UNMATCHED_REFERENCE_BRACKETS));
    vm.create(v);

    List<Reference> in = new ArrayList<>();
    in.add(newReference("My diverse backyard baby", "Markus","Döring"));
    in.add(newReference("On the road", "Jack","Kerouac"));
    in.add(newReference("Mammal Species of the World. A Taxonomic and Geographic Reference (3rd ed)",  "Don E.","Wilson",  "DeeAnn M.","Reeder"));
    for (Reference r : in) {
      r.setDatasetKey(Datasets.COL);
      r.setSectorKey(null);
      r.setVerbatimKey(v.getId());
      mapper().create(r);
    }
    commit();
    final String r1 = in.get(0).getId();
    final String r2 = in.get(1).getId();
    final String r3 = in.get(2).getId();
  
    ReferenceSearchRequest req = ReferenceSearchRequest.byQuery("backyard");
    List<Reference> out = mapper().search(Datasets.COL, req, new Page());
    assertEquals(1, out.size());
    assertEquals(r1, out.get(0).getId());
  
    req = ReferenceSearchRequest.byQuery("Kerouac");
    out = mapper().search(Datasets.COL, req, new Page());
    assertEquals(1, out.size());
    assertEquals(r2, out.get(0).getId());

    req.setIssues(List.of(Issue.REFTYPE_INVALID, Issue.DUPLICATE_NAME));
    out = mapper().search(Datasets.COL, req, new Page());
    assertEquals(0, out.size());

    req.setIssues(List.of(Issue.UNMATCHED_REFERENCE_BRACKETS));
    out = mapper().search(Datasets.COL, req, new Page());
    assertEquals(1, out.size());

    req.setQ(null);
    out = mapper().search(Datasets.COL, req, new Page());
    assertEquals(3, out.size());
  }

  @Test
  public void listByIds() {
    assertEquals(2, mapper().listByIds(11, Sets.newHashSet("ref-1", "ref-1b")).size());
    assertEquals(1, mapper().listByIds(11, Sets.newHashSet("ref-1", "ref-12")).size());
    assertEquals(1, mapper().listByIds(11, Sets.newHashSet("ref-1b")).size());
    assertEquals(0, mapper().listByIds(12, Sets.newHashSet("ref-1b")).size());
    assertEquals(0, mapper().listByIds(12, Sets.newHashSet("ref-2")).size());
  }
  
}