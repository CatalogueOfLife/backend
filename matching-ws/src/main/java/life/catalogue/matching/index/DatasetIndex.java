package life.catalogue.matching.index;

import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.matching.Main;
import life.catalogue.matching.model.*;
import life.catalogue.matching.util.IOUtil;
import life.catalogue.matching.util.IUCNUtils;
import life.catalogue.matching.util.LuceneUtils;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.PostConstruct;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.*;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.MMapDirectory;
import org.apache.lucene.util.BytesRef;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.extern.slf4j.Slf4j;

import static life.catalogue.matching.util.IndexConstants.*;

/**
 * Represents an index of a dataset.
 * This class provides the entry point search methods for an index, and
 * methods to retrieve metadata about the index.
 *
 * This class has knowledge of Lucene indexes and how to query them.
 */
@Slf4j
@Service
public class DatasetIndex {

  private IndexSearcher searcher;
  private Map<Dataset, IndexSearcher> identifierSearchers = new HashMap<>();
  private Map<Dataset, IndexSearcher> ancillarySearchers = new HashMap<>();

  protected static final ScientificNameAnalyzer scientificNameAnalyzer = new ScientificNameAnalyzer();

  protected static final ObjectMapper MAPPER = new ObjectMapper();

  @Value("${index.path:/data/matching-ws/index}")
  String indexPath;

  @Value("${working.dir:/tmp/}")
  String workingDir;

  @Autowired
  IOUtil ioUtil;

  private boolean isInitialised = false;

  public boolean getIsInitialised() {
    return isInitialised;
  }

  final static NameUsageMatch NO_MATCH = NameUsageMatch.builder()
    .diagnostics(
      NameUsageMatch.Diagnostics.builder()
        .confidence(100)
        .matchType(MatchType.NONE)
        .build())
    .build();

  /** Attempts to read the index from disk if it exists. */
  @PostConstruct
  void init() {

    MAPPER.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    final String mainIndexPath = getMainIndexPath();
    final Map<Integer, Dataset> prefixMapping = loadPrefixMapping();

    if (new File(mainIndexPath).exists()) {
      log.info("Loading lucene index from {} ....", mainIndexPath);
      try {
        MMapDirectory mMapDirectory = new MMapDirectory(Path.of(mainIndexPath));
        mMapDirectory.setPreload(true);
        initWithDir(mMapDirectory);
      } catch (IOException e) {
        log.warn("Cannot open lucene index. Index not available", e);
      }
      log.info("Loaded lucene index from {}", mainIndexPath);

      // load identifier indexes
      log.debug("Loading identifier indexes from {}", IDENTIFIERS_DIR);
      this.identifierSearchers = initialiseAdditionalIndexes(IDENTIFIERS_DIR, prefixMapping);

      // load ancillary indexes
      log.debug("Loading ancillary indexes from {}", IDENTIFIERS_DIR);
      this.ancillarySearchers = initialiseAdditionalIndexes(ANCILLARY_DIR, prefixMapping);

      this.isInitialised = true;
      log.debug("All indexes initialised");
    } else {
      log.warn("Main lucene index not found at {}", mainIndexPath);
    }
  }

  public void reinit() {

    final String mainIndexPath = getMainIndexPath();
    if (new File(mainIndexPath).exists()) {
      log.info("Reinitialising lucene index from {}", mainIndexPath);
      try {
        initWithDir(new MMapDirectory(Path.of(mainIndexPath)));
      } catch (IOException e) {
        log.warn("Cannot open lucene index. Index not available", e);
      }
      this.isInitialised = true;
    } else {
      log.warn("Unable to reinitialise. Main lucene index not found at {}", mainIndexPath);
    }
  }

