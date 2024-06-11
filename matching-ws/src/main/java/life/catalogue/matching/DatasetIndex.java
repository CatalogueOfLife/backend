package life.catalogue.matching;

import static life.catalogue.matching.IndexConstants.*;
import static life.catalogue.matching.IndexingService.scientificNameAnalyzer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.gbif.nameparser.api.Rank;
import org.jetbrains.annotations.NotNull;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

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

  @Value("${index.path:/data/matching-ws/index}")
  String indexPath;

  @Value("${working.dir}")
  String workingDir = "/tmp/";

  final static NameUsageMatch NO_MATCH = NameUsageMatch.builder()
    .diagnostics(
      Diagnostics.builder()
        .confidence(100)
        .matchType(MatchType.NONE)
        .build())
    .build();

  /** Attempts to read the index from disk if it exists. */
  @PostConstruct
  void init() {

    final String mainIndexPath = getMainIndexPath();
    final Map<Integer, Dataset> prefixMapping = loadPrefixMapping();

    if (new File(mainIndexPath).exists()) {
      log.info("Loading lucene index from {}", mainIndexPath);
      try {
        initWithDir(new MMapDirectory(Path.of(mainIndexPath)));
      } catch (IOException e) {
        log.warn("Cannot open lucene index. Index not available", e);
      }

      // load identifier indexes
      this.identifierSearchers = initialiseAdditionalIndexes("identifiers");

      // load ancillary indexes
      this.ancillarySearchers = initialiseAdditionalIndexes("ancillary");

    } else {
      log.warn("Lucene index not found at {}", mainIndexPath);
    }
  }

  protected void reinit() {

    final String mainIndexPath = getMainIndexPath();
    if (new File(mainIndexPath).exists()) {
      log.info("Loading lucene index from {}", mainIndexPath);
      try {
        initWithDir(new MMapDirectory(Path.of(mainIndexPath)));
      } catch (IOException e) {
        log.warn("Cannot open lucene index. Index not available", e);
      }
    } else {
      log.warn("Lucene index not found at {}", mainIndexPath);
    }
  }

  private HashMap<Dataset, IndexSearcher> initialiseAdditionalIndexes(String directoryName) {
    HashMap<Dataset, IndexSearcher> searchers = new HashMap<>();
    if (Path.of(indexPath + "/" + directoryName).toFile().exists()) {
      try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(indexPath + "/" + directoryName))) {
        for (Path entry : stream) {
          if (Files.isDirectory(entry)) {
            try {
              Directory ancillaryDir = new MMapDirectory(entry);
              DirectoryReader reader = DirectoryReader.open(ancillaryDir);
              ObjectMapper mapper = new ObjectMapper();
              Dataset dataset = mapper.readValue(new FileReader(entry.resolve("metadata.json").toFile()),
                Dataset.class);
              searchers.put(dataset, new IndexSearcher(reader));
            } catch (IOException e) {
              log.warn("Cannot open {} lucene index {}", directoryName, entry, e);
            }
          }
        }
      } catch (IOException e) {
        log.error("Cannot read " + directoryName + " index directory", e);
      }
    } else {
      log.info("Ancillary indexes not found at {}", indexPath + "/" + directoryName);
    }
    return searchers;
  }

  private Map<Integer, Dataset> loadPrefixMapping() {
    ClassLoader classLoader = Main.class.getClassLoader();

    try (InputStream inputStream = classLoader.getResourceAsStream("datasets.json")) {
      log.info("Loading identifier prefix mapping");
      if (inputStream == null) {
        return Map.of();
      }

      ObjectMapper mapper = new ObjectMapper();
      Dataset[] datasets = mapper.readValue(inputStream, Dataset[].class);

      return Arrays.stream(datasets)
        .peek(dataset -> log.info("Loaded dataset {}", dataset))
        .collect(Collectors.toMap(Dataset::getKey, dataset -> dataset));
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
    } catch (IOException e) {
      log.warn("Cannot open lucene index. Index not available", e);
    }
  }

  void initWithIdentifierDir(Dataset dataset, Directory indexDirectory) {
    try {
      DirectoryReader reader = DirectoryReader.open(indexDirectory);
      IndexSearcher searcher = new IndexSearcher(reader);
      this.identifierSearchers.put(dataset, searcher);

    } catch (IOException e) {
      log.warn("Cannot open lucene index. Index not available", e);
    }
  }

  void initWithAncillaryDir(Dataset dataset, Directory indexDirectory) {
    try {
      DirectoryReader reader = DirectoryReader.open(indexDirectory);
      IndexSearcher searcher = new IndexSearcher(reader);
      this.ancillarySearchers.put(dataset, searcher);

    } catch (IOException e) {
      log.warn("Cannot open lucene index. Index not available", e);
    }
  }

  /**
   * Returns the metadata of the index. This includes the number of taxa, the size on disk, the
   * dataset title and key, and the build information.
   * @return IndexMetadata
   */
  public IndexMetadata getIndexMetadata(){

    IndexMetadata metadata = new IndexMetadata();
    // get size on disk
    Path directoryPath = Path.of(getMainIndexPath());
    try {
      BasicFileAttributes attributes = Files.readAttributes(directoryPath, BasicFileAttributes.class);
      Instant creationTime = attributes.creationTime().toInstant();
      DateTimeFormatter dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSSXXX")
        .withZone(ZoneId.systemDefault());
      String formattedCreationTime = dateFormatter.format(creationTime);
      metadata.setCreated(formattedCreationTime);
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

    metadata.setDatasetTitle((String) getDatasetInfo().getOrDefault("datasetTitle", null));
    metadata.setDatasetKey((String) getDatasetInfo().getOrDefault("datasetKey", null));
    metadata.setBuildInfo(getGitInfo());

    // number of taxa
    IndexReader reader = getSearcher().getIndexReader();
    int numDocs = reader.numDocs();
    metadata.setTaxonCount((long) numDocs);
    try {
      Map<String, Long> rankCounts = new LinkedHashMap<>();
      rankCounts.put(Rank.KINGDOM.name(), getCountForRank(reader, Rank.KINGDOM));
      rankCounts.put(Rank.PHYLUM.name(), getCountForRank(reader, Rank.PHYLUM));
      rankCounts.put(Rank.CLASS.name(), getCountForRank(reader, Rank.CLASS));
      rankCounts.put(Rank.ORDER.name(), getCountForRank(reader, Rank.ORDER));
      rankCounts.put(Rank.FAMILY.name(), getCountForRank(reader, Rank.FAMILY));
      rankCounts.put(Rank.GENUS.name(), getCountForRank(reader, Rank.GENUS));
      rankCounts.put(Rank.SPECIES.name(), getCountForRank(reader, Rank.SPECIES));
      rankCounts.put(Rank.SUBSPECIES.name(), getCountForRank(reader, Rank.SUBSPECIES));
      metadata.setTaxaByRankCount(rankCounts);
    } catch (IOException e) {
      log.error("Cannot read index information", e);
    }
    return metadata;
  }

  /**
   * Reads the git information from the git.json file in the working directory.
   * @return Map<String, Object>
   */
  public Map<String, Object> getGitInfo() {
    ObjectMapper mapper = new ObjectMapper();
    final String filePath = workingDir + "/git.json";
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

        return Map.of("sha", sha, "url", url, "html_url", html_url, "name", name, "email", email, "date", date, "message", message);
      } else {
        log.warn("Git info not found at {}", filePath);
      }
    } catch (IOException e) {
      log.error("Cannot read index git information", e);
    }
    return Map.of();
  }

  /**
   * Reads the dataset information from the dataset.json file in the working directory.
   * @return Map<String, Object>
   */
  public Map<String, Object> getDatasetInfo() {
    ObjectMapper mapper = new ObjectMapper();

    String filePath = workingDir + "/dataset.json";

    try {
      if (new File(filePath).exists()){
        log.info("Loading dataset info from {}", filePath);
        // Read JSON file and parse to JsonNode
        JsonNode rootNode = mapper.readTree(new File(filePath));
        // Navigate to the author node
        String datasetKey = rootNode.path("key").asText();
        String datasetTitle = rootNode.path("title").asText();
        return Map.of("datasetKey", datasetKey, "datasetTitle", datasetTitle);
      } else {
        log.warn("Dataset info not found at {}", filePath);
      }
    } catch (IOException e) {
      log.error("Cannot read index dataset information", e);
    }
    return Map.of();
  }

  private long getCountForRank(IndexReader reader, Rank rank) throws IOException {
    Query query = new TermQuery(new Term(FIELD_RANK, rank.name()));
    TopDocs docs = searcher.search(query, reader.numDocs());
    return docs.totalHits.value;
  }

  /**
   * Creates a new in memory lucene index from the given list of usages.
   *
   * @return
   * @throws IOException
   */
  public static DatasetIndex newDatasetIndex(Directory mainIndexDir) throws IOException {
    DatasetIndex datasetIndex = new DatasetIndex();
    datasetIndex.initWithDir(mainIndexDir);
    return datasetIndex;
  }

  /**
   * Creates a new in memory lucene index from the given list of usages.
   *
   * @return
   * @throws IOException
   */
  public static DatasetIndex newDatasetIndex(Directory mainIndexDir, Map<Dataset, Directory> idIndexes) throws IOException {
    DatasetIndex datasetIndex = newDatasetIndex(mainIndexDir);
    for (Dataset dataset: idIndexes.keySet()){
      datasetIndex.initWithIdentifierDir(dataset, idIndexes.get(dataset));
    }
    return datasetIndex;
  }

