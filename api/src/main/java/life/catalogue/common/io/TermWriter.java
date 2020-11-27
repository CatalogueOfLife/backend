package life.catalogue.common.io;

import org.gbif.dwc.terms.Term;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class TermWriter implements AutoCloseable {
  private final Term rowType;
  private final Term idTerm;
  private final Map<Term, Integer> cols;
  private final TabWriter writer;
  private String[] row;

  public TermWriter(File dir, Term rowType, Term idTerm, List<Term> cols) throws IOException {
    this.rowType = rowType;
    this.idTerm = idTerm;
    Map<Term, Integer> map = new HashMap<>();
    int idx = 0;
    map.put(idTerm, idx++);
    for (Term t : cols) {
      map.put(t, idx++);
    }
    this.cols = Map.copyOf(map);

    File f = new File(dir, rowType.simpleName() + ".tsv");
    writer = TabWriter.fromFile(f);

    // write header row
    row = new String[map.size()];
    set(idTerm, idTerm.prefixedName());
    for (Term t : cols) {
      set(t, t.prefixedName());
    }
    next();
  }

  public String next() throws IOException {
    writer.write(row);
    String id = get(idTerm);
    row = new String[cols.size()];
    return id;
  }

  public String get(Term term) {
    int idx = cols.getOrDefault(term, -1);
    if (idx < 0) return null;
    return row[idx];
  }

  public void set(Term term, String value) {
    int idx = cols.getOrDefault(term, -1);
    if (idx < 0) throw new IllegalArgumentException(term.prefixedName() + " is not mapped for " + rowType.prefixedName());
    row[idx]=value;
  }

  public void set(Term term, Object value) {
    set(term, value.toString());
  }

  public void set(Term term, Enum value) {
    set(term, value.name());
  }

  @Override
  public void close() throws IOException {
    writer.close();
  }
}
