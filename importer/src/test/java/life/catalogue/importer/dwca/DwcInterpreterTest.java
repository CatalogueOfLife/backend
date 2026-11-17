package life.catalogue.importer.dwca;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.Distribution;
import life.catalogue.api.model.VerbatimRecord;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.area.Gazetteer;
import life.catalogue.csv.MappingInfos;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.InterpreterTestAbstractBase;
import life.catalogue.importer.store.ImportStore;

import org.gbif.dwc.terms.DwcTerm;

import org.junit.Test;

import static org.junit.Assert.*;

public class DwcInterpreterTest extends InterpreterTestAbstractBase<DwcInterpreter> {

  private static VerbatimRecord rec(String locationID, String countryCode, String locality) {
    var v = new VerbatimRecord();
    v.put(DwcTerm.locationID, locationID);
    v.put(DwcTerm.countryCode, countryCode);
    v.put(DwcTerm.locality, locality);
    return v;
  }

  private Distribution single(VerbatimRecord v) {
    var dists = interpreter.interpretDistribution(v);
    assertEquals(1, dists.size());
    return dists.get(0);
  }

  /**
   * GRIIS Albania shares locationID=ALBANIA together with countryCode=AL.
   * https://github.com/CatalogueOfLife/rcol/issues/9
   */
  @Test
  public void countryCodeFallback() {
    var v = rec("ALBANIA", "AL", null);
    var d = single(v);
    assertEquals(Gazetteer.ISO, d.getArea().getGazetteer());
    assertEquals("AL", d.getArea().getId());
    assertEquals("Albania", d.getArea().getName());
    assertTrue(v.contains(Issue.DISTRIBUTION_AREA_INVALID));

    // an unparsable gazetteer id falls back too
    v = rec("tdwg:milchbar", "AL", null);
    d = single(v);
    assertEquals(Gazetteer.ISO, d.getArea().getGazetteer());
    assertEquals("AL", d.getArea().getId());
    assertTrue(v.contains(Issue.DISTRIBUTION_AREA_INVALID));
  }

  @Test
  public void parsableLocationID() {
    // GRIIS Belgium
    var v = rec("ISO_3166:BE", "BE", "Belgium");
    var d = single(v);
    assertEquals(Gazetteer.ISO, d.getArea().getGazetteer());
    assertEquals("BE", d.getArea().getId());
    assertFalse(v.contains(Issue.DISTRIBUTION_AREA_INVALID));

    // a valid locationID of another gazetteer wins over the countryCode
    v = rec("tdwg:AGS", "AR", null);
    d = single(v);
    assertEquals(Gazetteer.TDWG, d.getArea().getGazetteer());
    assertEquals("AGS", d.getArea().getId());
    assertFalse(v.contains(Issue.DISTRIBUTION_AREA_INVALID));
  }

  @Test
  public void textLocationIdWithoutCountryCode() {
    var v = rec("ALBANIA", null, null);
    var d = single(v);
    assertEquals(Gazetteer.TEXT, d.getArea().getGazetteer());
    assertEquals("ALBANIA", d.getArea().getName());
    assertFalse(v.contains(Issue.DISTRIBUTION_AREA_INVALID));
  }

  @Override
  protected DwcInterpreter buildInterpreter(DatasetSettings settings, ReferenceFactory refFactory, ImportStore store) {
    return new DwcInterpreter(settings, new MappingInfos(), refFactory, store);
  }
}
