package life.catalogue.api.vocab;

import life.catalogue.api.vocab.area.Gazetteer;
import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.net.URI;
import java.time.LocalDate;
import java.util.UUID;

import static life.catalogue.api.vocab.DatasetOrigin.*;


/**
 * Dataset settings
 */
public enum Setting implements Described {

  CSV_DELIMITER(Character.class,
    "When importing data from text files this overrides the field delimiter character used.",
    EXTERNAL),

  CSV_QUOTE(Character.class,
    "When importing data from text files this overrides the quote character used.",
    EXTERNAL),

  CSV_QUOTE_ESCAPE(Character.class,
    "When importing data from text files this overrides the single character used for escaping quotes " +
      "inside an already quoted value. For example '\"' for CSV.",
    EXTERNAL),

  DISTRIBUTION_GAZETTEER(Gazetteer.class,
    "Overrides the gazetteer standard to use in all distribution interpretations for the dataset.",
    EXTERNAL, PROJECT),

  NOMENCLATURAL_CODE(NomCode.class,
    "The nomenclatural code followed in the dataset. It will be used mostly as a hint to format names accordingly. " +
      "If the dataset contains mixed data from multiple codes keep this field empty.",
    EXTERNAL, PROJECT),

  EXTINCT(Rank.class,
    "Sets the extinct flag for names in the dataset during imports. If given, it defines the highest rank of the names that will be flagged. " +
      "E.g. if extinct=genus, then all genera, species, subspecies and other lower taxa are flagged as extinct.",
    EXTERNAL, PROJECT),

  ENVIRONMENT(Environment.class,
    "Default value for the environment field in case it is not given explicitly.",
    EXTERNAL, PROJECT),

  REMATCH_DECISIONS(Boolean.class,
    "Informs the importer to rematch all decisions, i.e. decisions sensu stricto but also sectors and estimates. Defaults to false.",
    EXTERNAL, PROJECT),

  LOCK_METADATA(Boolean.class,
    "Informs the importer not to update any metadata from archives. Metadata will be locked and can only be edited manually.",
    EXTERNAL, PROJECT),

  MERGE_METADATA(Boolean.class,
    "Informs the importer to merge metadata found in archives with the existing metadata. " +
      "Metadata from the archive will take precedence, but any missing value would be taken from the current metadata in ChecklistBank.",
    EXTERNAL, PROJECT),

  RELEASE_CONFIG(URI.class,
    "URL to a yaml file with the configuration for a release.",
    PROJECT),

  XRELEASE_CONFIG(URI.class,
    "URL to a yaml file with the configuration for an extended release.",
    PROJECT),

  SOURCE_MAX_CONTAINER_AUTHORS(Integer.class,
    "Number of first authors from a project/release to use for the container authors of a source chapter-in-a-book citation. " +
      "A release takes it from its project unless it has its own. " +
      "It follows the number of project creators whenever these change, as a release appends source authors to them, " +
      "unless it was set manually to another value, which then sticks. " +
      "If not given the first 3 authors are used.",
    PROJECT, RELEASE),

  IMPORT_FREQUENCY(Frequency.class,
    "In continuous import mode the frequency the dataset is scheduled for imports.",
    EXTERNAL),

  DATA_ACCESS(URI.class,
    "URL of the archive that is downloaded for every import.",
    EXTERNAL),

  DATA_FORMAT(DataFormat.class,
    "Format of the archive. If not given it is detected from the files of the archive.",
    EXTERNAL, PROJECT),

  SYNC_SCHEDULER(Boolean.class, false,
    "Turns on/off the sync scheduling for a project. " +
      "Which sectors are being scheduled can be influenced by the sync scheduler sources setting.",
    PROJECT),

  SYNC_SCHEDULER_SOURCES(Integer.class, true,
    "List of sources to consider for sector scheduling. If empty, all sources are considered.",
    PROJECT),

  GBIF_SYNC_LOCK(Boolean.class, false,
    "If set to true the dataset metadata is locked and the GBIF registry sync will not be applied to the dataset.",
    EXTERNAL),

  DOI_RESOLUTION(DoiResolution.class, false,
    "Defines whether the importer makes use of Crossref to look up DOI metadata.",
    EXTERNAL, PROJECT),

  EPITHET_ADD_HYPHEN(Boolean.class, false,
    "If true replaces spaces in epithets with hyphens during interpretation, thus replacing multiple words with a single one.",
    EXTERNAL),

  PREFER_NAME_ATOMS(Boolean.class, false,
    "If true prefers the atomised name as given in several terms over the full scientificName string which needs parsing with our name parser. " +
      "If not given, atoms are preferred for ColDP and ACEF, but not for DwC-A.",
    EXTERNAL),

  DONT_INFER_RANKS(Boolean.class, false,
    "If true, rank inference from the name structure is disabled during imports. " +
      "Useful for datasets like NCBI/ENA taxonomy that contain placeholder names (e.g. \"cellular organisms\") " +
      "without explicit ranks, which would otherwise be incorrectly inferred as species. " +
      "When disabled, names without an explicit rank remain unranked.",
    EXTERNAL),

  BLOCK_MERGE_SYNCS(Boolean.class, false,
    "If true no merge syncs into the project will be allowed.",
    PROJECT),

  /**
   * <p>The identifier scope is taken from the configured scope registry for the project/dataset key,
   * or falls back to {@code clb{resolvedDatasetKey}}.
   * The configured matcher must already exist in the UsageMatcherFactory cache;
   * imports fail early otherwise.
   */
  ADD_IDENTIFIERS_FROM(String.class,
    "If set, every imported usage is matched against the specified dataset and the matched usageID is stored as an identifier on the imported usage. " +
      "Format: {integer}(LXR|LR)?, e.g. 1000 matches against dataset 1000 directly, " +
      "2207LR against the latest release of project 2207 and 2207LXR against the latest extended release of project 2207.",
    EXTERNAL, PROJECT);

  private final Class type;
  private final DatasetOrigin[] origin;
  private final boolean multiple;
  private final String description;

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

  /**
   * @return what the setting does, shown as help in the ChecklistBank UI
   */
  @Override
  public String getDescription() {
    return description;
  }

  Setting(Class type, String description, DatasetOrigin... origin) {
    this(type, false, description, origin);
  }

  /**
   * Use String, Character, Integer, Boolean, LocalDate, UUID, URI or a custom col enumeration class
   */
  Setting(Class type, boolean multiple, String description, DatasetOrigin... origin) {
    this.multiple = multiple;
    this.description = description;
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
