package org.col.db.mapper;

import org.col.api.model.Page;
import org.col.api.model.Taxon;
import org.col.db.TestEntityGenerator;
import org.javers.core.Javers;
import org.javers.core.JaversBuilder;
import org.javers.core.diff.Diff;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

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
    Taxon out = mapper().get(in.getKey());

    Javers javers = JaversBuilder.javers().build();
    Diff diff = javers.compare(in, out);
    assertEquals(0, diff.getChanges().size());
    assertEquals(in, out);
  }

  @Test
  public void count() throws Exception {
    int i = mapper().count(TestEntityGenerator.DATASET1.getKey(), false, null);
    // Just to make sure we understand our environment
    // 2 Taxa pre-inserted through InitMybatisRule.apple()
    assertEquals(2, i);
    mapper().create(TestEntityGenerator.newTaxon("t2"));
    mapper().create(TestEntityGenerator.newTaxon("t3"));
    mapper().create(TestEntityGenerator.newTaxon("t4"));
    assertEquals(5, mapper().count(TestEntityGenerator.DATASET1.getKey(), false, null));
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

    List<Taxon> res = mapper().list(TestEntityGenerator.DATASET1.getKey(), false, null, p);
    assertEquals(3, res.size());
    // First 2 taxa in dataset D1 are pre-inserted taxa:
    assertTrue(TestEntityGenerator.TAXON1.getKey().equals(res.get(0).getKey()));
    assertTrue(TestEntityGenerator.TAXON2.getKey().equals(res.get(1).getKey()));;
    assertTrue(taxa.get(0).getKey().equals(res.get(2).getKey()));

    p.next();
    res = mapper().list(TestEntityGenerator.DATASET1.getKey(), false, null, p);
    assertEquals(3, res.size());
    assertTrue(taxa.get(1).getKey().equals(res.get(0).getKey()));
    assertTrue(taxa.get(2).getKey().equals(res.get(1).getKey()));
    assertTrue(taxa.get(3).getKey().equals(res.get(2).getKey()));

  }

  @Test
  public void list2() throws Exception {
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

    List<Taxon> res = mapper().list(TestEntityGenerator.DATASET1.getKey(), false,
        TestEntityGenerator.NAME1.getKey(), p);
    // 9 plus pre-inserted (root) taxon with this name key
    assertEquals(10, res.size());
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

    List<Taxon> res = mapper().list(TestEntityGenerator.DATASET1.getKey(), true, null, p);
    // Only the 2 pre-inserted root taxa.
    assertEquals(2, res.size());
  }

  @Test
  public void list4() throws Exception {
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

    List<Taxon> res = mapper().list(TestEntityGenerator.DATASET1.getKey(), true,
        TestEntityGenerator.NAME2.getKey(), p);
    // Only 1 pre-inserted root taxon has name NAME2
    assertEquals(1, res.size());
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

    int res = mapper().countChildren(parent.getKey());
    assertEquals("01", 3, res);
  }

  @Test
  public void children() throws Exception {
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

    List<Taxon> res = mapper().children(parent.getKey(), new Page(0, 5));
    assertEquals("01", 3, res.size());
    assertEquals("02", c1, res.get(0));
    assertEquals("03", c2, res.get(1));
    assertEquals("04", c3, res.get(2));

  }

  @Test
  public void classification() throws Exception {

    Taxon kingdom = TestEntityGenerator.newTaxon("kingdom-01"); // 1
    // Explicitly set to null to override TestEntityGenerator
    kingdom.setParentKey(null);
    mapper().create(kingdom);

    Taxon phylum = TestEntityGenerator.newTaxon("phylum-01"); // 2
    phylum.setParentKey(kingdom.getKey());
    mapper().create(phylum);

    Taxon clazz = TestEntityGenerator.newTaxon("class-01"); // 3
    clazz.setParentKey(phylum.getKey());
    mapper().create(clazz);

    Taxon order = TestEntityGenerator.newTaxon("order-01"); // 4
    order.setParentKey(clazz.getKey());
    mapper().create(order);

    Taxon family = TestEntityGenerator.newTaxon("family-01"); // 5
    family.setParentKey(order.getKey());
    mapper().create(family);

    Taxon genus = TestEntityGenerator.newTaxon("genus-01"); // 6
    genus.setParentKey(family.getKey());
    mapper().create(genus);

    Taxon species = TestEntityGenerator.newTaxon("species-01"); // 7
    species.setParentKey(genus.getKey());
    mapper().create(species);

    commit();

    List<Taxon> res = mapper().classification(species.getKey());
    assertEquals("01", 7, res.size());

  }

}
