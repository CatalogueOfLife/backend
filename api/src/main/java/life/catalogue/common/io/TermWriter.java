package life.catalogue.common.io;

import life.catalogue.api.jackson.PermissiveEnumSerde;
import life.catalogue.api.model.CslDate;
import life.catalogue.api.model.CslName;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.date.FuzzyDate;

import org.gbif.dwc.terms.Term;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TermWriter implements AutoCloseable {
  private static Logger LOG = LoggerFactory.getLogger(TermWriter.class);
  protected final Term rowType;
  protected final Map<Term, Integer> cols;
  protected String[] row;
  private final RowWriter writer;
  private int counter = 0;


  public static class TSV extends TermWriter{

    public TSV(File dir, Term rowType, List<? extends Term> cols) throws IOException {
      super(setupWriter(dir, rowType), rowType, cols);
    }

    private static RowWriter setupWriter(File dir, Term rowType) {
      File f = new File(dir, filename(rowType));
      return TabWriter.fromFile(f);
    }

    public static String filename(Term rowType) {
      return rowType.simpleName() + ".tsv";
    }
  }


  public TermWriter(RowWriter writer, Term rowType, List<? extends Term> cols) throws IOException {
    this.writer = writer;
    this.rowType = rowType;
    Map<Term, Integer> map = new HashMap<>();
    int idx = 0;
    for (Term t : cols) {
      map.put(t, idx++);
    }
    this.cols = Map.copyOf(map);

    // write header row
    row = new String[map.size()];
    for (Term t : cols) {
      set(t, t.prefixedName());
    }
    next();
    counter=0;
  }

  public void next() throws IOException {
    writer.write(row);
    row = new String[cols.size()];
    if (++counter % 100000 == 0) {
      LOG.debug("Written {} {}s", counter, rowType.simpleName());
    }
  }

  public int getCounter() {
    return counter;
  }

  @Override
  public void close() throws IOException {
    writer.close();
    LOG.info("Written {} {}s in total", counter, rowType.simpleName());
  }

  public Term getRowType() {
    return rowType;
  }

  public String get(Term term) {
    int idx = cols.getOrDefault(term, -1);
    if (idx < 0) return null;
    return row[idx];
  }

  public boolean has(Term term) {
    return cols.containsKey(term) && row[cols.get(term)] != null;
  }

  public void unset(Term term) {
    set(term, (String) null);
  }

  public void set(Term term, String value) {
    int idx = cols.getOrDefault(term, -1);
    if (idx < 0) throw new IllegalArgumentException(term.prefixedName() + " is not mapped for " + rowType.prefixedName());
    row[idx]=StringUtils.trimToNull(value);
  }

  public <T> void set(Term term, T value, Function<T, String> converter) {
    if (value != null) {
      set(term, converter.apply(value));
    }
  }

  /**
   * Concatenates values with comma using toString.
   */
  public <T> void set(Term term, Collection<T> value) {
    set(term, value, ",");
  }

  /**
   * Concatenates values with comma
   */
  public <T> void set(Term term, Collection<T> value, Function<T, String> converter) {
    set(term, value, ",", converter);
  }

  /**
   * Concatenates values with given delimiter using toString.
   */
  public <T> void set(Term term, Collection<T> value, String delimiter) {
    set(term, value, delimiter, Object::toString);
  }

  public <T> void set(Term term, Collection<T> value, String delimiter, Function<T, String> converter) {
    if (value != null && !value.isEmpty()) {
      set(term, value.stream()
                     .filter(Objects::nonNull)
                     .map(converter)
                     .filter(StringUtils::isNotBlank)
                     .collect(Collectors.joining(delimiter))
      );
    }
  }

  public void set(Term term, CslDate value) {
    if (value != null) {
      String date;
      if (value.getDateParts() != null && value.getDateParts().length > 0 && value.getDateParts()[0].length > 0) {
        date = FuzzyDate.of(value.getDateParts()[0]).toISO();
      } else {
        date = ObjectUtils.coalesce(value.getLiteral(), value.getRaw());
      }
      set(term, date);
    }
  }

  public void set(Term term, CslName[] value) {
    if (value != null) {
      set(term, CslUtil.toColdpString(value));
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

  public void set(Term term, Enum<?> value) {
    if (value != null) {
      set(term, PermissiveEnumSerde.enumValueName(value));
    }
  }

}
