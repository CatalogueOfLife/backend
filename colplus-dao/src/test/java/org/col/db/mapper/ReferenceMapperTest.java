package org.col.db.mapper;

import java.util.ArrayList;
import java.util.List;

import com.google.common.collect.Sets;
import org.col.api.RandomUtils;
import org.col.api.TestEntityGenerator;
import org.col.api.model.CslData;
import org.col.api.model.Page;
import org.col.api.model.Reference;
import org.col.api.search.ReferenceSearchRequest;
import org.col.api.vocab.Datasets;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.*;
import static org.junit.Assert.assertEquals;

/**
 *
 */
public class ReferenceMapperTest extends MapperTestBase<ReferenceMapper> {
  
  public ReferenceMapperTest() {
    super(ReferenceMapper.class);
  }
  
  @Test
  public void roundtrip() throws Exception {
    Reference r1 = create();
    mapper().create(r1);
    commit();
    Reference r2 = mapper().get(r1.getDatasetKey(), r1.getId());
    
    TestEntityGenerator.nullifyUserDate(r1);
    TestEntityGenerator.nullifyUserDate(r2);
  
    assertEquals(r1, r2);
  }
  
  @Test
  public void count() throws Exception {
    // we start with 3 records in reference table, inserted through
    // apple, only two of which belong to DATASET11.
    mapper().create(create());
    mapper().create(create());
    mapper().create(create());
    generateDatasetImport(DATASET11.getKey());
    commit();
    
    assertEquals(6, mapper().count(DATASET11.getKey()));
  }
  
  @Test
  public void list() throws Exception {
    List<Reference> in = new ArrayList<>();
    in.add(create());
    in.add(create());
    in.add(create());
    in.add(create());
    in.add(create());
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

  }

  @Test
  public void listByIds() {
    assertEquals(2, mapper().listByIds(11, Sets.newHashSet("ref-1", "ref-1b")).size());
    assertEquals(1, mapper().listByIds(11, Sets.newHashSet("ref-1", "ref-12")).size());
    assertEquals(1, mapper().listByIds(11, Sets.newHashSet("ref-1b")).size());
    assertEquals(0, mapper().listByIds(12, Sets.newHashSet("ref-1b")).size());
    assertEquals(0, mapper().listByIds(12, Sets.newHashSet("ref-2")).size());
  }
  
  private static Reference create() throws Exception {
    return newReference();
  }
  
  private static CslData createCsl() {
    CslData item = new CslData();
    item.setTitle(RandomUtils.randomLatinString(80));
    item.setContainerTitle(RandomUtils.randomLatinString(100));
    item.setPublisher("Springer");
    item.setYearSuffix("1988b");
    item.setDOI("doi:10.1234/" + RandomUtils.randomLatinString(20));
    return item;
  }
  
}