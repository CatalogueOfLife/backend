package org.col.db.mapper;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

import com.google.common.collect.Lists;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Name;
import org.col.api.model.Page;
import org.col.api.model.Taxon;
import org.col.db.dao.NameDao;
import org.gbif.nameparser.api.Rank;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 *
 */
public class TaxonMapperTest extends MapperTestBase<TaxonMapper> {

  public TaxonMapperTest() {
    super(TaxonMapper.class);
  }

  @Test
  public void roundtrip() throws Exception {
    Taxon in = TestEntityGenerator.newTaxon("t1");
    mapper().create(in);
    assertNotNull(in.getKey());
    commit();
    Taxon out = mapper().get(in.getDatasetKey(), in.getKey());

    Javers javers = JaversBuilder.javers().build();
    Diff diff = javers.compare(in, out);
    System.out.println(diff);
    assertEquals(0, diff.getChanges().size());
    assertEquals(in, out);
  }

  @Test
  public void count() throws Exception {
    int i = mapper().count(TestEntityGenerator.DATASET11.getKey(), false);
    // Just to make sure we understand our environment
    // 2 Taxa pre-inserted through InitMybatisRule.apple()
    assertEquals(2, i);
    mapper().create(TestEntityGenerator.newTaxon("t2"));
    mapper().create(TestEntityGenerator.newTaxon("t3"));
    mapper().create(TestEntityGenerator.newTaxon("t4"));
    assertEquals(5, mapper().count(TestEntityGenerator.DATASET11.getKey(), false));
  }

  @Test
  public void list() throws Exception {
    List<Taxon> taxa = new ArrayList<>();
    taxa.add(TestEntityGenerator.newTaxon("t1"));
    taxa.add(TestEntityGenerator.newTaxon("t2"));
    taxa.add(TestEntityGenerator.newTaxon("t3"));
    taxa.add(TestEntityGenerator.newTaxon("t4"));
    taxa.add(TestEntityGenerator.newTaxon("t5"));
    taxa.add(TestEntityGenerator.newTaxon("t6"));
    taxa.add(TestEntityGenerator.newTaxon("t7"));
    taxa.add(TestEntityGenerator.newTaxon("t8"));
    taxa.add(TestEntityGenerator.newTaxon("t9"));
    for (Taxon t : taxa) {
      mapper().create(t);
    }
    commit();

    // get first page
    Page p = new Page(0, 3);

    List<Taxon> res = mapper().list(TestEntityGenerator.DATASET11.getKey(), false, p);
    assertEquals(3, res.size());
    // First 2 taxa in dataset D1 are pre-inserted taxa:
    assertTrue(TestEntityGenerator.TAXON1.getKey().equals(res.get(0).getKey()));
    assertTrue(TestEntityGenerator.TAXON2.getKey().equals(res.get(1).getKey()));;
    assertTrue(taxa.get(0).getKey().equals(res.get(2).getKey()));

    p.next();
    res = mapper().list(TestEntityGenerator.DATASET11.getKey(), false, p);
    assertEquals(3, res.size());
    assertTrue(taxa.get(1).getKey().equals(res.get(0).getKey()));
    assertTrue(taxa.get(2).getKey().equals(res.get(1).getKey()));
    assertTrue(taxa.get(3).getKey().equals(res.get(2).getKey()));

  }

  @Test
  public void list3() throws Exception {
    List<Taxon> taxa = new ArrayList<>();
    taxa.add(TestEntityGenerator.newTaxon("t1"));
    taxa.add(TestEntityGenerator.newTaxon("t2"));
    taxa.add(TestEntityGenerator.newTaxon("t3"));
    taxa.add(TestEntityGenerator.newTaxon("t4"));
    taxa.add(TestEntityGenerator.newTaxon("t5"));
    taxa.add(TestEntityGenerator.newTaxon("t6"));
    taxa.add(TestEntityGenerator.newTaxon("t7"));
    taxa.add(TestEntityGenerator.newTaxon("t8"));
    taxa.add(TestEntityGenerator.newTaxon("t9"));
    for (Taxon t : taxa) {
      mapper().create(t);
    }
    commit();

    // get first page
    Page p = new Page(0, 1000);

    List<Taxon> res = mapper().list(TestEntityGenerator.DATASET11.getKey(), true, p);
    // Only the 2 pre-inserted root taxa.
    assertEquals(2, res.size());
  }

