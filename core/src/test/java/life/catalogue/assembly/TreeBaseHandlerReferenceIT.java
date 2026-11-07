package life.catalogue.assembly;

import life.catalogue.api.model.DSID;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.Sector;
import life.catalogue.api.model.SectorImport;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Users;
import life.catalogue.db.mapper.ReferenceMapper;
import life.catalogue.db.mapper.SectorMapper;
import life.catalogue.junit.PgSetupRule;
import life.catalogue.junit.SqlSessionFactoryRule;
import life.catalogue.junit.TestDataRule;
import life.catalogue.matching.nidx.NameIndexFactory;

import org.gbif.nameparser.api.Rank;

import java.util.Map;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

/**
 * A sync copies each reference of its sector only once, the references sharing a citation becoming one.
 * The citations used to be looked up with a query per new reference that scanned all references of the sector,
 * they are now kept in memory, starting with the references the sector holds already.
 */
public class TreeBaseHandlerReferenceIT {

  @ClassRule
  public final static SqlSessionFactoryRule pg = new PgSetupRule();

  @Rule
  public final TestDataRule dataRule = TestDataRule.apple();

  @Test
  public void referencesByCitation() throws Exception {
    final var factory = SqlSessionFactoryRule.getSqlSessionFactory();
    final Sector s = new Sector();
    s.setDatasetKey(Datasets.COL);
    s.setSubjectDatasetKey(TestDataRule.APPLE.key);
    s.setMode(Sector.Mode.ATTACH);
    s.applyUser(Users.TESTER);
    try (SqlSession session = factory.openSession(true)) {
      session.getMapper(SectorMapper.class).create(s);
      // a reference the sector holds already
      var old = ref("old", "Old citation.");
      old.setDatasetKey(Datasets.COL);
      old.setSectorKey(s.getId());
      old.applyUser(Users.TESTER);
      session.getMapper(ReferenceMapper.class).create(old);
    }
    s.setEntities(Set.of(EntityType.NAME_USAGE, EntityType.REFERENCE));
    s.setRanks(Set.of(Rank.values()));

    final String a;
    final String c;
    try (var h = new TreeCopyHandler(Datasets.COL, Map.of(), factory, NameIndexFactory.passThru(), Users.TESTER, s, new SectorImport())) {
      a = h.lookupOrCreateReference(ref("a", "Smith 1999."));
      // another source reference with the same citation becomes the same one
      assertEquals(a, h.lookupOrCreateReference(ref("b", "Smith 1999.")));
      // and the same source reference again too
      assertEquals(a, h.lookupOrCreateReference(ref("a", "Smith 1999.")));
      c = h.lookupOrCreateReference(ref("c", "Jones 2001."));
      assertNotEquals(a, c);
      // the existing reference of the sector is reused
      assertEquals("old", h.lookupOrCreateReference(ref("d", "Old citation.")));
      // references without citation never match
      assertNotEquals(h.lookupOrCreateReference(ref("e", null)), h.lookupOrCreateReference(ref("f", null)));
    }

    try (SqlSession session = factory.openSession(true)) {
      var rm = session.getMapper(ReferenceMapper.class);
      // old, Smith and Jones have a citation, the two without one are not listed
      assertEquals(3, rm.listCitations(Datasets.COL, s.getId()).size());
      assertEquals("Smith 1999.", rm.get(DSID.of(Datasets.COL, a)).getCitation());
      assertEquals("Jones 2001.", rm.get(DSID.of(Datasets.COL, c)).getCitation());
    }
  }

  private static Reference ref(String id, String citation) {
    var r = new Reference();
    r.setId(id);
    r.setCitation(citation);
    return r;
  }
}
