package life.catalogue.common.io;

import life.catalogue.coldp.ColdpTerm;

import org.gbif.dwc.terms.Term;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class TermWriterTest {

  @Test
  public void tsvSetCollection() throws Exception {
    try (TmpIO.Dir dir = new TmpIO.Dir()) {
      final Term term = ColdpTerm.referenceID;
      var w = new TermWriter.TSV(dir.file, ColdpTerm.Taxon, List.of(ColdpTerm.ID, term));
      assertNull(w.get(term));

      var list = new ArrayList<String>();
      w.set(term, list);
      assertNull(w.get(term));

      list.add("");
      w.set(term, list);
      assertNull(w.get(term));

      list.add(" ");
      w.set(term, list);
      assertNull(w.get(term));

      list.add(null);
      w.set(term, list);
      assertNull(w.get(term));

      w.set(term, List.of("a", "b", "c"));
      assertEquals("a,b,c", w.get(term));
    }
  }
}