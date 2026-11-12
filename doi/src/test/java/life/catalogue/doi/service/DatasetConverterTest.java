package life.catalogue.doi.service;

import life.catalogue.api.model.Agent;
import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.User;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.Datasets;

import java.net.URI;
import java.util.List;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DatasetConverterTest {
  static final String PORTAL = "https://www.catalogueoflife.org/data/metadata";
  final DatasetConverter converter = new DatasetConverter(
    URI.create("https://www.catalogueoflife.org"),
    URI.create("https://www.checklistbank.org"),
    key -> new User()
  );

  static Dataset release(int key, int projectKey, DatasetOrigin origin) {
    Dataset d = new Dataset();
    d.setKey(key);
    d.setSourceKey(projectKey);
    d.setOrigin(origin);
    d.setTitle("Release " + key);
    d.setCreator(List.of(Agent.organisation("Catalogue of Life")));
    d.setDoi(DOI.dataset("10.48580", key));
    return d;
  }

  @Test
  void latestColReleasesPointToPortal() {
    assertEquals(PORTAL, converter.release(release(316595, Datasets.COL, DatasetOrigin.RELEASE), true, null, null).getUrl());
    assertEquals(PORTAL, converter.release(release(316691, Datasets.COL, DatasetOrigin.XRELEASE), true, null, null).getUrl());
    assertEquals(URI.create(PORTAL), converter.releaseURI(316691, Datasets.COL, true));
  }

  /**
   * https://github.com/CatalogueOfLife/checklistbank/issues/1753
   */
  @Test
  void latestReleaseOfOtherProjectPointsToChecklistBank() {
    assertEquals("https://www.checklistbank.org/dataset/316757",
      converter.release(release(316757, 315439, DatasetOrigin.RELEASE), true, null, null).getUrl());
    assertEquals("https://www.checklistbank.org/dataset/312904",
      converter.release(release(312904, 312605, DatasetOrigin.XRELEASE), true, null, null).getUrl());
    assertEquals(URI.create("https://www.checklistbank.org/dataset/316757"), converter.releaseURI(316757, 315439, true));
  }

  @Test
  void olderColReleasePointsToChecklistBank() {
    assertEquals("https://www.checklistbank.org/dataset/316441",
      converter.release(release(316441, Datasets.COL, DatasetOrigin.XRELEASE), false, null, null).getUrl());
    assertEquals(URI.create("https://www.checklistbank.org/dataset/316441"), converter.releaseURI(316441, Datasets.COL, false));
  }

  @Test
  void unknownProjectPointsToChecklistBank() {
    assertEquals(URI.create("https://www.checklistbank.org/dataset/100"), converter.releaseURI(100, null, true));
  }
}
