package life.catalogue.importer;

import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.importer.store.model.UsageData;
import life.catalogue.junit.PgSetupRule;

import org.gbif.nameparser.api.NameType;

import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Test;


import static org.junit.Assert.*;

/**
 *
 */
public class NormalizerTxtTreeIT extends NormalizerITBase {

  @ClassRule
  public static PgSetupRule pgSetupRule = new PgSetupRule();

  public NormalizerTxtTreeIT() {
    super(DataFormat.TEXT_TREE, NormalizerFullIT::newIndex);
  }
  
  @Test
  public void mammalia() throws Exception {
    normalize(0);
    store.debug();
    UsageData s = usageByID("13");
    assertTrue(s.isSynonym());
    assertEquals("Pardina", s.usage.getLabel());

    UsageData t = usageByID("12");
    assertFalse(t.isSynonym());
    assertEquals("Lynx", t.usage.getLabel());

    var accs = store.usages().accepted(s).stream().map(store::nameUsage).toList();
    assertEquals(1, accs.size());
    assertEquals(t.getId(), accs.getFirst().ud.getId());
    assertEquals(t.usage.getLabel(), accs.getFirst().nd.getName().getLabel());
  }

  /**
   * A taxonomic note is the name phrase, as in ColDP, and never a reference id
   */
  @Test
  public void taxonomicNote() throws Exception {
    normalize(10);
    store.debug();
    UsageData u = usageByID("3");
    assertFalse(u.isSynonym());
    assertEquals("Abies alba Mill.", u.usage.getName().getLabel());
    assertEquals("sensu Smith 1999", u.usage.getNamePhrase());
    assertNull(u.usage.getAccordingToId());

    u = usageByID("4");
    assertTrue(u.isSynonym());
    assertEquals("sensu Jones 2001", u.usage.getNamePhrase());
    assertNull(u.usage.getAccordingToId());
  }

  @Test
  public void californicum() throws Exception {
    normalize(2);
    store.debug();
    UsageData u = usageByID("14");
    assertTrue(u.isSynonym());
    // text-tree strips the leading "?" as its provisional marker, so the parser only sees the bare epithet
    assertEquals("californicum Torr. & A.Gray", u.usage.getName().getLabel());
    assertNull(u.usage.getName().getNamesIndexId());
  }

  @Ignore("re-enable once name-parser fixes the VIRUS false-positive on zoological 'vector'/'virus' binomials; see name-parser PREFLIGHT_VIRUS_FALSE_POSITIVES.md")
  @Test
  public void aspilota() throws Exception {
    normalize(3);
    store.debug();
    UsageData u = usageByID("8");
    assertFalse(u.isSynonym());
    assertEquals("Aspilota vector Belokobylskij, 2007", u.usage.getName().getLabel());
    assertEquals(NameType.SCIENTIFIC, u.usage.getName().getType());
    assertEquals("Aspilota", u.usage.getName().getGenus());
    assertEquals("vector", u.usage.getName().getSpecificEpithet());

    VerbatimRecord v = store.getVerbatim(u.getVerbatimKey());
    assertEquals(0, v.getIssues().size());
  }

}
