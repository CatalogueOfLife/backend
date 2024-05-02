package life.catalogue.matching;

import static life.catalogue.matching.IndexConstants.*;
import static life.catalogue.matching.IndexingService.analyzer;

import jakarta.annotation.PostConstruct;
import java.io.File;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import life.catalogue.api.vocab.MatchType;
import life.catalogue.api.vocab.TaxonomicStatus;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;
import org.gbif.nameparser.api.Rank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class DatasetIndex {

  private static Logger LOG = LoggerFactory.getLogger(DatasetIndex.class);

  private IndexSearcher searcher;

  @Value("${index.path:/data/matching-ws/index}")
  String indexPath;

  /** Attempts to read the index from disk if it exists. */
  @PostConstruct
  void init() {
    if (new File(indexPath).exists()) {
      LOG.info("Loading lucene index from {}", indexPath);
      try {
        initWithDir(new MMapDirectory(Path.of(indexPath)));
      } catch (IOException e) {
        LOG.warn("Cannot open lucene index. Index not available", e);
      }
    } else {
      LOG.warn("Lucene index not found at {}", indexPath);
    }
  }

  void initWithDir(Directory indexDir) {
    try {
      DirectoryReader reader = DirectoryReader.open(indexDir);
      this.searcher = new IndexSearcher(reader);
    } catch (IOException e) {
      LOG.warn("Cannot open lucene index. Index not available", e);
    }
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
    Optional<Document> docOpt = getByUsageId(usageKey);
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

  public Optional<Document> getByUsageId(String usageKey) {
    Query query = new TermQuery(new Term(FIELD_ID, usageKey));
    try {
      TopDocs docs = getSearcher().search(query, 3);
      if (docs.totalHits.value > 0) {
        return Optional.of(getSearcher().doc(docs.scoreDocs[0].doc));
      } else {
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
      Optional<Document> docOpt = getByUsageId(parentID);
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
      Optional<Document> accDocOpt = getByUsageId(doc.get(FIELD_ACCEPTED_ID));
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

    String status = doc.get(FIELD_STATUS);
    u.setStatus(TaxonomicStatus.valueOf(status));
    u.getDiagnostics().setStatus(status);

    return u;
  }

  public List<NameUsageMatch> matchByName(String name, boolean fuzzySearch, int maxMatches) {
    // use the same lucene analyzer to normalize input
    final String analyzedName = LuceneUtils.analyzeString(analyzer, name).get(0);
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
          NameUsageMatch match = fromDoc(searcher.doc(sdoc.doc));
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
