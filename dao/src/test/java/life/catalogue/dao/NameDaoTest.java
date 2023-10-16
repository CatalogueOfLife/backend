package life.catalogue.dao;

import life.catalogue.api.TestEntityGenerator;
import life.catalogue.api.model.DSID;
import life.catalogue.api.model.IndexName;
import life.catalogue.api.model.Name;
import life.catalogue.api.model.NameRelation;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.SqlSessionFactoryRule;
import life.catalogue.db.mapper.NameMapper;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.es.NameUsageIndexService;
import life.catalogue.matching.NameIndexFactory;

import java.util.*;

import org.junit.Assert;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class NameDaoTest extends DaoTestBase {

  static final IndexName match = new IndexName(TestEntityGenerator.NAME4, 1);
  NameDao dao = new NameDao(SqlSessionFactoryRule.getSqlSessionFactory(), NameUsageIndexService.passThru(), NameIndexFactory.fixed(match), validator);
  
  @Test
  public void authorshipNormalization() throws Exception {
    Name n1 = TestEntityGenerator.newName("n2");

    dao.create(n1, Users.IMPORTER);
    Assert.assertNotNull(n1.getAuthorshipNormalized());
  }

  @Test
  public void nameMatching() throws Exception {
    Name n = TestEntityGenerator.newName("n2");
    dao.create(n, Users.IMPORTER);

    Name nidx = mapper(NameMapper.class).get(n);
    assertEquals(MatchType.VARIANT, nidx.getNamesIndexType());
    assertEquals(match.getKey(), nidx.getNamesIndexId());
  }

  @Test
  public void homotypic() throws Exception {
    // n1-n4 are homotypic names via transitive relations, n5+n6 are homotypic and n7 is not homotypic
    Name n = null;
    Map<Integer, Name> names = new HashMap<>();
    for (int i = 1; i<=7; i++) {
      n = TestEntityGenerator.newName("n"+i);
      dao.create(n, Users.IMPORTER);
      names.put(i, n);
    }

    NameRelationMapper nrm = mapper(NameRelationMapper.class);
    NameRelation rel = new NameRelation();
    rel.applyUser(Users.IMPORTER);
    rel.setDatasetKey(n.getDatasetKey());

    rel.setNameId("n1");
    rel.setRelatedNameId("n2");
    rel.setType(NomRelType.HOMOTYPIC);
    nrm.create(rel);

    rel.setType(NomRelType.BASIONYM);
    nrm.create(rel);

    rel.setNameId("n1");
    rel.setRelatedNameId("n3");
    rel.setType(NomRelType.BASED_ON);
    nrm.create(rel);

    rel.setNameId("n3");
    rel.setRelatedNameId("n4");
    rel.setType(NomRelType.HOMOTYPIC);
    nrm.create(rel);

    rel.setNameId("n4");
    rel.setRelatedNameId("n3");
    rel.setType(NomRelType.REPLACEMENT_NAME);
    nrm.create(rel);

    rel.setNameId("n4");
    rel.setRelatedNameId("n3");
    rel.setType(NomRelType.SPELLING_CORRECTION);
    nrm.create(rel);

    // group 5+6
    rel.setNameId("n5");
    rel.setRelatedNameId("n6");
    rel.setType(NomRelType.SPELLING_CORRECTION);
    nrm.create(rel);

    rel.setNameId("n6");
    rel.setRelatedNameId("n5");
    rel.setType(NomRelType.CONSERVED);
    nrm.create(rel);

    // group 7
    rel.setNameId("n7");
    rel.setRelatedNameId("n2");
    rel.setType(NomRelType.LATER_HOMONYM);
    nrm.create(rel);

    commit();

    System.out.println("Prepared");

    assertEquals(Set.of(names.get(2), names.get(3), names.get(4)), Set.copyOf(rmMatch(dao.homotypicGroup(DSID.of(n.getDatasetKey(), "n1")))));
    assertEquals(Set.of(names.get(1), names.get(3), names.get(4)), Set.copyOf(rmMatch(dao.homotypicGroup(DSID.of(n.getDatasetKey(), "n2")))));
    assertEquals(Set.of(names.get(6)), Set.copyOf(rmMatch(dao.homotypicGroup(DSID.of(n.getDatasetKey(), "n5")))));
    assertEquals(Collections.emptySet(), Set.copyOf(rmMatch(dao.homotypicGroup(DSID.of(n.getDatasetKey(), "n7")))));
  }

  static List<Name> rmMatch(List<Name> names) {
    names.forEach(n -> {
      n.setNamesIndexType(MatchType.NONE);
      n.setNamesIndexId(null);
    });
    return names;
  }

}
