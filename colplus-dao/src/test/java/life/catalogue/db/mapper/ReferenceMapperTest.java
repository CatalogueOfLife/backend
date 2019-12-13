package life.catalogue.db.mapper;

import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.Page;
import life.catalogue.api.model.Reference;
import life.catalogue.api.search.ReferenceSearchRequest;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Issue;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static life.catalogue.api.TestEntityGenerator.*;
import static org.junit.Assert.*;

/**
 *
 */
public class ReferenceMapperTest extends CRUDPageableTestBase<Reference, ReferenceMapper> {
  
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
    LocalDateTime bFirst = LocalDateTime.now();
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
    in.add(REF1);
    in.add(REF1b);
    in.add(REF2);
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
  }
  
  @Test
  public void search() throws Exception {
    List<Reference> in = new ArrayList<>();
    in.add(newReference("My diverse backyard baby", "Markus","Döring"));
    in.add(newReference("On the road", "Jack","Kerouac"));
    in.add(newReference("Mammal Species of the World. A Taxonomic and Geographic Reference (3rd ed)",  "Don E.","Wilson",  "DeeAnn M.","Reeder"));
    for (Reference r : in) {
      r.setDatasetKey(Datasets.DRAFT_COL);
      r.setSectorKey(null);
      mapper().create(r);
    }
    commit();
    final String r1 = in.get(0).getId();
    final String r2 = in.get(1).getId();
    final String r3 = in.get(2).getId();
  
    ReferenceSearchRequest req = ReferenceSearchRequest.byQuery("backyard");
    List<Reference> out = mapper().search(Datasets.DRAFT_COL, req, new Page());
    assertEquals(1, out.size());
    assertEquals(r1, out.get(0).getId());
  
    req = ReferenceSearchRequest.byQuery("Kerouac");
    out = mapper().search(Datasets.DRAFT_COL, req, new Page());
    assertEquals(1, out.size());
    assertEquals(r2, out.get(0).getId());
    
    req.setIssues(Lists.newArrayList(Issue.REFTYPE_INVALID, Issue.UNMATCHED_REFERENCE_BRACKETS));
    out = mapper().search(Datasets.DRAFT_COL, req, new Page());
    assertEquals(0, out.size());
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