  private HashMap<Dataset, IndexSearcher> initialiseAdditionalIndexes(String directoryName, Map<Integer, Dataset> prefixMapping) {
    HashMap<Dataset, IndexSearcher> searchers = new HashMap<>();
    if (Path.of(indexPath + "/" + directoryName).toFile().exists()) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(indexPath + "/" + directoryName))) {
        for (Path entry : stream) {
          if (Files.isDirectory(entry)) {
            try {
              Directory ancillaryDir = new MMapDirectory(entry);
              DirectoryReader reader = DirectoryReader.open(ancillaryDir);
              ObjectMapper mapper = new ObjectMapper();
              Dataset dataset = mapper.readValue(new FileReader(entry.resolve(METADATA_JSON).toFile()),
                Dataset.class);

              // apply prefix mapping
              Dataset prefixDatasetConfig = prefixMapping.get(dataset.getClbKey());
              if (prefixDatasetConfig != null) {
                dataset.setPrefix(prefixDatasetConfig.getPrefix());
                dataset.setPrefixMapping(prefixDatasetConfig.getPrefixMapping());
                dataset.setRemovePrefixForMatching(prefixDatasetConfig.getRemovePrefixForMatching());
              }

              searchers.put(dataset, new IndexSearcher(reader));
            } catch (org.apache.lucene.index.IndexNotFoundException e){
              log.warn("Index not found at {} lucene index {}", directoryName, entry);
            } catch (IOException e) {
              log.warn("Cannot open {} lucene index {}", directoryName, entry, e);
            }
          }
        }
      } catch (IOException e) {
        log.error("Cannot read {} index directory", directoryName, e);
      }
    } else {
      log.info("additional indexes not found at {}", indexPath + "/" + directoryName);
    }
    return searchers;
  }

  private Map<Integer, Dataset> loadPrefixMapping() {
    ClassLoader classLoader = Main.class.getClassLoader();

    try (InputStream inputStream = classLoader.getResourceAsStream(DATASETS_JSON)) {
      log.debug("Loading identifier prefix mapping");
      if (inputStream == null) {
        return Map.of();
      }

      ObjectMapper mapper = new ObjectMapper();
      Dataset[] datasets = mapper.readValue(inputStream, Dataset[].class);

      return Arrays.stream(datasets)
        .peek(dataset -> log.debug("Loaded dataset {} [{}]", dataset.getTitle(), dataset.getClbKey()))
        .collect(Collectors.toMap(Dataset::getClbKey, dataset -> dataset));
    } catch (IOException e) {
      log.warn("Cannot read dataset prefix mapping file", e);
      return Map.of();
    }
  }

  private @NotNull String getMainIndexPath() {
    return indexPath + "/main";
  }

  void initWithDir(Directory indexDirectory) {
    try {
      DirectoryReader reader = DirectoryReader.open(indexDirectory);
      this.searcher = new IndexSearcher(reader);
      this.isInitialised = true;
    } catch (IOException e) {
      log.warn("Cannot open lucene index. Index not available", e);
    }
  }

  public void initWithIdentifierDir(Dataset dataset, Directory indexDirectory) {
    try {
      DirectoryReader reader = DirectoryReader.open(indexDirectory);
      IndexSearcher searcher = new IndexSearcher(reader);
      this.identifierSearchers.put(dataset, searcher);
    } catch (IOException e) {
      log.warn("Cannot open lucene index. Index not available", e);
    }
  }

  /**
   * Returns the metadata of the index. This includes the number of taxa, the size on disk, the
   * dataset title and key, and the build information.
   * @return IndexMetadata
   */
  public APIMetadata getAPIMetadata(){

    APIMetadata metadata = new APIMetadata();
    // get size on disk
    Path directoryPath = Path.of(indexPath);
    if (Files.exists(directoryPath)) {

      try {
        BasicFileAttributes attributes = Files.readAttributes(directoryPath, BasicFileAttributes.class);
        Instant creationTime = attributes.creationTime().toInstant();
        DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
          .withZone(ZoneId.systemDefault());
        String formattedCreationTime = dateFormatter.format(creationTime);
        metadata.setCreated(formattedCreationTime);
      } catch (IOException e) {
        log.error("Cannot read index directory attributes", e);
      }

      metadata.setMainIndex(getIndexMetadata(indexPath + "/" + MAIN_INDEX_DIR, getSearcher(), true));

      for (Dataset dataset : identifierSearchers.keySet()) {
        metadata.getIdentifierIndexes().add(
          getIndexMetadata(indexPath + "/" + IDENTIFIERS_DIR + "/" + dataset.getClbKey(),
            identifierSearchers.get(dataset), false));
      }

      for (Dataset dataset : ancillarySearchers.keySet()) {
        metadata.getAncillaryIndexes().add(
          getIndexMetadata(indexPath + "/" + ANCILLARY_DIR + "/" + dataset.getClbKey(),
            ancillarySearchers.get(dataset), false));
      }
    }

    getGitInfo().ifPresent(metadata::setBuildInfo);
    return metadata;
  }

  public static List<String> distinctValuesForField(String field, String indexPath) throws Exception {

    List<String> distinctValues = new ArrayList<>();
    FSDirectory directory = FSDirectory.open(Paths.get(indexPath));

    try (DirectoryReader directoryReader = DirectoryReader.open(directory)) {

      // Get the field terms
      for (LeafReaderContext leafContext : directoryReader.leaves()) {
        LeafReader leafReader = leafContext.reader();
        Terms terms = leafReader.terms(field);

        if (terms != null) {
          TermsEnum termsEnum = terms.iterator();
          BytesRef byteRef;
          while ((byteRef = termsEnum.next()) != null) {
            String termValue = byteRef.utf8ToString();
            distinctValues.add(termValue);
          }
        }
      }
    }
    return distinctValues;
  }

  /**
   * Returns the metadata of the index. This includes the number of taxa, the size on disk, the
   * dataset title and key, and the build information.
   * @return IndexMetadata
   */
  private IndexMetadata getIndexMetadata(String indexPath, IndexSearcher searcher, boolean isMain){

    IndexMetadata metadata = new IndexMetadata();

    // get size on disk
    Path directoryPath = Path.of(indexPath);
    try {
      long totalSize = 0;
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(directoryPath)) {
        for (Path entry : stream) {
          if (!Files.isDirectory(entry)) {
            totalSize += Files.size(entry);
          }
        }
      }
      metadata.setSizeInMB((long) (totalSize / (1024.0 * 1024.0)));

    } catch (IOException e) {
      log.error("Cannot read index directory attributes", e);
    }

    Map<String, Object> datasetInfo = getDatasetInfo(indexPath);
    metadata.setDatasetTitle((String) datasetInfo.getOrDefault("datasetTitle", null));
    metadata.setClbDatasetKey((String) datasetInfo.getOrDefault("clbKey", null));
    metadata.setDatasetKey((String) datasetInfo.getOrDefault("datasetKey", null));

    // number of taxa
    metadata.setNameUsageCount(Long.parseLong((String) datasetInfo.getOrDefault("taxonCount", 0)));
    if (!isMain) {
      metadata.setMatchesToMain(Long.parseLong((String) datasetInfo.getOrDefault("matchesToMainIndex", 0)));
    }

    try {
      Map<String, Long> rankCounts = new LinkedHashMap<>();
      distinctValuesForField(FIELD_RANK, indexPath).stream().sorted( (a, b) -> Rank.valueOf(a.toUpperCase()).ordinal() - Rank.valueOf(b.toUpperCase()).ordinal()
      ).forEach(rank -> {
        try {
          rankCounts.put(rank, getCountForRank(searcher, rank));
        } catch (IOException e) {
          log.error("Cannot read index information", e);
        }
      });
      metadata.setNameUsageByRankCount(rankCounts);
    } catch (Exception e) {
      log.error("Cannot read index information", e);
    }
    return metadata;
  }

  /**
   * Reads the git information from the git.json file in the working directory.
   * @return Optional of BuildInfo
   */
  private Optional<BuildInfo> getGitInfo() {
    ObjectMapper mapper = new ObjectMapper();
    final String filePath = workingDir + GIT_JSON;
    try {
      if (new File(filePath).exists()) {
        // Read JSON file and parse to JsonNode
        JsonNode rootNode = mapper.readTree(new File(filePath));

        // Navigate to the author node
        String sha = rootNode.path("sha").asText();
        String url = rootNode.path("url").asText();
        String html_url = rootNode.path("html_url").asText();
        String message = rootNode.path("commit").path("message").asText();
        JsonNode authorNode = rootNode.path("commit").path("author");

        // Retrieve author information
        String name = authorNode.path("name").asText();
        String email = authorNode.path("email").asText();
        String date = authorNode.path("date").asText();

        return Optional.of(BuildInfo.builder()
          .sha(sha)
          .url(url)
          .html_url(html_url)
          .message(message)
          .name(name)
          .email(email)
          .date(date)
          .build());
      } else {
        log.warn("Git info not found at {}", filePath);
      }
    } catch (IOException e) {
      log.error("Cannot read index git information", e);
    }
    return Optional.empty();
  }

  /**
   * Reads the dataset information from the dataset.json file in the working directory.
   * @param indexPath the path to the dataset index
   * @return Map of dataset information
   */
  public Map<String, Object> getDatasetInfo(String indexPath) {
    ObjectMapper mapper = new ObjectMapper();
    String filePath = indexPath + "/" + METADATA_JSON;

    try {
      if (new File(filePath).exists()){
        log.debug("Loading dataset info from {}", filePath);
        // Read JSON file and parse to JsonNode
        JsonNode rootNode = mapper.readTree(new File(filePath));
        // Navigate to the author node
        String clbKey = rootNode.path("key").asText();
        String datasetTitle = rootNode.path("title").asText();
        String datasetKey = rootNode.path("gbifKey").asText();
        String taxonCount = rootNode.path("taxonCount").asText();
        String matchesToMainIndex = rootNode.path("matchesToMainIndex").asText();
        return Map.of(
          "clbKey", clbKey,
          "datasetTitle", datasetTitle,
          "datasetKey", datasetKey,
          "taxonCount", taxonCount,
          "matchesToMainIndex", matchesToMainIndex
        );
      } else {
        log.warn("Dataset info not found at {}", filePath);
      }
    } catch (IOException e) {
      log.error("Cannot read index dataset information", e);
    }
    return Map.of();
  }

  private long getCountForRank(IndexSearcher searcher, String rank) throws IOException {
    Query query = new TermQuery(new Term(FIELD_RANK, rank));
    return searcher.search(query, new TotalHitCountCollectorManager());
  }

  /**
   * Creates a new in memory lucene index from the given list of usages.
   * @param mainIndexDir the directory of the main index
   * @return DatasetIndex
   */
  public static DatasetIndex newDatasetIndex(Directory mainIndexDir)  {
    DatasetIndex datasetIndex = new DatasetIndex();
    datasetIndex.initWithDir(mainIndexDir);
    datasetIndex.ioUtil = new IOUtil();
    return datasetIndex;
  }

  private IndexSearcher getSearcher() {
    if (searcher == null) {
      throw new IllegalStateException("Lucene index not loaded");
    }
    return searcher;
  }

  /**
   * Lookup a name usage by its usage key.
   *
   * @param usageKey the usage key to lookup
   * @return NameUsageMatch
   */
  public NameUsageMatch matchByUsageKey(String usageKey) {
    return matchByKey(usageKey, this::getByUsageKey);
  }

  private Optional<Document> getByUsageKey(String usageKey) {
    Query query = new TermQuery(new Term(FIELD_ID, usageKey));
    try {
      TopDocs docs = getSearcher().search(query, 3);
      if (docs.totalHits.value > 0) {
        return Optional.of(getSearcher().storedFields().document(docs.scoreDocs[0].doc));
      }
    } catch (IOException e) {
      log.error("Cannot load usage {} from lucene index", usageKey, e);
    }
    return Optional.empty();
  }

  private NameUsageMatch matchByKey(String key, Function<String, Optional<Document>> func){

    Optional<Document> optDoc = func.apply(key);
    if (optDoc.isPresent()) {
      Document doc = optDoc.get();
      NameUsageMatch match = fromDoc(doc);
      match.getDiagnostics().setConfidence(100);
      match.getDiagnostics().setMatchType(MatchType.EXACT);
      return match;
    } else {
      log.warn("No usage {} found in lucene index", key);
      return NO_MATCH;
    }
  }

  /**
   * Matches an external ID. Intended for debug purposes only, to quickly
   * check whether ids are present and joined to main index or not.
   *
   * @param identifier the identifier to match
   * @return List of ExternalID
   */
  public List<ExternalID> lookupJoins(@NotNull String identifier)  {
    List<ExternalID> results = new ArrayList<>();

    try {
      // if join indexes are present, add them to the match
      if (identifierSearchers != null && !identifierSearchers.isEmpty()) {
        for (Dataset dataset : identifierSearchers.keySet()) {
          // find the index and search it
          IndexSearcher identifierSearcher = identifierSearchers.get(dataset);
          Query identifierQuery = new TermQuery(new Term(FIELD_JOIN_ID, identifier));
          TopDocs identifierDocs = identifierSearcher.search(identifierQuery, 1000);

          if (identifierDocs.totalHits.value > 0) {
            Document identifierDoc = identifierSearcher.storedFields().document(identifierDocs.scoreDocs[0].doc);
            results.add(toExternalID(identifierDoc, dataset));
          }
        }
      }
    } catch (IOException e) {
      log.error("Problem querying external ID indexes with {}", identifier, e);
    }
    // no indexes available
    return results;
  }


  /**
   * Matches an external ID. Intended for debug purposes only, to quickly
   * check whether ids are present and joined to main index or not.
   *
   * @param identifier the identifier to match
   * @return List of ExternalID
   */
  public List<ExternalID> lookupIdentifier(@NotNull String identifier)  {
    List<ExternalID> results = new ArrayList<>();

    try {
      // if join indexes are present, add them to the match
      if (identifierSearchers != null && !identifierSearchers.isEmpty()) {
        for (Dataset dataset : identifierSearchers.keySet()) {

          // if configured, remove the prefix
          if (dataset.getRemovePrefixForMatching()){
            identifier = identifier.replace(dataset.getPrefix(), "");
          }

          // find the index and search it
          IndexSearcher identifierSearcher = identifierSearchers.get(dataset);
          Query identifierQuery = new TermQuery(new Term(FIELD_ID, identifier));
          TopDocs identifierDocs = identifierSearcher.search(identifierQuery, 3);

          if (identifierDocs.totalHits.value > 0) {
            Document identifierDoc = identifierSearcher.storedFields().document(identifierDocs.scoreDocs[0].doc);
            results.add(toExternalID(identifierDoc, dataset));
          }
        }
      }
    } catch (IOException e) {
      log.error("Problem querying external ID indexes with {}", identifier, e);
    }
    // no indexes available
    return results;
  }

  /**
   * Matches an external ID. Intended for debug purposes only, to quickly
   * check if ids are present and joined to main index or not.
   *
   * @param datasetID the datasetKey to match
   * @param identifier the identifier to match
   * @return List of ExternalID
   */
  public List<ExternalID> lookupIdentifier(@NotNull String datasetID, @NotNull String identifier)  {
    return lookupIdentifier(datasetID, identifier, identifierSearchers);
  }

  /**
   * Matches an external ID. Intended for debug purposes only, to quickly
   * check if ids are present and joined to main index or not.
   *
   * @param datasetID the datasetKey to match
   * @param identifier the identifier to match
   * @return List of ExternalID
   */
  public List<ExternalID> lookupAncillary(@NotNull String datasetID, @NotNull String identifier)  {
    return lookupIdentifier(datasetID, identifier, ancillarySearchers);
  }

  /**
   * Matches an external ID. Intended for debug purposes only, to quickly
   * check if ids are present and joined to main index or not.
   *
   * @param datasetID the datasetKey to match
   * @param identifier the identifier to match
   * @return List of ExternalID
   */
  public List<ExternalID> lookupIdentifier(@NotNull String datasetID, @NotNull String identifier,  Map<Dataset, IndexSearcher> searchers)  {
    List<ExternalID> results = new ArrayList<>();

    try {
      // if join indexes are present, add them to the match
      if (searchers != null && !searchers.isEmpty()) {
        for (Dataset dataset : searchers.keySet()) {

          // use the prefix mapping
          if (dataset.getClbKey().toString().equals(datasetID) || (dataset.getDatasetKey() != null && dataset.getDatasetKey().equals(datasetID))) {

            // if configured, remove the prefix
            if (dataset.getRemovePrefixForMatching()){
              identifier = identifier.replace(dataset.getPrefix(), "");
            }

            // find the index and search it
            IndexSearcher searcher = searchers.get(dataset);
            Query identifierQuery = new TermQuery(new Term(FIELD_ID, identifier));
            TopDocs identifierDocs = searcher.search(identifierQuery, 3);

            if (identifierDocs.totalHits.value > 0) {
              Document identifierDoc = searcher.storedFields().
                document(identifierDocs.scoreDocs[0].doc);

              results.add(toExternalID(identifierDoc, dataset));
            }
          }
        }
      }
    } catch (IOException e) {
      log.error("Problem querying external ID indexes with {}", identifier, e);
    }
    // no indexes available
    return results;
  }

  private static ExternalID toExternalID(Document doc, Dataset dataset) {
    return ExternalID.builder()
      .id(doc.get(FIELD_ID))
      .clbDatasetKey(dataset.getClbKey().toString())
      .datasetKey(dataset.getDatasetKey())
      .datasetTitle(dataset.getTitle())
      .scientificName(doc.get(FIELD_SCIENTIFIC_NAME))
      .rank(doc.get(FIELD_RANK))
      .parentID(doc.get(FIELD_PARENT_ID))
      .status(doc.get(FIELD_STATUS))
      .mainIndexID(doc.get(FIELD_JOIN_ID))
      .build();
  }

  /**
   * Matches an external ID
   * @param suppliedKey the external ID to match
   * @param notFoundIssue the issue to add if the identifier is not found
   * @param ignoredIssue the issue to add if the identifier is ignored
   * @return NameUsageMatch
   */
  public NameUsageMatch matchByExternalKey(String suppliedKey, MatchingIssue notFoundIssue, MatchingIssue ignoredIssue) {

    // if join indexes are present, add them to the match
    if (identifierSearchers != null && !identifierSearchers.isEmpty()){
      try {
        for (Dataset dataset: identifierSearchers.keySet()){

          Optional<String> key = extractKeyForSearch(suppliedKey, dataset);
          if (key.isEmpty()) continue;

          // find the index and search it
          IndexSearcher identifierSearcher = identifierSearchers.get(dataset);
          Query identifierQuery = new TermQuery(new Term(FIELD_ID, key.get()));
          TopDocs identifierDocs = identifierSearcher.search(identifierQuery, 3);

          if (identifierDocs.totalHits.value > 0) {

            // check for multiple matches - indicates duplicates in index
            if (identifierDocs.totalHits.value > 1) {
              log.warn("Multiple matches found for identifier {} in dataset {}", key, dataset.getClbKey());
              return noMatch(ignoredIssue, "Multiple matches found for the identifier");
            }

            Document identifierDoc = identifierSearcher.storedFields().document(identifierDocs.scoreDocs[0].doc);

            final String joinID = identifierDoc.get(FIELD_JOIN_ID);
            Query getByIDQuery = new TermQuery(new Term(FIELD_ID, joinID));
            TopDocs docs = getSearcher().search(getByIDQuery, 3);
            if (docs.totalHits.value > 0) {
              // success - build the name match
              NameUsageMatch idMatch = fromDoc(getSearcher().storedFields().document(docs.scoreDocs[0].doc));
              idMatch.getDiagnostics().setConfidence(100);
              idMatch.getDiagnostics().setMatchType(MatchType.EXACT);
              return idMatch;
            } else {
              log.warn("Cannot find usage {} in main lucene index after " +
                "finding it in identifier index for {}", key, dataset.getClbKey());
              return noMatch(ignoredIssue, "Identifier recognised in {}, but not matching in main index" + dataset.getClbKey());
            }
          } else {
            log.info("Identifier {} not found in dataset {}", key, dataset.getClbKey());
            return noMatch(notFoundIssue, "Identifier not found");
          }
        }
      } catch (IOException e) {
        log.error("Problem querying external ID indexes with {}", suppliedKey, e);
      }
    }

    // no indexes available
    return NO_MATCH;
  }

  public static Optional<String> extractKeyForSearch(String key, Dataset dataset) {
    if (!hasRecognisedPrefix(key, dataset)) {
      // only search indexes with matching prefixes
      return Optional.empty();
    }

    // use the prefix mapping
    if (dataset.getPrefixMapping() != null && !dataset.getPrefixMapping().isEmpty()) {
      for (String prefix : dataset.getPrefixMapping()) {
        if (key.startsWith(prefix)) {
          key = key.replace(prefix, dataset.getPrefix());
        }
      }
    }

    // if configured, remove the prefix
    if (dataset.getRemovePrefixForMatching() != null && dataset.getRemovePrefixForMatching()){
      key = key.replace(dataset.getPrefix(), "");
    }
    log.debug("Searching for identifier {} in dataset {}", key, dataset.getClbKey());
    return Optional.of(key);
  }

  private static boolean hasRecognisedPrefix(String key, Dataset dataset) {
    if (dataset.getPrefix() == null){
      return false;
    }
    if (key.startsWith(dataset.getPrefix()))
      return true;
    return dataset.getPrefixMapping().stream().anyMatch(key::startsWith);
  }

  private static NameUsageMatch noMatch(MatchingIssue issue, String note) {
    return NameUsageMatch.builder()
      .diagnostics(
        NameUsageMatch.Diagnostics.builder()
          .matchType(MatchType.NONE)
          .issues(new ArrayList<MatchingIssue>(List.of(issue)))
          .note(note)
          .build())
      .synonym(false)
      .build();
  }

  /**
   * Converts a lucene document into a NameUsageMatch object.
   *
   * @param doc the lucene document to convert to a NameUsageMatch
   * @return NameUsageMatch
   */
  private NameUsageMatch fromDoc(Document doc) {

    boolean synonym = false;
    NameUsageMatch u = NameUsageMatch.builder().build();
    u.setDiagnostics(NameUsageMatch.Diagnostics.builder().build());

    u.setUsage(constructUsage(doc));

    if (doc.get(FIELD_ACCEPTED_ID) != null) {
      synonym = true;
      Optional<Document> accDocOpt = getByUsageKey(doc.get(FIELD_ACCEPTED_ID));
      if (accDocOpt.isPresent()) {
        Document accDoc = accDocOpt.get();
        u.setAcceptedUsage(constructUsage(accDoc));
      }
    }

    if (doc.get(FIELD_LEFT_NESTED_SET_ID) != null) {
      u.setLeft(Long.parseLong(doc.get(FIELD_LEFT_NESTED_SET_ID)));
      u.setRight(Long.parseLong(doc.get(FIELD_RIGHT_NESTED_SET_ID)));
    }

    ioUtil.deserialiseField(doc, FIELD_CLASSIFICATION, StoredClassification.class)
      .map(StoredClassification::getNames)
      .ifPresent(names -> u.setClassification(
        names.stream()
          .map(r -> NameUsageMatch.RankedName.builder()
            .key(r.getKey())
            .rank(Rank.valueOf(r.getRank().toUpperCase()))
            .canonicalName(r.getName())
            .name(r.getName())
            .build()
          )
          .collect(Collectors.toList())
      ));

    u.setSynonym(synonym);

    // if ancillary join indexes are present, add them to the match
    for (Dataset dataset: ancillarySearchers.keySet()){
      IndexSearcher ancillarySearcher = ancillarySearchers.get(dataset);
      Query query = new TermQuery(
        new Term(FIELD_JOIN_ID, doc.get(FIELD_ID))
      );
      try {
        TopDocs docs = ancillarySearcher.search(query, 3);
        if (docs.totalHits.value > 0) {
          Document ancillaryDoc = ancillarySearcher.storedFields().document(docs.scoreDocs[0].doc);
          NameUsageMatch.Status ancillaryStatus = new NameUsageMatch.Status();
          ancillaryStatus.setStatus(ancillaryDoc.get(FIELD_CATEGORY));
          String formattedIUCN = IUCNUtils.formatIucn(ancillaryDoc.get(FIELD_CATEGORY));
          if (formattedIUCN != null) {
            IUCNUtils.IUCN iucn = IUCNUtils.IUCN.valueOf(formattedIUCN);
            ancillaryStatus.setStatus(formattedIUCN);
            ancillaryStatus.setStatusCode(iucn.getCode());
            ancillaryStatus.setClbDatasetKey(dataset.getClbKey().toString());
            ancillaryStatus.setDatasetKey(dataset.getDatasetKey());
            ancillaryStatus.setDatasetAlias(dataset.getAlias());
            ancillaryStatus.setSourceId(ancillaryDoc.get(FIELD_ID));
            u.addAdditionalStatus(ancillaryStatus);
          }
        }
      } catch (IOException e) {
        log.error("Cannot load usage {} from lucene index", doc.get(FIELD_ID), e);
      }
    }

    String status = doc.get(FIELD_STATUS);
    if (status != null && !status.isEmpty()) {
      u.getUsage().setStatus(TaxonomicStatus.valueOf(status.toUpperCase()));
    }

    return u;
  }

  private NameUsageMatch.Usage constructUsage(Document doc) {

    return NameUsageMatch.Usage.builder()
        .key(doc.get(FIELD_ID))
        .name(doc.get(FIELD_SCIENTIFIC_NAME))
        .authorship(doc.get(FIELD_AUTHORSHIP))
        .rank(Rank.valueOf(doc.get(FIELD_RANK).toUpperCase()))
        .canonicalName(doc.get(FIELD_CANONICAL_NAME))
        .genericName(doc.get(FIELD_GENERICNAME))
        .parentID(doc.get(FIELD_PARENT_ID))
        .specificEpithet(doc.get(FIELD_SPECIFIC_EPITHET))
        .infraspecificEpithet(doc.get(FIELD_INFRASPECIFIC_EPITHET))
        .infragenericEpithet(doc.get(FIELD_INFRAGENERIC_EPITHET))
        .type(doc.get(FIELD_TYPE))
        .formattedName(doc.get(FIELD_FORMATTED))
        .code(getCode(doc))
        .build();
  }

  private static NomCode getCode(Document doc) {
    if (doc.get(FIELD_NOMENCLATURAL_CODE) == null) {
      return null;
    }
    return NomCode.valueOf(doc.get(FIELD_NOMENCLATURAL_CODE));
  }

  public List<NameUsageMatch> matchByName(String name, boolean fuzzySearch, int maxMatches) {

    if (!isInitialised || this.searcher == null) {
      log.warn("Lucene index not loaded. Cannot match name {}", name);
      return new ArrayList<>();
    }

    // use the same lucene analyzer to normalize input
    final String analyzedName = LuceneUtils.analyzeString(scientificNameAnalyzer, name).get(0);
    log.debug(
        "Analyzed {} query \"{}\" becomes >>{}<<",
        fuzzySearch ? "fuzzy" : "straight",
        name,
        analyzedName);

    // query needs to have at least 2 letters to match a real name
    if (analyzedName.length() < 2) {
      return new ArrayList<>();
    }

    Term t = new Term(FIELD_CANONICAL_NAME, analyzedName);
    Query q;
    if (fuzzySearch) {
      // allow 2 edits for names longer than 10 chars
      q = new FuzzyQuery(t, analyzedName.length() > 10 ? 2 : 1, 1);
    } else {
      q = new TermQuery(t);
    }

    // strict match
    Term t2 = new Term(FIELD_SCIENTIFIC_NAME, name.toLowerCase());
    Query q2 = new TermQuery(t2);

    BooleanQuery.Builder booleanQueryBuilder = new BooleanQuery.Builder();
    booleanQueryBuilder.add(q, BooleanClause.Occur.SHOULD);
    booleanQueryBuilder.add(q2, BooleanClause.Occur.SHOULD);

    q = booleanQueryBuilder.build();

    try {
      return search(q, name, fuzzySearch, maxMatches);
    } catch (RuntimeException e) {
      log.error("Lucene search error", e);
      // for example TooComplexToDeterminizeException, see
      // http://dev.gbif.org/issues/browse/POR-2725
      log.warn("Lucene failed to fuzzy search for name [{}]. Try a straight match instead", name);
      return search(new TermQuery(t), name, false, maxMatches);
    }
  }

  private List<NameUsageMatch> search(Query q, String name, boolean fuzzySearch, int maxMatches) {
    if (this.searcher == null) {
      log.warn("Lucene index not loaded. Cannot search for name {}", name);
      return new ArrayList<>();
    }
    List<NameUsageMatch> results = new ArrayList<>();
    try {
      TopDocs docs = searcher.search(q, maxMatches);
      if (docs.totalHits.value > 0) {
        for (ScoreDoc sdoc : docs.scoreDocs) {
          NameUsageMatch match = fromDoc(searcher.storedFields().document(sdoc.doc));
          if (name.equalsIgnoreCase(match.getUsage().getCanonicalName())) {
            match.getDiagnostics().setMatchType(MatchType.EXACT);
            results.add(match);
          } else {
            // even though we used a term query for straight matching the lucene analyzer has
            // already normalized
            // the name drastically. So we include these matches here only in case of fuzzy queries
            match.getDiagnostics().setMatchType(MatchType.VARIANT);
            results.add(match);
          }
        }

      } else {
        log.debug("No {} match for name {}", fuzzySearch ? "fuzzy" : "straight", name);
      }

    } catch (IOException e) {
      log.error("lucene search error", e);
    }
    return results;
  }
}
