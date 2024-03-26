package life.catalogue.matching;

import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.search.TopDocs;

import org.apache.lucene.store.Directory;
import org.apache.lucene.store.MMapDirectory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static life.catalogue.matching.IndexConstants.*;

@Service
public class DatasetIndex {

  private static Logger LOG = LoggerFactory.getLogger(DatasetIndex.class);

  private IndexSearcher searcher;

  private DatasetIndex() {

    try {
      Directory indexDir = new MMapDirectory(Path.of("/tmp/matching-index"));
      DirectoryReader reader = DirectoryReader.open(indexDir);
      this.searcher = new IndexSearcher(reader);
    } catch (IOException e) {
      LOG.error("Cannot open lucene index", e);
    }
  }

  public NameUsageMatch matchByUsageId(Integer usageID) {

    Query query = new TermQuery(new Term(FIELD_ID, Integer.toString(usageID))); // Searching for documents with 'id' field matching the given ID

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


  private static NameUsageMatch fromDoc(Document doc) {
    NameUsageMatch u = new NameUsageMatch();
    u.setUsageKey(doc.get(FIELD_ID));
    u.setAcceptedUsageKey(doc.get(FIELD_ACCEPTED_ID));
    u.setScientificName(doc.get(FIELD_SCIENTIFIC_NAME));
    u.setCanonicalName(doc.get(FIELD_CANONICAL_NAME));

    // higher ranks
//    for (Rank r : HIGHER_RANK_FIELD_MAP.keySet()) {
//      ClassificationUtils.setHigherRank(u, r, doc.get(HIGHER_RANK_FIELD_MAP.get(r)), toInteger(doc,
//        HIGHER_RANK_ID_FIELD_MAP.get(r)));
//    }

//    u.setRank(doc.get(FIELD_RANK));
//    u.setStatus(doc.get(FIELD_STATUS));

    return u;
  }

  public List<NameUsageMatch> matchByName(String canonicalName, boolean fuzzy, int i) {
    return List.of();
  }
}
