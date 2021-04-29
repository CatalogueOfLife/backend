package life.catalogue.common.io;

import com.google.common.base.Joiner;
import life.catalogue.api.jackson.ApiModule;
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

    File f = new File(dir, filename(rowType));
    writer = TabWriter.fromFile(f);

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

  @Override
  public void close() throws IOException {
    writer.close();
  }
}
