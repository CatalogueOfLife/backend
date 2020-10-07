package life.catalogue.release;

import com.google.common.collect.Lists;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.Datasets;
import life.catalogue.api.vocab.Setting;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class ProjectReleaseTest {

  @Test
  public void buildCitation() throws Exception {
    Dataset d = new Dataset();
    d.setKey(Datasets.DRAFT_COL);
    d.setTitle("Species 2000 & ITIS Catalogue of Life");
    d.setEditors(Person.parse(Lists.newArrayList("Roskov Y.", "Ower G.", "Orrell T.", "Nicolson D.")));
    d.setReleased(LocalDate.parse("2019-04-21"));
    assertEquals("Roskov Y., Ower G., Orrell T., Nicolson D. (eds.) (2019). Species 2000 & ITIS Catalogue of Life, 2019-04-21. Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.",
      ProjectRelease.buildCitation(d)
    );

    d.setAuthors(d.getEditors());
    d.setEditors(Collections.emptyList());
    assertEquals("Roskov Y., Ower G., Orrell T., Nicolson D. (2019). Species 2000 & ITIS Catalogue of Life, 2019-04-21. Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.",
      ProjectRelease.buildCitation(d)
    );

    d.setEditors(null);
    assertEquals("Roskov Y., Ower G., Orrell T., Nicolson D. (2019). Species 2000 & ITIS Catalogue of Life, 2019-04-21. Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.",
      ProjectRelease.buildCitation(d)
    );
  }

  @Test
  public void releaseDataset() throws Exception {
    DatasetSettings ds = new DatasetSettings();
    ds.put(Setting.RELEASE_ALIAS_TEMPLATE, "CoL{date,yy.M}");
    ds.put(Setting.RELEASE_TITLE_TEMPLATE, "Catalogue of Life - {date,MMMM yyyy}");
    ds.put(Setting.RELEASE_CITATION_TEMPLATE, "{editors} ({date,yyyy}). Species 2000 & ITIS Catalogue of Life, {date,MMMM yyyy}. Digital resource at www.catalogueoflife.org. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.");

    Dataset d = new Dataset();
    d.setKey(Datasets.DRAFT_COL);
    d.setTitle("Catalogue of Life");
    d.setOrganisations(List.of("Species 2000", "ITIS"));
    d.setEditors(List.of(
      new Person("Yuri","Roskov"),
      new Person("Geoff", "Ower"),
      new Person("Thomas", "Orrell"),
      new Person("David", "Nicolson")
    ));
    d.setReleased(LocalDate.parse("2020-10-06"));

    ProjectRelease.releaseDataset(d, ds);
    assertEquals("CoL20.10", d.getAlias());
    assertEquals("Catalogue of Life - October 2020", d.getTitle());
    assertEquals("Roskov Y., Ower G., Orrell T., Nicolson D. (eds.) (2020). Species 2000 & ITIS Catalogue of Life, 2020-10-06. Digital resource at www.catalogueoflife.org. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.",
      d.getCitation()
    );
  }

}