package life.catalogue.api.model;

import life.catalogue.api.vocab.EstablishmentMeans;
import life.catalogue.api.vocab.area.Country;
import life.catalogue.api.vocab.area.Gazetteer;
import life.catalogue.api.vocab.area.GenericArea;

import org.junit.Test;

import static org.junit.Assert.*;

public class DistributionTest {

  static Distribution dist(GenericArea area, EstablishmentMeans means) {
    var d = new Distribution();
    d.setArea(area);
    d.setEstablishmentMeans(means);
    return d;
  }

  @Test
  public void isDuplicate() {
    var tdwg = new GenericArea(Gazetteer.TDWG, "BZL", "Brazil");
    // same area, different status
    assertTrue(dist(tdwg, EstablishmentMeans.NATIVE).isDuplicate(dist(tdwg, EstablishmentMeans.INTRODUCED)));
    // same id, ignoring case and the name
    assertTrue(dist(tdwg, null).isDuplicate(dist(new GenericArea(Gazetteer.TDWG, "bzl"), null)));
    // other area or gazetteer
    assertFalse(dist(tdwg, null).isDuplicate(dist(new GenericArea(Gazetteer.TDWG, "BZC", "Brazil"), null)));
    assertFalse(dist(tdwg, null).isDuplicate(dist(new GenericArea(Gazetteer.ISO, "BZL", "Brazil"), null)));
    // free text areas compare by name
    assertTrue(dist(new GenericArea("Andes"), null).isDuplicate(dist(new GenericArea("andes"), EstablishmentMeans.NATIVE)));
    assertFalse(dist(new GenericArea("Andes"), null).isDuplicate(dist(new GenericArea("Alps"), null)));
    // no area
    assertTrue(dist(null, null).isDuplicate(dist(null, EstablishmentMeans.NATIVE)));
    assertFalse(dist(null, null).isDuplicate(dist(tdwg, null)));
  }

  @Test
  public void isDuplicateCountry() {
    var de = new Distribution();
    de.copyArea(Country.GERMANY);
    var de2 = dist(new GenericArea(Country.GERMANY), EstablishmentMeans.NATIVE);
    assertTrue(de.isDuplicate(de2));
    assertTrue(de2.isDuplicate(de));
  }
}
