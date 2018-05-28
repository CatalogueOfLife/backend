package org.col.db.mapper;

import java.util.List;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.col.api.TestEntityGenerator;
import org.col.api.model.Dataset;
import org.col.api.model.Name;
import org.col.api.model.Page;
import org.col.api.vocab.Origin;
import org.gbif.nameparser.api.NameType;
import org.junit.Before;
import org.junit.Test;

import static org.col.api.TestEntityGenerator.*;
import static org.junit.Assert.*;

/**
 *
 */
public class NameMapperTest extends org.col.db.mapper.MapperTestBase<NameMapper> {

  private static final Splitter SPACE_SPLITTER = Splitter.on(" ").trimResults();
  private NameMapper nameMapper;

  public NameMapperTest() {
    super(NameMapper.class);
  }

  @Before
  public void initMappers() {
    nameMapper = initMybatisRule.getMapper(NameMapper.class);
  }

  private static Name create(final String id, final Name basionym) throws Exception {
    Name n = TestEntityGenerator.newName(id);
    n.setHomotypicNameKey(basionym.getKey());
    return n;
  }

  private static Name create(Dataset d) throws Exception {
    Name n = TestEntityGenerator.newName();
    n.setDatasetKey(d.getKey());
    n.setHomotypicNameKey(NAME1.getKey());
    return n;
  }

  @Test
  public void roundtrip() throws Exception {
    Name n1 = TestEntityGenerator.newName("sk1");
    nameMapper.create(n1);
    assertNotNull(n1.getKey());
    commit();

    int n1Key = nameMapper.lookupKey(n1.getId(), TestEntityGenerator.DATASET1.getKey());
    assertEquals((Integer) n1Key, n1.getKey());

    Name n1b = nameMapper.get(n1Key);
    n1b.setHomotypicNameKey(null);
    assertEquals(n1, n1b);

    // with explicit homotypic group
    Name n2 = TestEntityGenerator.newName("sk2");
    n2.setHomotypicNameKey(n1.getKey());
    nameMapper.create(n2);

    commit();

    // we use a new instance of n1 with just the keys for the equality tests
    // n1 = new Name();
    // n1.setKey(n2.getBasionymKey());
    // n1.setId(n2.getBasionymKey());
    // n2.setBasionymKey(n1);

    int n2Key = nameMapper.lookupKey(n2.getId(), TestEntityGenerator.DATASET1.getKey());
    assertEquals((Integer) n2Key, n2.getKey());
    Name n2b = nameMapper.get(n2Key);
    assertEquals(n2, n2b);
  }

  @Test
  public void list() throws Exception {
    List<Name> names = Lists.newArrayList();
    names.add(create(TestEntityGenerator.DATASET2));
    names.add(create(TestEntityGenerator.DATASET2));
    names.add(create(TestEntityGenerator.DATASET2));
    names.add(create(TestEntityGenerator.DATASET2));
    names.add(create(TestEntityGenerator.DATASET2));

    for (Name n : names) {
      nameMapper.create(n);
    }
    commit();

    // get first page
    Page p = new Page(0, 3);

    List<Name> res = nameMapper.list(TestEntityGenerator.DATASET2.getKey(), p);
    assertEquals(3, res.size());
    assertEquals(Lists.partition(names, 3).get(0), res);

    // next page
    p.next();
    res = nameMapper.list(TestEntityGenerator.DATASET2.getKey(), p);
    assertEquals(2, res.size());
    List<Name> l2 = Lists.partition(names, 3).get(1);
    assertEquals(l2, res);
  }

  @Test
  public void count() throws Exception {
    assertEquals(4, nameMapper.count(TestEntityGenerator.DATASET1.getKey()));

    nameMapper.create(TestEntityGenerator.newName());
    nameMapper.create(TestEntityGenerator.newName());
    commit();
    assertEquals(6, nameMapper.count(TestEntityGenerator.DATASET1.getKey()));
  }

  @Test
  public void basionymGroup() throws Exception {
    Name n2bas = TestEntityGenerator.newName("n2");
    nameMapper.create(n2bas);

    Name n1 = create("n1", n2bas);
    nameMapper.create(n1);

    Name n3 = create("n3", n2bas);
    nameMapper.create(n3);

    Name n4 = create("n4", n2bas);
    nameMapper.create(n4);

    commit();

    List<Name> s1 = mapper().homotypicGroup(n1.getKey());
    assertEquals(4, s1.size());

    List<Name> s2 = mapper().homotypicGroup(n2bas.getKey());
    assertEquals(4, s2.size());
    assertEquals(s1, s2);

    List<Name> s3 = mapper().homotypicGroup(n3.getKey());
    assertEquals(4, s3.size());
    assertEquals(s1, s3);

    List<Name> s4 = mapper().homotypicGroup(n4.getKey());
    assertEquals(4, s4.size());
    assertEquals(s1, s4);
  }

  /*
   * Checks difference in behaviour between providing non-existing key
   * and providing existing key but without synonyms.
   * yields null (issue #55)
   */
  @Test
  public void basionymGroup2() throws Exception {
    Name n = TestEntityGenerator.newName("nxx");
    mapper().create(n);
    List<Name> s = mapper().homotypicGroup(n.getKey());
    assertNotNull(s);
    s = mapper().homotypicGroup(-1);
    assertNotNull(s);
    assertEquals(0, s.size());
  }

  @Test
  public void listByReference() throws Exception {
    Name acc1 = newAcceptedName("Nom uno");
    nameMapper.create(acc1);
    assertTrue(nameMapper.listByReference(REF2.getKey()).isEmpty());

    Name acc2 = newAcceptedName("Nom duo");
    acc2.setPublishedInKey(REF2.getKey());
    Name acc3 = newAcceptedName("Nom tres");
    acc3.setPublishedInKey(REF2.getKey());
    nameMapper.create(acc2);
    nameMapper.create(acc3);

    // we have one ref from the apple.sql
    assertEquals(1, nameMapper.listByReference(REF1.getKey()).size());
    assertEquals(2, nameMapper.listByReference(REF2.getKey()).size());
  }

  private static Name newAcceptedName(String scientificName) {
    Name name = new Name();
    name.setDatasetKey(TestEntityGenerator.DATASET1.getKey());
    name.setId(scientificName.toLowerCase().replace(' ', '-'));
    name.setScientificName(scientificName);
    List<String> tokens = SPACE_SPLITTER.splitToList(scientificName);
    name.setGenus(tokens.get(0));
    name.setSpecificEpithet(tokens.get(1));
    name.setOrigin(Origin.SOURCE);
    name.setType(NameType.SCIENTIFIC);
    return name;
  }

}
