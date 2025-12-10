package life.catalogue.importer.bibtex;

import de.undercouch.citeproc.bibtex.BibTeXConverter;

import life.catalogue.common.io.Resources;
import life.catalogue.concurrent.JobExecutor;

import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.store.ImportStore;

import org.jbibtex.BibTeXDatabase;
import org.jbibtex.ParseException;
import org.junit.jupiter.api.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import static org.junit.jupiter.api.Assertions.*;

public class BibTexInserterTest {

  @Test
  void bibTeXConverter() throws ParseException {
    var stream = Resources.stream("bibtex/afd-lit.bib");
    BibTeXConverter bc = new BibTeXConverter();
    BibTeXDatabase db = bc.loadDatabase(stream);
    bc.toItemData(db).forEach((id, cslItem) -> {
      assertNotNull(cslItem);
      assertNotNull(cslItem.getType());
      assertNotNull(cslItem.getTitle());
      assertNotNull(cslItem.getAuthor());
      assertNotNull(cslItem.getAuthor()[0].getFamily());
      assertNotNull(cslItem.getAuthor()[0].getGiven());
      assertNull(cslItem.getAuthor()[0].getLiteral()); // this gets set when the CSLName parsing fails
    });
  }
}