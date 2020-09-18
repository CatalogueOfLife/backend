package life.catalogue.release;

import com.google.common.collect.Lists;
import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.Datasets;
import org.junit.Test;

import java.time.LocalDate;

import static org.junit.Assert.assertEquals;

public class ProjectRelease2Test {
  
  @Test
  public void release() throws Exception {
    Dataset d = new Dataset();
    d.setKey(Datasets.DRAFT_COL);
    d.setTitle("Species 2000 & ITIS Catalogue of Life");
    d.setAuthorsAndEditors(Person.parse(Lists.newArrayList("Roskov Y.", "Ower G.", "Orrell T.", "Nicolson D.", "eds.")));
    d.setReleased(LocalDate.parse("2019-04-21"));
    assertEquals("Roskov Y., Ower G., Orrell T., Nicolson D., eds. (2019). Species 2000 & ITIS Catalogue of Life, 2019-04-21. Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.",
        ProjectRelease.buildCitation(d)
    );
  }
}