  @Test
  public void countChildren() throws Exception {
    Taxon parent = TestEntityGenerator.newTaxon("parent-1");
    mapper().create(parent);

    Taxon c1 = TestEntityGenerator.newTaxon("child-1");
    c1.setParentKey(parent.getKey());
    mapper().create(c1);

    Taxon c2 = TestEntityGenerator.newTaxon("child-2");
    c2.setParentKey(parent.getKey());
    mapper().create(c2);

    Taxon c3 = TestEntityGenerator.newTaxon("child-3");
    c3.setParentKey(parent.getKey());
    mapper().create(c3);

    commit();

    int res = mapper().countChildren(parent.getDatasetKey(), parent.getKey());
    assertEquals("01", 3, res);
  }

  @Test
  public void children() throws Exception {
    Taxon parent = TestEntityGenerator.newTaxon("parent-1");
    mapper().create(parent);

    NameDao nameDao = new NameDao(initMybatisRule.getSqlSession());

    Name n1 = TestEntityGenerator.newName("XXX");
    n1.setScientificName("XXX");
    n1.setRank(Rank.SUBGENUS);
    nameDao.create(n1);

    Taxon c1 = TestEntityGenerator.newTaxon("child-1");
    c1.setName(n1);
    c1.setParentKey(parent.getKey());
    mapper().create(c1);

    Name n2 = TestEntityGenerator.newName("YYY");
    n1.setScientificName("YYY");
    n2.setRank(Rank.FAMILY);
    nameDao.create(n2);

    Taxon c2 = TestEntityGenerator.newTaxon("child-2");
    c2.setName(n2);
    c2.setParentKey(parent.getKey());
    mapper().create(c2);

    Name n3 = TestEntityGenerator.newName("ZZZ");
    n3.setScientificName("ZZZ");
    n3.setRank(Rank.INFRASPECIFIC_NAME);
    nameDao.create(n3);

    Taxon c3 = TestEntityGenerator.newTaxon("child-3");
    c3.setName(n3);
    c3.setParentKey(parent.getKey());
    mapper().create(c3);

    Name n4 = TestEntityGenerator.newName("AAA");
    n4.setScientificName("AAA");
    n4.setRank(Rank.SUBGENUS);
    nameDao.create(n4);

    Taxon c4 = TestEntityGenerator.newTaxon("child-4");
    c4.setName(n4);
    c4.setParentKey(parent.getKey());
    mapper().create(c4);

    commit();

    List<Taxon> res = mapper().children(parent.getDatasetKey(), parent.getKey(), new Page(0, 5));

    assertEquals("01", 4, res.size());
    assertEquals("02", c2.getKey(), res.get(0).getKey()); // Family YYY
    assertEquals("03", c4.getKey(), res.get(1).getKey()); // Subgenus AAA
    assertEquals("04", c1.getKey(), res.get(2).getKey()); // Subgenus XXX
    assertEquals("05", c3.getKey(), res.get(3).getKey()); // Infraspecific ZZZ

  }

  private LinkedList<Taxon> createClassification(Taxon root, String... ids) throws Exception {
    LinkedList<Taxon> taxa = Lists.newLinkedList();
    taxa.add(root);
    Taxon p = root;
    for (String id : ids) {
      p = createChild(p, id);
      taxa.add(p);
    }
    return taxa;
  }

  private Taxon createChild(Taxon parent, String id) throws Exception {
    Taxon t = TestEntityGenerator.newTaxon(id);
    t.setParentKey(parent.getKey());
    mapper().create(t);
    return t;
  }

  @Test
  public void classification() throws Exception {

    Taxon kingdom = TestEntityGenerator.newTaxon("kingdom-01"); // 1
    // Explicitly set to null to override TestEntityGenerator
    kingdom.setParentKey(null);
    mapper().create(kingdom);

    LinkedList<Taxon> parents =
        createClassification(kingdom, "p1", "c1", "o1", "sf1", "f1", "g1", "sg1", "s1");

    commit();

    Taxon sp = parents.removeLast();
    List<Taxon> classification = mapper().classification(sp.getDatasetKey(), sp.getKey());
    assertEquals(parents.size(), classification.size());

    for (Taxon ht : classification) {
      Taxon p = parents.removeLast();
      assertEquals(p.getId(), ht.getId());
    }
  }

}
