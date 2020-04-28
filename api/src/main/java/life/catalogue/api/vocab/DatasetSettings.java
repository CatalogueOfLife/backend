package life.catalogue.api.vocab;

import com.google.common.base.Preconditions;
import org.gbif.nameparser.api.NomCode;

import java.time.LocalDate;

public enum DatasetSettings {


  /**
   * When importing data from text files this overrides
   * the field delimiter character used
   */
  CSV_DELIMITER(String.class),

  /**
   * When importing data from text files this overrides
   * the quote character used
   */
  CSV_QUOTE(String.class),

  /**
   * When importing data from text files this overrides
   * the single character used for escaping quotes inside an already quoted value.
   * For example '"' for CSV
   */
  CSV_QUOTE_ESCAPE(String.class),

  /**
   * Overrides the gazetteer standard to use in all distribution interpretations for the dataset.
   */
  DISTRIBUTION_GAZETTEER(Gazetteer.class),

  /**
   * The nomenclatural code followed in the dataset.
   * It will be used mostly as a hint to format names accordingly.
   * If the dataset contains mixed data from multiple codes keep this field null.
   */
  NOMENCLATURAL_CODE(NomCode.class),

  /**
   * Setting that will inform the importer to rematch all decisions (decisions sensu strictu but also sectors and estimates)
   * Defaults to false
   */
  REMATCH_DECISIONS(Boolean.class),

  /**
   * Template used to build a new release title.
   */
  RELEASE_TITLE_TEMPLATE(String.class);

  private final Class type;

  public Class getType() {
    return type;
  }

  public boolean isEnum() {
    return type.isEnum();
  }

  /**
   * Use String, Integer, Boolean, LocalDate or a custom col enumeration class
   *
   * @param type
   */
  DatasetSettings(Class type) {
    Preconditions.checkArgument(type.equals(String.class)
      || type.equals(Integer.class)
      || type.equals(Boolean.class)
      || type.equals(LocalDate.class)
      || type.isEnum(), "Unsupported type");
    this.type = type;
  }

}
