package life.catalogue.common.io;

import life.catalogue.api.jackson.PermissiveEnumSerde;
import org.apache.commons.lang3.StringUtils;
import org.gbif.dwc.terms.Term;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public abstract class TermWriter implements AutoCloseable {
  protected final Term rowType;
  protected final Term idTerm;
  protected final Map<Term, Integer> cols;
  protected String[] row;
  private final RowWriter writer;

  public static class TSV extends TermWriter{
    public TSV(File dir, Term rowType, Term idTerm, List<Term> cols) throws IOException {
      super(setupWriter(dir, rowType), rowType, idTerm, cols);
    }

    static RowWriter setupWriter(File dir, Term rowType) {
      File f = new File(dir, filename(rowType));
      return TabWriter.fromFile(f);
    }
  }

  public TermWriter(RowWriter writer, Term rowType, Term idTerm, List<Term> cols) throws IOException {
    this.writer = writer;
    this.rowType = rowType;
    this.idTerm = idTerm;
    Map<Term, Integer> map = new HashMap<>();
    int idx = 0;
    map.put(idTerm, idx++);
    for (Term t : cols) {
      map.put(t, idx++);
    }
    this.cols = Map.copyOf(map);

    // write header row
    row = new String[map.size()];
    set(idTerm, idTerm.prefixedName());
    for (Term t : cols) {
      set(t, t.prefixedName());
    }
    next();
  }

  public static String filename(Term rowType) {
    return rowType.simpleName() + ".tsv";
  }

  public String next() throws IOException {
    writer.write(row);
    String id = get(idTerm);
    row = new String[cols.size()];
    return id;
  }

  @Override
  public void close() throws IOException {
    writer.close();
  }

  public Term getRowType() {
    return rowType;
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

  public <T> void set(Term term, T value, Function<T, String> converter) {
    if (value != null) {
      set(term, converter.apply(value));
    }
  }

  /**
   * Concatenates values with pipes
   */
  public void set(Term term, Collection<?> value) {
    if (value != null && !value.isEmpty()) {
      set(term, value.stream()
        .map(Object::toString)
        .filter(StringUtils::isNotBlank)
        .collect(Collectors.joining("|"))
      );
    }
  }

  public void set(Term term, Object value) {
    if (value != null) {
      set(term, value.toString());
    }
  }

  public void set(Term term, int value) {
    set(term, Integer.toString(value));
  }

  public void set(Term term, Enum value) {
    if (value != null) {
      set(term, PermissiveEnumSerde.enumValueName(value));
    }
  }
}
