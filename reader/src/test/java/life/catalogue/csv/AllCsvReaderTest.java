package life.catalogue.csv;

import org.gbif.dwc.terms.Term;

import org.gbif.dwc.terms.TermFactory;

import org.junit.Test;

import java.io.File;
import java.io.IOException;

import static org.junit.Assert.*;

public class AllCsvReaderTest {

  @Test
  public void from() throws Exception {
    File dir = new File("/Users/markus/code/data/data-euro-leps");
    AllCsvReader r = AllCsvReader.from(dir.toPath());
    Term remarks = TermFactory.instance().findTerm("remarks", true);
    var s = r.schema(remarks);
    System.out.println(s);
  }
}