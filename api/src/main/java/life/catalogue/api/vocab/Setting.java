package life.catalogue.api.vocab;

import life.catalogue.api.vocab.area.Gazetteer;
import org.gbif.nameparser.api.NameType;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import static life.catalogue.api.vocab.DatasetOrigin.*;


/**
 * Dataset settings
 */
public enum Setting {

  /**
   * When importing data from text files this overrides
   * the field delimiter character used
   */
  CSV_DELIMITER(Character.class, DatasetOrigin.EXTERNAL),

  /**
   * When importing data from text files this overrides
   * the quote character used
   */
  CSV_QUOTE(Character.class, DatasetOrigin.EXTERNAL),

  /**
   * When importing data from text files this overrides
   * the single character used for escaping quotes inside an already quoted value.
   * For example '"' for CSV
   */
  CSV_QUOTE_ESCAPE(Character.class, DatasetOrigin.EXTERNAL),

  /**
   * Overrides the gazetteer standard to use in all distribution interpretations for the dataset.
   */
  DISTRIBUTION_GAZETTEER(Gazetteer.class, DatasetOrigin.EXTERNAL, DatasetOrigin.PROJECT),

  /**
   * The nomenclatural code followed in the dataset.
   * It will be used mostly as a hint to format names accordingly.
   * If the dataset contains mixed data from multiple codes keep this field null.
   */
  NOMENCLATURAL_CODE(NomCode.class, DatasetOrigin.EXTERNAL, DatasetOrigin.PROJECT),

  /**
   * The setting is used to set the extinct flag for names in the dataset during imports.
   * If given, it defines the highest rank of the names that will be flagged.
   * E.g. if extinct=genus, then all genera, species, subspecies and other lower taxa are flagged as extinct.
   */
  EXTINCT(Rank.class, DatasetOrigin.EXTERNAL, DatasetOrigin.PROJECT),

  /**
   * Default value for the environment field in case it is not given explicitly.
   */
  ENVIRONMENT(Environment.class, DatasetOrigin.EXTERNAL, DatasetOrigin.PROJECT),

  /**
   * Setting that will inform the importer to rematch all decisions (decisions sensu strictu but also sectors and estimates)
   * Defaults to false
   */
  REMATCH_DECISIONS(Boolean.class, DatasetOrigin.EXTERNAL, DatasetOrigin.PROJECT),

  /**
   * Setting that will inform the importer not to update any metadata from archives.
   * Metadata will be locked and can only be edited manually.
   */
  LOCK_METADATA(Boolean.class, DatasetOrigin.EXTERNAL, DatasetOrigin.PROJECT),

  /**
   * Setting that will inform the importer to merge metadata found in archives with the existing metadata.
   * Metadata from the archive will take precedence, but any missing value would be taken from the current metadata in ChecklistBank.
   */
  MERGE_METADATA(Boolean.class, DatasetOrigin.EXTERNAL, DatasetOrigin.PROJECT),

  /**
   * URL to a yaml file with the configuration for an release.
   */
  RELEASE_CONFIG(URI.class, DatasetOrigin.PROJECT),

  /**
   * URL to a yaml file with the configuration for an extended release.
   */
  XRELEASE_CONFIG(URI.class, DatasetOrigin.PROJECT),

  /**
   * Number of first authors from a project/release to use for the container authors of a source chapter-in-a-book citation.
   * If not given all authors are used.
   */
  SOURCE_MAX_CONTAINER_AUTHORS(Integer.class, DatasetOrigin.PROJECT, DatasetOrigin.RELEASE),

  /**
   * In continuous import mode the frequency the dataset is scheduled for imports.
   */
  IMPORT_FREQUENCY(Frequency.class, DatasetOrigin.EXTERNAL),

  DATA_ACCESS(URI.class, DatasetOrigin.EXTERNAL),

  DATA_FORMAT(DataFormat.class, DatasetOrigin.EXTERNAL, DatasetOrigin.PROJECT),

  /**
   * Project defaults to be used for the sector.entities property
   */
  SECTOR_ENTITIES(EntityType.class, true, DatasetOrigin.PROJECT),

  /**
   * Project defaults to be used for the sector.ranks property
   */
  SECTOR_RANKS(Rank.class, true, DatasetOrigin.PROJECT),

  /**
   * Project defaults to be used for the sector.nameTypes property
   */
  SECTOR_NAME_TYPES(NameType.class, true, DatasetOrigin.PROJECT),

  /**
   * Project defaults to be used for the sector.nameStatusExclusion property
   */
  SECTOR_NAME_STATUS_EXCLUSION(NomStatus.class, true, DatasetOrigin.PROJECT),

  /**
   * Project defaults to be used for the sector.copyAccordingTo property.
   * Defaults to false.
   */
  SECTOR_COPY_ACCORDING_TO(Boolean.class, false, DatasetOrigin.PROJECT),

  /**
   * Flag to turn on/off the sync scheduling for a project.
   * Which sectors are being scheduled can be influenced by the SYNC_SCHEDULER_SOURCES setting.
   */
  SYNC_SCHEDULER(Boolean.class, false, DatasetOrigin.PROJECT),

  /**
   * List of sources to consider for sector scheduling.
   * If empty, all sources are considered.
   */
  SYNC_SCHEDULER_SOURCES(Integer.class, true, DatasetOrigin.PROJECT),

  /**
   * Project defaults to be used for the sector.removeOrdinals property
   * Defaults to false.
   */
  SECTOR_REMOVE_ORDINALS(Boolean.class, false, DatasetOrigin.PROJECT),

  /**
   * If set to true the dataset metadata is locked and the gbif registry sync will not be applied to the dataset.
   */
  GBIF_SYNC_LOCK(Boolean.class, false, DatasetOrigin.EXTERNAL),

  /**
   * Defines wheter the importer makes use of Crossref to lookup DOI metadata.
   */
  DOI_RESOLUTION(DoiResolution.class, false, DatasetOrigin.EXTERNAL, DatasetOrigin.PROJECT),

  /**
   * If true replaces spaces in epithets with hyphens during interpretation, thus replacing multiple words with a single one.
   */
  EPITHET_ADD_HYPHEN(Boolean.class, false, DatasetOrigin.EXTERNAL),

  /**
   * If true prefers the atomised name as given in several terms over the full scientificName string which needs parsing with our name parser.
   * Default depends on the
   */
  PREFER_NAME_ATOMS(Boolean.class, false, DatasetOrigin.EXTERNAL),

  /**
   * If true no merge syncs into the project will be allowed.
   */
  BLOCK_MERGE_SYNCS(Boolean.class, false, DatasetOrigin.PROJECT),

  /**
   * If null or true creates implicit names during syncs.
   */
  SECTOR_CREATE_IMPLICIT_NAMES(Boolean.class, false, DatasetOrigin.PROJECT);

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
    if(!(type.equals(String.class)
      || type.equals(Character.class)
      || type.equals(Integer.class)
      || type.equals(Boolean.class)
      || type.equals(LocalDate.class)
      || type.equals(UUID.class)
      || type.equals(URI.class)
      || type.isEnum())
    ) {
      throw new IllegalArgumentException("Unsupported type"); // see SettingsDeserializer
    }
    this.type = type;
  }

}
