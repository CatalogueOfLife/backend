package life.catalogue.release;

import com.google.common.collect.Lists;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.Datasets;
import org.junit.Test;

import java.time.LocalDate;
import java.util.Collections;

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
}