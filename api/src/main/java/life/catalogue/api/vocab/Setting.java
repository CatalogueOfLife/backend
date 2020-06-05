package life.catalogue.api.vocab;

import com.google.common.base.Preconditions;
import org.gbif.nameparser.api.NomCode;

import java.net.URI;
import java.time.LocalDate;
import static life.catalogue.api.vocab.DatasetOrigin.*;


public enum Setting {

  /**
   * When importing data from text files this overrides
   * the field delimiter character used
   */
  CSV_DELIMITER(String.class, EXTERNAL, MANAGED),

  /**
   * When importing data from text files this overrides
   * the quote character used
   */
  CSV_QUOTE(String.class, EXTERNAL, MANAGED),

  /**
   * When importing data from text files this overrides
   * the single character used for escaping quotes inside an already quoted value.
   * For example '"' for CSV
   */
  CSV_QUOTE_ESCAPE(String.class, EXTERNAL, MANAGED),

  /**
   * Overrides the gazetteer standard to use in all distribution interpretations for the dataset.
   */
  DISTRIBUTION_GAZETTEER(Gazetteer.class, EXTERNAL, MANAGED),

  /**
   * The nomenclatural code followed in the dataset.
   * It will be used mostly as a hint to format names accordingly.
   * If the dataset contains mixed data from multiple codes keep this field null.
   */
  NOMENCLATURAL_CODE(NomCode.class, EXTERNAL, MANAGED),

  /**
   * Setting that will inform the importer to rematch all decisions (decisions sensu strictu but also sectors and estimates)
   * Defaults to false
   */
  REMATCH_DECISIONS(Boolean.class, EXTERNAL, MANAGED),

  /**
   * Template used to build a new release title.
   * Use dataset properties in curly brackets, e.g. {title}
   * and the current {date} followed by an optional {@link java.time.format.DateTimeFormatter} syntax.
   *
   * See https://docs.oracle.com/en/java/javase/11/docs/api/java.base/java/time/format/DateTimeFormatter.html
   */
  RELEASE_TITLE_TEMPLATE(String.class, MANAGED),

  /**
   * Template used to build a new release alias.
   * See RELEASE_TITLE_TEMPLATE for usage.
   */
  RELEASE_ALIAS_TEMPLATE(String.class, MANAGED),

  /**
   * Template used to build a new release citation.
   * See RELEASE_TITLE_TEMPLATE for usage.
   */
  RELEASE_CITATION_TEMPLATE(String.class, MANAGED),

  DATA_FORMAT(DataFormat.class, EXTERNAL, MANAGED),

  /**
   * In continuous import mode the frequency the dataset is scheduled for imports.
   */
  IMPORT_FREQUENCY(Frequency.class, EXTERNAL),

  DATA_ACCESS(URI.class, EXTERNAL);

  private final Class type;
  private final DatasetOrigin[] origin;

  public Class getType() {
    return type;
  }

  public DatasetOrigin[] getOrigin() {
    return origin;
  }

  public boolean isEnum() {
    return type.isEnum();
  }

  Setting(Class type) {
    this(type, null);
  }

  /**
   * Use String, Integer, Boolean, LocalDate, URI or a custom col enumeration class
   *
   * @param type
   * @param origin
   */
  Setting(Class type, DatasetOrigin... origin) {
    this.origin = origin;
    Preconditions.checkArgument(type.equals(String.class)
      || type.equals(Integer.class)
      || type.equals(Boolean.class)
      || type.equals(LocalDate.class)
      || type.equals(URI.class)
      || type.isEnum(), "Unsupported type");
    this.type = type;
  }

}
