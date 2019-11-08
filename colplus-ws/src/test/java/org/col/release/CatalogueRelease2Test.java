package org.col.release;

import java.time.LocalDate;

import com.google.common.collect.Lists;
import org.col.api.model.Dataset;
import org.col.api.vocab.Datasets;
import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class CatalogueRelease2Test {
  
  @Test
  public void release() throws Exception {
    Dataset d = new Dataset();
    d.setKey(Datasets.DRAFT_COL);
    d.setTitle("Species 2000 & ITIS Catalogue of Life");
    d.setAuthorsAndEditors(Lists.newArrayList("Roskov Y.", "Ower G.", "Orrell T.", "Nicolson D., eds."));
    d.setReleased(LocalDate.parse("2019-04-21"));
    assertEquals("Roskov Y., Ower G., Orrell T., Nicolson D., eds. (2019). Species 2000 & ITIS Catalogue of Life, 2019-04-21. Digital resource at www.catalogueoflife.org/col. Species 2000: Naturalis, Leiden, the Netherlands. ISSN 2405-8858.",
        CatalogueRelease.buildCitation(d)
    );
  }
}