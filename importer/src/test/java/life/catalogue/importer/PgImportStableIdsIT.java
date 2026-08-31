package life.catalogue.importer;

import life.catalogue.api.vocab.DataFormat;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.db.mapper.NameUsageMapper.GeneratedUsage;
import life.catalogue.junit.SqlSessionFactoryRule;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import org.apache.ibatis.session.SqlSession;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Identifiers the importer generates itself must survive a re-import of a changed source,
 * see <a href="https://github.com/CatalogueOfLife/backend/issues/1189">#1189</a>.
 */
public class PgImportStableIdsIT extends PgImportITBase {

  /**
   * A DwC-A with a purely denormalised classification. All higher taxa are implicit, so their ids are generated.
   * The second version drops one species, adds two and renumbers them all, which changes the order the
   * implicit taxa are created in - without this feature they all get different ids.
   */
  @Test
  public void implicitTaxaKeepTheirIds() throws Exception {
    normalizeAndImport(DataFormat.DWCA, 59);
    final int key = dataset.getKey();
    final Map<String, GeneratedUsage> before = generatedUsages(key, false);
    // 2 kingdoms, 2 phyla, 3 classes, 4 orders, 5 families and 7 genera - Aster exists twice
    assertEquals(23, before.size());
    assertTrue(before.containsKey("GENUS|Aster|Asteraceae"));
    assertTrue(before.containsKey("GENUS|Aster|Poaceae"));
    assertTrue(before.containsKey("FAMILY|Cyperaceae|Poales"));

    normalizeAndImport(DataFormat.DWCA, "59b");
    assertEquals(key, (int) dataset.getKey());
    final Map<String, GeneratedUsage> after = generatedUsages(key, false);

    assertStable(before, after);
    // the homonymous genera must not have swapped their ids
    assertEquals(before.get("GENUS|Aster|Asteraceae").usageId, after.get("GENUS|Aster|Asteraceae").usageId);
    assertEquals(before.get("GENUS|Aster|Poaceae").usageId, after.get("GENUS|Aster|Poaceae").usageId);

    // the removed species took its family and genus with it
    assertFalse(after.containsKey("FAMILY|Cyperaceae|Poales"));
    // and the new ones got ids nothing held before
    assertNew(before, after, "FAMILY|Xylariaceae|Xylariales");
    assertNew(before, after, "GENUS|Xylaria|Xylariaceae");
  }

  /**
   * A TextTree carries no identifiers of its own, so every id is the line number. Inserting a family at the top
   * shifts every line below it, which used to renumber the whole tree.
   */
  @Test
  public void textTreeKeepsItsIds() throws Exception {
    normalizeAndImport(DataFormat.TEXT_TREE, 8);
    final int key = dataset.getKey();
    final Map<String, GeneratedUsage> before = generatedUsages(key, true);
    assertEquals(8, before.size());
    // the ids really are the line numbers to begin with
    assertEquals("1", before.get("KINGDOM|Plantae|null").usageId);

    normalizeAndImport(DataFormat.TEXT_TREE, "8b");
    assertEquals(key, (int) dataset.getKey());
    final Map<String, GeneratedUsage> after = generatedUsages(key, true);
    assertEquals(11, after.size());

    assertStable(before, after);
    assertNew(before, after, "FAMILY|Apiaceae|Plantae");
    assertNew(before, after, "SPECIES|Daucus carota|Daucus");
  }

  /**
   * A TextTree whose nodes carry explicit IDs already has stable usage ids, but its name ids are derived from
   * the line number all the same and used to shift with them.
   */
  @Test
  public void textTreeWithExplicitIdsKeepsNameIds() throws Exception {
    normalizeAndImport(DataFormat.TEXT_TREE, 9);
    final int key = dataset.getKey();
    final Map<String, GeneratedUsage> before = generatedUsages(key, true);
    assertEquals(8, before.size());
    var plantae = before.get("KINGDOM|Plantae|null");
    assertEquals("p1", plantae.usageId);   // the explicit ID
    assertEquals("1", plantae.nameId);     // but the name id is the line number
    assertEquals("2", before.get("FAMILY|Asteraceae|Plantae").nameId);

    normalizeAndImport(DataFormat.TEXT_TREE, "9b");
    assertEquals(key, (int) dataset.getKey());
    final Map<String, GeneratedUsage> after = generatedUsages(key, true);
    assertEquals(11, after.size());

    assertStable(before, after);
    // the new family sits on the line Asteraceae reclaims, so it cannot keep that name id
    assertNotEquals("2", after.get("FAMILY|Apiaceae|Plantae").nameId);
  }

  /**
   * Every record present in both versions keeps both its usage and its name id, and no id is used twice.
   */
  private void assertStable(Map<String, GeneratedUsage> before, Map<String, GeneratedUsage> after) {
    int checked = 0;
    for (var e : before.entrySet()) {
      var now = after.get(e.getKey());
      if (now != null) {
        assertEquals("usage id of " + e.getKey(), e.getValue().usageId, now.usageId);
        assertEquals("name id of " + e.getKey(), e.getValue().nameId, now.nameId);
        checked++;
      }
    }
    assertTrue("nothing survived the re-import", checked > 0);

    Set<String> ids = new HashSet<>();
    for (var g : after.values()) {
      assertTrue("duplicate usage id " + g.usageId, ids.add(g.usageId));
    }
  }

  /** the record is new and holds an id no record of the previous version had */
  private void assertNew(Map<String, GeneratedUsage> before, Map<String, GeneratedUsage> after, String key) {
    assertFalse(key + " already existed", before.containsKey(key));
    var g = after.get(key);
    assertNotNull("missing " + key, g);
    for (var old : before.values()) {
      assertNotEquals(key + " reused the usage id of " + old, old.usageId, g.usageId);
      assertNotEquals(key + " reused the name id of " + old, old.nameId, g.usageId);
    }
  }

  /**
   * Reads the state back through the very mapper method the feature uses, keyed by rank, name and parent name
   * so it is independent of the identifiers under test.
   */
  private Map<String, GeneratedUsage> generatedUsages(int datasetKey, boolean includeSource) {
    Map<String, GeneratedUsage> map = new HashMap<>();
    try (SqlSession s = SqlSessionFactoryRule.getSqlSessionFactory().openSession()) {
      PgUtils.consume(
        () -> s.getMapper(NameUsageMapper.class).processDatasetGeneratedUsages(datasetKey, includeSource),
        g -> assertNull("duplicate key for " + g, map.put(g.rank + "|" + g.scientificName + "|" + g.parent, g))
      );
    }
    return map;
  }
}
