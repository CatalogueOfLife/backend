package life.catalogue.api.vocab;

import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import com.google.common.base.Preconditions;

import static life.catalogue.api.vocab.DatasetOrigin.*;


/**
 * Dataset settings
 */
public enum Setting {

  /**
   * When importing data from text files this overrides
   * the field delimiter character used
   */
  CSV_DELIMITER(Character.class, EXTERNAL),

  /**
   * When importing data from text files this overrides
   * the quote character used
   */
  CSV_QUOTE(Character.class, EXTERNAL),

  /**
   * When importing data from text files this overrides
   * the single character used for escaping quotes inside an already quoted value.
   * For example '"' for CSV
   */
  CSV_QUOTE_ESCAPE(Character.class, EXTERNAL),

  /**
   * Overrides the gazetteer standard to use in all distribution interpretations for the dataset.
   */
  DISTRIBUTION_GAZETTEER(Gazetteer.class, EXTERNAL, PROJECT),

  /**
   * The nomenclatural code followed in the dataset.
   * It will be used mostly as a hint to format names accordingly.
   * If the dataset contains mixed data from multiple codes keep this field null.
   */
  NOMENCLATURAL_CODE(NomCode.class, EXTERNAL, PROJECT),

  /**
   * Default value for the extinct flag in case it is not given explicitly.
   */
  EXTINCT(Boolean.class, EXTERNAL, PROJECT),

  /**
   * Default value for the environment field in case it is not given explicitly.
   */
  ENVIRONMENT(Environment.class, EXTERNAL, PROJECT),

  /**
   * Setting that will inform the importer to rematch all decisions (decisions sensu strictu but also sectors and estimates)
   * Defaults to false
   */
  REMATCH_DECISIONS(Boolean.class, EXTERNAL, PROJECT),

  /**
   * Setting that will inform the importer not to update any metadata from archives.
   * Metadata will be locked and can only be edited manually.
   */
  LOCK_METADATA(Boolean.class, EXTERNAL, PROJECT),

  /**
   * Setting that will inform the importer to merge metadata found in archives with the existing metadata.
   * Metadata from the archive will take precedence, but any missing value would be taken from the current metadata in ChecklistBank.
   */
  MERGE_METADATA(Boolean.class, EXTERNAL, PROJECT),

  /**
   * Template used to build a new release alias.
   */
  RELEASE_ALIAS_TEMPLATE(String.class, PROJECT),

  /**
   * Template used to build a new release version.
   * See RELEASE_TITLE_TEMPLATE for usage.
   */
  RELEASE_VERSION_TEMPLATE(String.class, PROJECT),

  /**
   * If true a release will include as its authors all authors of all it's sources.
   */
  RELEASE_ADD_SOURCE_AUTHORS(Boolean.class, PROJECT),

  /**
   * If true a release will include as its authors all contributors of the project (not source contributors).
   */
  RELEASE_ADD_CONTRIBUTORS(Boolean.class, PROJECT),

  /**
   * If true a release will issue new DOIs to changed sources.
   */
  RELEASE_ISSUE_SOURCE_DOIS(Boolean.class, PROJECT),

  /**
   * If true a release will first delete all bare names from the project before it copies data.
   */
  RELEASE_REMOVE_BARE_NAMES(Boolean.class, PROJECT),

  /**
   * If true a release will prepare exports for the entire release in all common formats.
   */
  RELEASE_PREPARE_DOWNLOADS(Boolean.class, PROJECT),

  /**
   * URL to a yaml file with the configuration for an extended release.
   */
  XRELEASE_CONFIG(URI.class, PROJECT),

  /**
   * Number of first authors from a project/release to use for the container authors of a source chapter-in-a-book citation.
   * If not given all authors are used.
   */
  SOURCE_MAX_CONTAINER_AUTHORS(Integer.class, PROJECT, RELEASE),

  /**
   * In continuous import mode the frequency the dataset is scheduled for imports.
   */
  IMPORT_FREQUENCY(Frequency.class, EXTERNAL),

  DATA_ACCESS(URI.class, EXTERNAL),

  DATA_FORMAT(DataFormat.class, EXTERNAL, PROJECT),

  /**
   * Project defaults to be used for the sector.entities property
   */
  SECTOR_ENTITIES(EntityType.class, true, PROJECT),

  /**
   * Project defaults to be used for the sector.ranks property
   */
  SECTOR_RANKS(Rank.class, true, PROJECT),

  /**
   * Project defaults to be used for the sector.nameTypes property
   */
  SECTOR_NAME_TYPES(NameType.class, true, PROJECT),

  /**
   * Project defaults to be used for the sector.nameStatusExclusion property
   */
  SECTOR_NAME_STATUS_EXCLUSION(NomStatus.class, true, PROJECT),

  /**
   * Project defaults to be used for the sector.copyAccordingTo property.
   * Defaults to false.
   */
  SECTOR_COPY_ACCORDING_TO(Boolean.class, false, PROJECT),

  /**
   * If set to true the dataset metadata is locked and the gbif registry sync will not be applied to the dataset.
   */
  GBIF_SYNC_LOCK(Boolean.class, false, EXTERNAL),

  /**
   * Defines wheter the importer makes use of Crossref to lookup DOI metadata.
   */
  DOI_RESOLUTION(DoiResolution.class, false, EXTERNAL, PROJECT),

  /**
   * If true replaces spaces in epithets with hyphens during interpretation, thus replacing multiple words with a single one.
   */
  EPITHET_ADD_HYPHEN(Boolean.class, false, EXTERNAL),

  /**
   * If true prefers the atomised name as given in several terms over the full scientificName string which needs parsing with our name parser.
   * Default depends on the
   */
  PREFER_NAME_ATOMS(Boolean.class, false, EXTERNAL);

  private final Class type;
  private final DatasetOrigin[] origin;
  private final boolean multiple;

  public Class getType() {
    return type;
  }

  public DatasetOrigin[] getOrigin() {
    return origin;
  }

  public boolean isEnum() {
    return type.isEnum();
  }

  public boolean isMultiple() {
    return multiple;
  }

  Setting(Class type, DatasetOrigin... origin) {
    this(type, false, origin);
  }

  /**
   * Use String, Character, Integer, Boolean, LocalDate, UUID, URI or a custom col enumeration class
   */
  Setting(Class type, boolean multiple, DatasetOrigin... origin) {
    this.multiple = multiple;
    this.origin = origin;
    Preconditions.checkArgument(type.equals(String.class)
      || type.equals(Character.class)
      || type.equals(Integer.class)
      || type.equals(Boolean.class)
      || type.equals(LocalDate.class)
      || type.equals(UUID.class)
      || type.equals(URI.class)
      || type.isEnum(), "Unsupported type"); // see SettingsDeserializer
    this.type = type;
  }

}
