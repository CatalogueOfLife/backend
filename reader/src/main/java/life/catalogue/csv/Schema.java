package life.catalogue.csv;

import life.catalogue.common.io.PathUtils;

import org.gbif.dwc.terms.Term;

import java.nio.charset.Charset;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Collectors;

import org.apache.commons.text.StringEscapeUtils;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.univocity.parsers.common.CommonParserSettings;
import com.univocity.parsers.tsv.TsvParserSettings;

/**
 *
 */
public class Schema {
  public final List<Path> files;
  public final Term rowType;
  public final Charset encoding;
  public final CommonParserSettings<?> settings;
  public final List<Field> columns;

  public Schema(List<Path> files, Term rowType, Charset encoding, CommonParserSettings<?> settings, List<Field> columns) {
    Preconditions.checkArgument(files != null && !files.isEmpty(), "At least one file is required");
    this.files = files;
    this.rowType = Preconditions.checkNotNull(rowType);
    this.encoding = Preconditions.checkNotNull(encoding);
    this.columns = ImmutableList.copyOf(columns);
    this.settings = settings;
  }

  public static class Field {
    public final Term term;
    public final String value;
    public final Integer index;
    public final String delimiter;
    
    public Field(Term term, Integer index) {
      this(term, null, index, null);
    }
    
    public Field(Term term, String value, Integer index, String delimiter) {
      this.term = Preconditions.checkNotNull(term);
      if (value == null && index == null) {
        throw new IllegalArgumentException("Default value or column index is required");
      }
      Preconditions.checkArgument(index == null || index >= 0, "Column index must be positive");
      this.value = value;
      this.index = index;
      this.delimiter = delimiter;
    }

    @Override
    public String toString() {
      StringBuilder sb = new StringBuilder();
      sb.append(term);
      if (index != null || value != null) {
        sb.append("[");
        if (index != null) {
          sb.append(index);
        }
        if (value != null) {
          if (index != null) {
            sb.append(", ");
          }
          sb.append(value);
        }
        sb.append("]");
      }
      return sb.toString();
    }
  }

  public Path getFirstFile() {
    return files.get(0);
  }

  public String getFilesLabel() {
    return files.stream()
                .map(Path::getFileName)
                .map(Path::toString)
                .collect(Collectors.joining("; "));
  }

  public Field field(Term term) {
    for (Field f : columns) {
      if (f.term != null && f.term.equals(term)) return f;
    }
    return null;
  }
  
  public boolean hasTerm(Term term) {
    for (Field f : columns) {
      if (f.term != null && f.term.equals(term)) return true;
    }
    return false;
  }
  
  public boolean hasAnyTerm(Term... terms) {
    for (Term t : terms) {
      if (hasTerm(t)) {
        return true;
      }
    }
    return false;
  }

  public int size() {
    return columns.size();
  }
  
  public boolean isEmpty() {
    return columns.isEmpty();
  }

  public boolean isTsv() {
    return settings instanceof TsvParserSettings;
  }

  @Override
  public String toString() {
    return rowType + " ["
        + PathUtils.getFilename(getFirstFile())
        + " "
        + StringEscapeUtils.escapeJava(String.valueOf(settings.getFormat()))
        + " "
        + columns.size()
        + "]";
  }
}
