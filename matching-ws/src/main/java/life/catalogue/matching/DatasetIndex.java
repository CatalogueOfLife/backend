package life.catalogue.matching;

import static life.catalogue.matching.IndexConstants.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import life.catalogue.api.vocab.TaxonomicStatus;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;

import org.gbif.api.vocabulary.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class DatasetIndex {

  private static Logger LOG = LoggerFactory.getLogger(DatasetIndex.class);

  private static final ScientificNameAnalyzer analyzer = new ScientificNameAnalyzer();

  private IndexSearcher searcher;

  private Map<String, CachedName> higherTaxaCache = new HashMap<>();

  class CachedName {

    CachedName(String key, String parentKey, String name, String rank) {
      this.key = key;
      this.parentKey = parentKey;
      this.name = name;
      this.rank = rank;
    }
    String key;
    String parentKey;
    String name;
    String rank;
  }

  private DatasetIndex() {

    try {
      Directory indexDir = new MMapDirectory(Path.of("/tmp/matching-index"));
      DirectoryReader reader = DirectoryReader.open(indexDir);
      this.searcher = new IndexSearcher(reader);
      buildHigherTaxaCache();
    } catch (IOException e) {
      LOG.error("Cannot open lucene index", e);
    }
  }

  /**
   * Naive implementation that build an in-memory cache of higher taxa.
   * Not intended for production use.
   */
  private void buildHigherTaxaCache(){
    IndexReader reader = this.searcher.getIndexReader();

    int pageSize = 10000; // Number of documents to retrieve per page
    int pageNumber = 1; // Start with page 1

    try {
      // Perform a query to retrieve all documents
      MatchAllDocsQuery query = new MatchAllDocsQuery();
      ScoreDoc[] hits = searcher.search(query, Integer.MAX_VALUE).scoreDocs;

      int totalHits = hits.length;
      int totalPages = (int) Math.ceil((double) totalHits / pageSize);

      // Pagination loop
      while (pageNumber <= totalPages) {
        int start = (pageNumber - 1) * pageSize;
        int end = Math.min(start + pageSize, totalHits);

        System.out.println("Page " + pageNumber + "/" + totalPages + ":");

        for (int i = start; i < end; i++) {
          int docId = hits[i].doc;
          Document doc = reader.document(docId);
          String name = doc.get(FIELD_SCIENTIFIC_NAME);
          String id = doc.get(FIELD_ID);
          String rank = doc.get(FIELD_RANK);
          String parentID = doc.get(FIELD_PARENT_ID);
          if (rank != null && !rank.equals("SPECIES") && !rank.equals("SUBSPECIES")) {
            higherTaxaCache.put(id, new CachedName(id, parentID, name, rank));
          }
        }

        pageNumber++;
      }

      System.out.println("Cache size: " + higherTaxaCache.size());
    } catch (IOException e) {
      e.printStackTrace();
    }
  }

  public NameUsageMatch matchByUsageId(String usageID) {

    Query query =
        new TermQuery(
            new Term(
                FIELD_ID,
                    usageID)); // Searching for documents with 'id' field matching the given ID

    try {
      TopDocs docs = this.searcher.search(query, 3);

      if (docs.totalHits.value > 0) {
        Document doc = searcher.doc(docs.scoreDocs[0].doc);
        NameUsageMatch match = fromDoc(doc);
        match.setConfidence(100);
        return match;
      } else {
        LOG.warn("No usage {} found in lucene index", usageID);
      }

    } catch (IOException e) {
      LOG.error("Cannot load usage {} from lucene index", usageID, e);
    }

    return null;
  }

  public List<CachedName> loadHigherTaxa(String parentID) {
    List<CachedName> higherTaxa = new ArrayList<>();
    String currentID = parentID;
    while (currentID != null) {
      CachedName current = higherTaxaCache.get(currentID);
      if (current == null) {
        break;
      }
      higherTaxa.add(current);
      currentID = current.parentKey;
    }
    return higherTaxa;
  }

  private NameUsageMatch fromDoc(Document doc) {
    NameUsageMatch u = new NameUsageMatch();
    u.setUsageKey(doc.get(FIELD_ID));
    u.setAcceptedUsageKey(doc.get(FIELD_ACCEPTED_ID));
    u.setScientificName(doc.get(FIELD_SCIENTIFIC_NAME));
    u.setCanonicalName(doc.get(FIELD_CANONICAL_NAME));

    // set the higher classification
    String parentID = doc.get(FIELD_PARENT_ID);
    List<CachedName> classification = loadHigherTaxa(parentID);
    for (CachedName c : classification) {
      Rank rank = Rank.valueOf(c.rank);
      u.setHigherRank(c.key, c.name, rank);
    }

    //FIXME dodgy, as some values from CLB might not be in this enum
    String rankStr = doc.get(FIELD_RANK);
    Rank rank = Rank.valueOf(rankStr);
    u.setHigherRank(u.getUsageKey(), u.getScientificName(), rank);
    u.setRank(rank);

    // parse to enum
    String status = doc.get(FIELD_STATUS);
    u.setStatus(TaxonomicStatus.valueOf(status));

    return u;
  }

  public List<NameUsageMatch> matchByName(String name, boolean fuzzySearch, int maxMatches) {
    // use the same lucene analyzer to normalize input
    final String analyzedName = LuceneUtils.analyzeString(analyzer, name).get(0);
    LOG.debug("Analyzed {} query \"{}\" becomes >>{}<<", fuzzySearch ? "fuzzy" : "straight", name, analyzedName);

    // query needs to have at least 2 letters to match a real name
    if (analyzedName.length() < 2) {
      return List.of();
    }

    Term t = new Term(FIELD_CANONICAL_NAME, analyzedName);
    Query q;
    if (fuzzySearch) {
      // allow 2 edits for names longer than 10 chars
      q = new FuzzyQuery(t, analyzedName.length() > 10 ? 2 : 1, 1);
    } else {
      q = new TermQuery(t);
    }

    try {
      return search(q, name, fuzzySearch, maxMatches);
    } catch (RuntimeException e) {
      // for example TooComplexToDeterminizeException, see http://dev.gbif.org/issues/browse/POR-2725
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
          if (name.equalsIgnoreCase(match.getCanonicalName())) {
            match.setMatchType(NameUsageMatch.MatchType.EXACT);
            results.add(match);
          } else {
            // even though we used a term query for straight matching the lucene analyzer has already normalized
            // the name drastically. So we include these matches here only in case of fuzzy queries
            match.setMatchType(NameUsageMatch.MatchType.FUZZY);
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