//  /**
//   * Creates a new in memory lucene index from the given list of usages.
//   *
//   * @param usages
//   * @return
//   * @throws IOException
//   */
//  public static DatasetIndex newMemoryDatasetIndex(MatchingService matchingService, Iterable<NameUsage> usages, Iterable<NameUsage> idUsages) throws IOException {
//
//    Directory mainDir = IndexingService.newMemoryIndex(usages);
//
//    //initialise main index
//    DatasetIndex datasetIndex = new DatasetIndex();
//    datasetIndex.initWithDir(mainDir);
//
//    // initialise identifier index
//    Directory idDir = IndexingService.newMemoryIndex(usages);
//
//    // join the index
//    IndexingService.createJoinIndex(matchingService, mainDir, idDir, false);
//
//
//    // dataset index
//    DatasetIndex datasetIndex = new DatasetIndex();
//    datasetIndex.initWithDir(mainDir, idDir);
//
//
//    return datasetIndex;
//  }


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

  private static String escapeQueryChars(String s) {
    StringBuilder sb = new StringBuilder();
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      // These are the special characters that need to be escaped
      if (c == '\\' || c == '+' || c == '-' || c == '!' || c == '(' || c == ')' ||
        c == ':' || c == '^' || c == '[' || c == ']' || c == '\"' || c == '{' ||
        c == '}' || c == '~' || c == '*' || c == '?' || c == '|' || c == '&' ||
        c == '/' || Character.isWhitespace(c)) {
        sb.append('\\');
      }
      sb.append(c);
    }
    return sb.toString();
  }

  private Optional<Document> getByUsageKey(String usageKey) {
    Query query = new TermQuery(new Term(FIELD_ID, escapeQueryChars(usageKey)));
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
      match.setDiagnostics(
        Diagnostics.builder()
        .confidence(100)
        .matchType(MatchType.EXACT)
      .build());
      return match;
    } else {
      log.warn("No usage {} found in lucene index", key);
      return NO_MATCH;
    }
  }

  @Builder
  public static class IDMatchResult {
    Optional<Document> doc;
    String datasetKey;
    boolean multipleMatches;
    boolean matchOnlyInJoinIndex;
  }

  /**
   * Matches an external ID
   * @param key the external ID to match
   * @return IDMatchResult with the document, datasetKey, and flags
   */
  public NameUsageMatch matchByExternalKey(String key, MatchIssue notFoundIssue, MatchIssue ignoredIssue) {

    // if join indexes are present, add them to the match
    if (identifierSearchers != null && !identifierSearchers.isEmpty()){
      try {
        for (Dataset dataset: identifierSearchers.keySet()){

          // use the prefix mapping
          if (dataset.getPrefixMapping() != null && !dataset.getPrefixMapping().isEmpty()) {
            for (String prefix : dataset.getPrefixMapping()) {
              if (key.startsWith(prefix)) {
                key = key.replace(prefix, "");
              }
            }
          }

          if ((dataset.prefix == null || !key.startsWith(dataset.prefix)) || dataset.prefix.equals("*")) {
            // only search indexes with matching prefixes
            continue;
          }

          log.info("Searching for identifier {} in dataset {}", key, dataset.getKey());

          // find the index and search it
          IndexSearcher identifierSearcher = identifierSearchers.get(dataset);
          Query identifierQuery = new TermQuery(new Term(FIELD_ID, key));
          TopDocs identifierDocs = identifierSearcher.search(identifierQuery, 3);

          if (identifierDocs.totalHits.value > 0) {

            // check for multiple matches - indicates duplicates in index
            if (identifierDocs.totalHits.value > 1) {
              log.warn("Multiple matches found for identifier {} in dataset {}", key, dataset.getKey());
              return NameUsageMatch.builder()
                .diagnostics(
                  Diagnostics.builder()
                    .matchType(MatchType.NONE)
                    .issues(new ArrayList<MatchIssue>(List.of(ignoredIssue)))
                    .note("Multiple matches found for the identifier")
                    .build())
                .synonym(false)
                .build();
            }

            Document identifierDoc = identifierSearcher.storedFields().document(identifierDocs.scoreDocs[0].doc);

            final String joinID = identifierDoc.get(FIELD_JOIN_ID);
            Query getByIDQuery = new TermQuery(new Term(FIELD_ID, joinID));
            TopDocs docs = getSearcher().search(getByIDQuery, 3);
            if (docs.totalHits.value > 0) {
              // success - build the name match
              return fromDoc(getSearcher().storedFields().document(docs.scoreDocs[0].doc));
            } else {
              log.warn("Cannot find usage {} in main lucene index after " +
                "finding it in identifier index for {}", key, dataset.getKey());
              return NameUsageMatch.builder()
                .diagnostics(
                  Diagnostics.builder()
                    .matchType(MatchType.NONE)
                    .issues(new ArrayList<MatchIssue>(List.of(ignoredIssue)))
                    .note("Identifier recognised in {}, but not matching in main index" + dataset.getKey())
                    .build())
                .synonym(false)
                .build();
            }
          } else {
            log.info("Identifier {} not found in dataset {}", key, dataset.getKey());
            return NameUsageMatch.builder()
              .diagnostics(
                Diagnostics.builder()
                  .matchType(MatchType.NONE)
                  .issues(new ArrayList<MatchIssue>(List.of(notFoundIssue)))
                  .note("Not found for the identifier")
                  .build())
              .synonym(false)
              .build();
          }
        }
      } catch (IOException e) {
        log.error("Problem querying external ID indexes with {}", key, e);
      }
    }

    // no indexes available
    return NO_MATCH;
  }

  /**
   * Loads the higher classification of a taxon starting from the given parentID. The parentID is
   * not included in the result.
   *
   * <p>TODO: this might be the naive approach. Need to check performance vs MapDB.
   *
   * @param parentID
   * @return
   */
  private List<RankedName> loadHigherTaxa(String parentID) {

    List<RankedName> higherTaxa = new ArrayList<>();

    while (parentID != null) {
      Optional<Document> docOpt = getByUsageKey(parentID);
      if (docOpt.isEmpty()) {
        break;
      }
      Document doc = docOpt.get();
      RankedName c = new RankedName();
      c.setKey(doc.get(FIELD_ID));
      c.setName(doc.get(FIELD_CANONICAL_NAME));
      c.setRank(Rank.valueOf(doc.get(FIELD_RANK)));
      higherTaxa.add(0, c);
      parentID = doc.get(FIELD_PARENT_ID);
    }
    return higherTaxa;
  }

  /**
   * Converts a lucene document into a NameUsageMatch object.
   *
   * @param doc
   * @return
   */
  private NameUsageMatch fromDoc(Document doc) {

    boolean synonym = false;
    NameUsageMatch u = NameUsageMatch.builder().build();
    u.setDiagnostics(Diagnostics.builder().build());

    // set the usage
    u.setUsage(
        new RankedName(
            doc.get(FIELD_ID),
            doc.get(FIELD_SCIENTIFIC_NAME),
            Rank.valueOf(doc.get(FIELD_RANK)),
            doc.get(FIELD_CANONICAL_NAME)));

    String acceptedParentID = null;

    if (doc.get(FIELD_ACCEPTED_ID) != null) {
      synonym = true;
      Optional<Document> accDocOpt = getByUsageKey(doc.get(FIELD_ACCEPTED_ID));
      if (accDocOpt.isPresent()) {
        Document accDoc = accDocOpt.get();
        u.setAcceptedUsage(
            new RankedName(
                accDoc.get(FIELD_ID),
                accDoc.get(FIELD_SCIENTIFIC_NAME),
                Rank.valueOf(accDoc.get(FIELD_RANK)),
                accDoc.get(FIELD_CANONICAL_NAME)));
        acceptedParentID = accDoc.get(FIELD_PARENT_ID);
      }
    }

    // set the higher classification
    String parentID = doc.get(FIELD_PARENT_ID);
    List<RankedName> classification = null;
    if (acceptedParentID != null) {
      classification = loadHigherTaxa(acceptedParentID);
    } else {
      classification = loadHigherTaxa(parentID);
    }

    u.setClassification(classification);

    // add leaf
    if (u.getAcceptedUsage() != null) {
      classification.add(
          new RankedName(
              u.getAcceptedUsage().getKey(),
              u.getAcceptedUsage().getCanonicalName(),
              u.getAcceptedUsage().getRank(),
              u.getAcceptedUsage().getCanonicalName()));
    } else {
      classification.add(
          new RankedName(
              doc.get(FIELD_ID),
              doc.get(FIELD_CANONICAL_NAME),
              Rank.valueOf(doc.get(FIELD_RANK)),
              doc.get(FIELD_CANONICAL_NAME)));
    }
    u.setSynonym(synonym);

    // if ancillary join indexes are present, add them to the match
    for (Dataset dataset: ancillarySearchers.keySet()){
      IndexSearcher ancillarySearcher = ancillarySearchers.get(dataset);
      Query query = new TermQuery(new Term(FIELD_JOIN_ID, doc.get(FIELD_ID) ));
      try {
        TopDocs docs = ancillarySearcher.search(query, 3);
        if (docs.totalHits.value > 0) {
          Document ancillaryDoc = ancillarySearcher.storedFields().document(docs.scoreDocs[0].doc);
          String status = ancillaryDoc.get(FIELD_CATEGORY);
          Status ancillaryStatus = new Status();
          ancillaryStatus.setCategory(status);
          ancillaryStatus.setDatasetKey(dataset.getKey().toString());
          ancillaryStatus.setGbifKey(dataset.getGbifKey());
          ancillaryStatus.setDatasetAlias(dataset.getAlias());
          u.addAdditionalStatus(ancillaryStatus);
        }
      } catch (IOException e) {
        log.error("Cannot load usage {} from lucene index", doc.get(FIELD_ID), e);
      }
    }

    String status = doc.get(FIELD_STATUS);
    u.getDiagnostics().setStatus(TaxonomicStatus.valueOf(status));

    return u;
  }

  public List<NameUsageMatch> matchByName(String name, boolean fuzzySearch, int maxMatches) {
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
      // for example TooComplexToDeterminizeException, see
      // http://dev.gbif.org/issues/browse/POR-2725
      log.warn("Lucene failed to fuzzy search for name [{}]. Try a straight match instead", name);
      return search(new TermQuery(t), name, false, maxMatches);
    }
  }

  private List<NameUsageMatch> search(Query q, String name, boolean fuzzySearch, int maxMatches) {
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
            match.getDiagnostics().setMatchType(MatchType.FUZZY);
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
