package life.catalogue.common.io;

import life.catalogue.coldp.ColdpTerm;

import java.util.List;

import org.junit.Test;

public class TermWriterTest {

  @Test
  public void tsvSetCollection() throws Exception {
    try (TmpIO.Dir dir = new TmpIO.Dir()) {
      var w = new TermWriter.TSV(dir.file, ColdpTerm.Taxon, List.of(ColdpTerm.ID, ColdpTerm.referenceID));
      w.set(ColdpTerm.referenceID, List.of(""));
      w.set(ColdpTerm.referenceID, List.of(" ", ""));
      w.set(ColdpTerm.referenceID, List.of("a", "b", "c"));
    }
  }
}