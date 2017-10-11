package org.col.db.mapper;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import org.col.api.Dataset;
import org.col.api.Name;
import org.col.api.Page;
import org.col.api.vocab.NamePart;
import org.col.api.vocab.NameType;
import org.col.api.vocab.Origin;
import org.col.api.vocab.Rank;
import org.col.dao.DaoTestUtil;
import org.gbif.utils.text.StringUtils;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

/**
 *
 */
public class NameMapperTest extends MapperTestBase<NameMapper> {
  Splitter SPACE_SPLITTER = Splitter.on(" ").trimResults();

  public NameMapperTest() {
    super(NameMapper.class);
  }

  private Name create(String id, Name basionym) throws Exception {
    Name n = create(id);
    n.setOriginalName(basionym);
    return n;
  }

  private Name create(Dataset d) throws Exception {
    Name n = create();
    n.setDataset(d);
    return n;
  }

  private Name create(String id) throws Exception {
    Name n = create();
    n.setId(id);
    return n;
  }

  private Name create() throws Exception {
    Name n = new Name();
    n.setDataset(DaoTestUtil.DATASET1);
    n.setScientificName(StringUtils.randomSpecies());
    n.setAuthorship(StringUtils.randomAuthor());
    List<String> tokens = SPACE_SPLITTER.splitToList(n.getScientificName());
    n.setGenus(tokens.get(0));
    n.setSpecificEpithet(tokens.get(1));
    n.setInfragenericEpithet("Igen");
    n.setInfraspecificEpithet(null);
    n.setNotho(NamePart.SPECIFIC);
    n.setFossil(true);
    n.setRank(Rank.SPECIES);
    n.setCombinationYear(StringUtils.randomSpeciesYear());
    n.setCombinationAuthors(Lists.newArrayList("Mill."));
    n.setOriginalYear(StringUtils.randomSpeciesYear());
    n.setOriginalAuthors(Lists.newArrayList("L.", "DC"));
    n.setOrigin(Origin.SOURCE);
    n.setType(NameType.SCIENTIFIC);
    return n;
  }

  @Test
  public void roundtrip() throws Exception {
    Name n1 = create("sk1");
    mapper().create(n1);
    assertNotNull(n1.getKey());
    commit();

    Name n1b = mapper().get(DaoTestUtil.DATASET1.getKey(), n1.getId());
    assertEquals(n1, n1b);

    Name n1c = mapper().getByKey(n1.getKey());
    assertEquals(n1, n1c);

    // now with basionym
    Name n2 = create("sk2");
    n2.setOriginalName(n1);
    mapper().create(n2);

    commit();

    // we use a new instance of n1 with just the keys for the equality tests
    n1 = new Name();
    n1.setKey(n2.getOriginalName().getKey());
    n1.setId(n2.getOriginalName().getId());
    n2.setOriginalName(n1);

    Name n2b = mapper().get(DaoTestUtil.DATASET1.getKey(), n2.getId());
    assertEquals(n2, n2b);

    Name n2c = mapper().getByKey(n2.getKey());
    assertEquals(n2, n2c);

  }

  @Test
  public void list() throws Exception {
    List<Name> names = Lists.newArrayList();
    names.add(create(DaoTestUtil.DATASET2));
    names.add(create(DaoTestUtil.DATASET2));
    names.add(create(DaoTestUtil.DATASET2));
    names.add(create(DaoTestUtil.DATASET2));
    names.add(create(DaoTestUtil.DATASET2));

    for (Name n : names) {
      mapper().create(n);
    }
    commit();

    // get first page
    Page p = new Page(0,3);

    List<Name> res = mapper().list(DaoTestUtil.DATASET2.getKey(), p);
    assertEquals(3, res.size());
    assertEquals(Lists.partition(names, 3).get(0), res);

    // next page
    p.next();
    res = mapper().list(DaoTestUtil.DATASET2.getKey(), p);
    assertEquals(2, res.size());
    List<Name> l2 = Lists.partition(names, 3).get(1);
    assertEquals(l2, res);
  }

  @Test
  public void count() throws Exception {
    assertEquals(2, mapper().count(DaoTestUtil.DATASET1.getKey()));

    mapper().create(create());
    mapper().create(create());
    commit();
    assertEquals(4, mapper().count(DaoTestUtil.DATASET1.getKey()));
  }

  @Test
  public void synonyms() throws Exception {
    Name n2bas = create("n2");
    mapper().create(n2bas);

    Name n1 = create("n1");
    n1.setOriginalName(n2bas);
    mapper().create(n1);

    Name n3 = create("n3", n2bas);
    mapper().create(n3);

    Name n4 = create("n4", n2bas);
    mapper().create(n4);

    commit();

    List<Name> s1 = mapper().synonymsByKey(n1.getKey());
    assertEquals(4, s1.size());
    List<Name> s2 = mapper().synonymsByKey(n1.getKey());
    assertEquals(s1, s2);
    List<Name> s3 = mapper().synonymsByKey(n1.getKey());
    assertEquals(s1, s3);
    List<Name> s4 = mapper().synonymsByKey(n1.getKey());
    assertEquals(s1, s4);
  }

}