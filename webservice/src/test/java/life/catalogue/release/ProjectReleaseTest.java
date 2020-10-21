package life.catalogue.release;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.Organisation;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Setting;
import org.junit.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ProjectReleaseTest {

  @Test
  public void releaseDataset() throws Exception {
    DatasetSettings ds = new DatasetSettings();
    ds.put(Setting.RELEASE_ALIAS_TEMPLATE, "CoL{created,yy.M}");
    ds.put(Setting.RELEASE_TITLE_TEMPLATE, "Catalogue of Life - {created,MMMM yyyy}");
    ds.put(Setting.RELEASE_CITATION_TEMPLATE, "{editors} ({created,yyyy}). Species 2000 & ITIS Catalogue of Life, {created,ddd MMMM yyyy}. Digital resource at www.catalogueoflife.org. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.");

    Dataset d = new Dataset();
    d.setKey(Datasets.COL);
    d.setTitle("Catalogue of Life");
    d.setOrganisations(Organisation.parse("Species 2000", "ITIS"));
    d.setEditors(List.of(
      new Person("Yuri","Roskov"),
      new Person("Geoff", "Ower"),
      new Person("Thomas", "Orrell"),
      new Person("David", "Nicolson")
    ));
    d.setCreated(LocalDateTime.of(2020,10,6,  1,1));

    ProjectRelease.releaseDataset(d, ds);
    assertEquals("CoL20.10", d.getAlias());
    assertEquals("Catalogue of Life - October 2020", d.getTitle());
    assertEquals("Roskov Y., Ower G., Orrell T., Nicolson D. (eds.) (2020). Species 2000 & ITIS Catalogue of Life, 6th October 2020. Digital resource at www.catalogueoflife.org. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.",
      d.getCitation()
    );
  }

}