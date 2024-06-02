package life.catalogue.matching;

import static life.catalogue.matching.IndexConstants.*;
import static life.catalogue.matching.IndexingService.scientificNameAnalyzer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.gbif.nameparser.api.Rank;

import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Represents an index of a dataset.
 */
@Service
public class DatasetIndex {

  private static Logger LOG = LoggerFactory.getLogger(DatasetIndex.class);

  private IndexSearcher searcher;
  private Map<String, IndexSearcher> identifierSearchers = new HashMap<>();
  private Map<String, IndexSearcher> ancillarySearchers = new HashMap<>();

  @Value("${index.path:/data/matching-ws/index}")
  String indexPath;

  @Value("${working.dir}")
  String workingDir = "/tmp/";

  /** Attempts to read the index from disk if it exists. */
  @PostConstruct
  void init() {

    final String mainIndexPath = getMainIndexPath();

    if (new File(mainIndexPath).exists()) {
      LOG.info("Loading lucene index from {}", mainIndexPath);
      try {
        initWithDir(new MMapDirectory(Path.of(mainIndexPath)));
      } catch (IOException e) {
        LOG.warn("Cannot open lucene index. Index not available", e);
      }

      // load identifier indexes
      this.identifierSearchers = new HashMap<>();
      if (Path.of(indexPath + "/identifiers").toFile().exists()) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(indexPath + "/identifiers"))) {
          for (Path entry : stream) {
            if (Files.isDirectory(entry)) {
              try {
                Directory identifierDir = new MMapDirectory(entry);
                DirectoryReader reader = DirectoryReader.open(identifierDir);
                identifierSearchers.put(entry.toFile().getName(), new IndexSearcher(reader));
              } catch (IOException e) {
                LOG.warn("Cannot open identifiers lucene index {}", entry, e);
              }
            }
          }
        } catch (IOException e) {
          LOG.error("Cannot read identifiers index directory", e);
        }
      } else {
        LOG.info("Identifiers indexes not found at {}", indexPath + "/identifiers");
      }

      // load ancillary indexes
      this.ancillarySearchers = new HashMap<>();
      if (Path.of(indexPath + "/ancillary").toFile().exists()) {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(Path.of(indexPath + "/ancillary"))) {
          for (Path entry : stream) {
            if (Files.isDirectory(entry)) {
              try {
                Directory ancillaryDir = new MMapDirectory(entry);
                DirectoryReader reader = DirectoryReader.open(ancillaryDir);
                ancillarySearchers.put(entry.toFile().getName(), new IndexSearcher(reader));
              } catch (IOException e) {
                LOG.warn("Cannot open ancillary lucene index {}", entry, e);
              }
            }
          }
        } catch (IOException e) {
          LOG.error("Cannot read ancillary index directory", e);
        }
      } else {
        LOG.info("Ancillary indexes not found at {}", indexPath + "/ancillary");
      }

    } else {
      LOG.warn("Lucene index not found at {}", mainIndexPath);
    }
  }

  protected void reinit() {

    final String mainIndexPath = getMainIndexPath();
    if (new File(mainIndexPath).exists()) {
      LOG.info("Loading lucene index from {}", mainIndexPath);
      try {
        initWithDir(new MMapDirectory(Path.of(mainIndexPath)));
      } catch (IOException e) {
        LOG.warn("Cannot open lucene index. Index not available", e);
      }
    } else {
      LOG.warn("Lucene index not found at {}", mainIndexPath);
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
      LOG.warn("Cannot open lucene index. Index not available", e);
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
      LOG.error("Cannot read index directory attributes", e);
    }

    metadata.setDatasetTitle((String) readDatasetInfo().getOrDefault("datasetTitle", null));
    metadata.setDatasetKey((String) readDatasetInfo().getOrDefault("datasetKey", null));
    metadata.setBuildInfo(readGitInfo());

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
      LOG.error("Cannot read index information", e);
    }
    return metadata;
  }

  /**
   * Reads the git information from the git.json file in the working directory.
   * @return Map<String, Object>
   */
  public Map<String, Object> readGitInfo() {
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
        LOG.warn("Git info not found at {}", filePath);
      }
    } catch (IOException e) {
      LOG.error("Cannot read index git information", e);
    }
    return Map.of();
  }

  /**
   * Reads the dataset information from the dataset.json file in the working directory.
   * @return Map<String, Object>
   */
  public Map<String, Object> readDatasetInfo() {
    ObjectMapper mapper = new ObjectMapper();

    String filePath = workingDir + "/dataset.json";

    try {
      if (new File(filePath).exists()){
        LOG.info("Loading dataset info from {}", filePath);
        // Read JSON file and parse to JsonNode
        JsonNode rootNode = mapper.readTree(new File(filePath));
        // Navigate to the author node
        String datasetKey = rootNode.path("key").asText();
        String datasetTitle = rootNode.path("title").asText();
        return Map.of("datasetKey", datasetKey, "datasetTitle", datasetTitle);
      } else {
        LOG.warn("Dataset info not found at {}", filePath);
      }
    } catch (IOException e) {
      LOG.error("Cannot read index dataset information", e);
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
   * @param usages
   * @return
   * @throws IOException
   */
  public static DatasetIndex newMemoryIndex(Iterable<NameUsage> usages) throws IOException {
    Directory dir = IndexingService.newMemoryIndex(usages);
    DatasetIndex datasetIndex = new DatasetIndex();
    datasetIndex.initWithDir(dir);
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
   * @param usageKey
   * @return
   */
  public NameUsageMatch matchByUsageKey(String usageKey) {
    Optional<Document> docOpt = getByUsageKey(usageKey, true);
    if (docOpt.isPresent()) {
      Document doc = docOpt.get();
      NameUsageMatch match = fromDoc(doc);
      Diagnostics diagnostics = new Diagnostics();
      match.setDiagnostics(diagnostics);
      diagnostics.setConfidence(100);
      diagnostics.setMatchType(MatchType.EXACT);
      return match;
    } else {
      LOG.warn("No usage {} found in lucene index", usageKey);
      NameUsageMatch match = new NameUsageMatch();
      Diagnostics diagnostics = new Diagnostics();
      match.setDiagnostics(diagnostics);
      diagnostics.setConfidence(100);
      diagnostics.setMatchType(MatchType.NONE);
      return match;
    }
  }

  public static String escapeQueryChars(String s) {
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

  public Optional<Document> getByUsageKey(String usageKey, boolean allowExternalIDs) {
    Query query = new TermQuery(new Term(FIELD_ID, escapeQueryChars(usageKey)));
    try {
      TopDocs docs = getSearcher().search(query, 3);
      if (docs.totalHits.value > 0) {
        return Optional.of(getSearcher().storedFields().document(docs.scoreDocs[0].doc));
      } else if (allowExternalIDs) {

        // if join indexes are present, add them to the match
        if (identifierSearchers != null){
          for (String datasetKey: identifierSearchers.keySet()){
            IndexSearcher identifierSearcher = identifierSearchers.get(datasetKey);
            Query identifierQuery = new TermQuery(new Term(FIELD_ID, usageKey));
            LOG.info("Searching for identifier {} in dataset {}", usageKey, datasetKey);
            TopDocs identifierDocs = identifierSearcher.search(identifierQuery, 3);
            if (identifierDocs.totalHits.value > 0) {
              Document identifierDoc = identifierSearcher.storedFields().document(identifierDocs.scoreDocs[0].doc);
              final String joinID = identifierDoc.get(FIELD_JOIN_ID);
              Query getByIDQuery = new TermQuery(new Term(FIELD_ID, joinID));
              docs = getSearcher().search(getByIDQuery, 3);
              if (docs.totalHits.value > 0) {
                return Optional.of(getSearcher().storedFields().document(docs.scoreDocs[0].doc));
              } else {
                LOG.warn("Cannot find usage {} in main lucene index after finding it in identifier index for {}", usageKey, datasetKey);
                return Optional.empty();
              }
            }
          }
        }
        return Optional.empty();
      }
    } catch (IOException e) {
      LOG.error("Cannot load usage {} from lucene index", usageKey, e);
    }
    return Optional.empty();
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
  public List<RankedName> loadHigherTaxa(String parentID) {

    List<RankedName> higherTaxa = new ArrayList<>();

    while (parentID != null) {
      Optional<Document> docOpt = getByUsageKey(parentID, false);
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
    NameUsageMatch u = new NameUsageMatch();
    u.setDiagnostics(new Diagnostics());

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
      Optional<Document> accDocOpt = getByUsageKey(doc.get(FIELD_ACCEPTED_ID), false);
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

    // if join indexes are present, add them to the match
    for (String datasetKey: ancillarySearchers.keySet()){
      IndexSearcher ancillarySearcher = ancillarySearchers.get(datasetKey);
      Query query = new TermQuery(new Term(FIELD_JOIN_ID, doc.get(FIELD_ID) ));
      try {
        TopDocs docs = ancillarySearcher.search(query, 3);
        if (docs.totalHits.value > 0) {
          Document ancillaryDoc = ancillarySearcher.storedFields().document(docs.scoreDocs[0].doc);
          String status = ancillaryDoc.get(FIELD_CATEGORY);
          Status ancillaryStatus = new Status();
          ancillaryStatus.setCategory(status);
          ancillaryStatus.setDatasetKey(datasetKey);
          ancillaryStatus.setDatasetTitle("");
          u.getAdditionalStatus().add(ancillaryStatus);
        }
      } catch (IOException e) {
        LOG.error("Cannot load usage {} from lucene index", doc.get(FIELD_ID), e);
      }
    }

    String status = doc.get(FIELD_STATUS);
    u.getDiagnostics().setStatus(TaxonomicStatus.valueOf(status));

    return u;
  }

  public List<NameUsageMatch> matchByName(String name, boolean fuzzySearch, int maxMatches) {
    // use the same lucene analyzer to normalize input
    final String analyzedName = LuceneUtils.analyzeString(scientificNameAnalyzer, name).get(0);
    LOG.debug(
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
      LOG.warn("Lucene failed to fuzzy search for name [{}]. Try a straight match instead", name);
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
        LOG.debug("No {} match for name {}", fuzzySearch ? "fuzzy" : "straight", name);
      }

    } catch (IOException e) {
      LOG.error("lucene search error", e);
    }
    return results;
  }
}
