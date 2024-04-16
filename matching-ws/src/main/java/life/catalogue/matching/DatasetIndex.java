package life.catalogue.matching;

import static life.catalogue.matching.IndexConstants.*;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

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

  private static final ScientificNameAnalyzer analyzer = new ScientificNameAnalyzer();

  private IndexSearcher searcher;

  @Value("${index.path:/tmp/matching-index}")
  String indexPath;

  private DatasetIndex() {

    try {
      Directory indexDir = new MMapDirectory(Path.of("/data/matching-ws/index"));
      DirectoryReader reader = DirectoryReader.open(indexDir);
      this.searcher = new IndexSearcher(reader);
    } catch (IOException e) {
      LOG.error("Cannot open lucene index", e);
    }
  }

  public NameUsageMatch matchByUsageId(String usageID) {

    Query query = new TermQuery(new Term(FIELD_ID, usageID));

    try {
      TopDocs docs = this.searcher.search(query, 3);

      if (docs.totalHits.value > 0) {
        Document doc = searcher.doc(docs.scoreDocs[0].doc);
        NameUsageMatch match = fromDoc(doc);
        Diagnostics diagnostics = new Diagnostics();
        match.setDiagnostics(diagnostics);
        diagnostics.setConfidence(100);
        diagnostics.setMatchType(MatchType.EXACT);
        return match;
      } else {
        LOG.warn("No usage {} found in lucene index", usageID);
      }

    } catch (IOException e) {
      LOG.error("Cannot load usage {} from lucene index", usageID, e);
    }

    return null;
  }

  public Document getByUsageId(String usageID) {
    Query query = new TermQuery(new Term(FIELD_ID, usageID));
    try {
      TopDocs docs = this.searcher.search(query, 3);

      if (docs.totalHits.value > 0) {
        return searcher.doc(docs.scoreDocs[0].doc);
      } else {
        return null;
      }
    } catch (IOException e) {
      LOG.error("Cannot load usage {} from lucene index", usageID, e);
    }
    return null;
  }

  public List<RankedName> loadHigherTaxa(String parentID) {

    List<RankedName> higherTaxa = new ArrayList<>();

    while (parentID != null){
      Document doc = getByUsageId(parentID);
      if (doc == null) {
        break;
      }
      RankedName c = new RankedName();
      c.setKey(doc.get(FIELD_ID));
      c.setName(doc.get(FIELD_CANONICAL_NAME));
      c.setRank(Rank.valueOf(doc.get(FIELD_RANK)));
      higherTaxa.add(c);
      parentID = doc.get(FIELD_PARENT_ID);
    }
    return higherTaxa;
  }

  private NameUsageMatch fromDoc(Document doc) {

    boolean synonym = false;
    NameUsageMatch u = new NameUsageMatch();
    u.setDiagnostics(new Diagnostics());

    String canonical = doc.get(FIELD_CANONICAL_NAME);

    // set the usage
    u.setUsage(new RankedName(
      doc.get(FIELD_ID),
      doc.get(FIELD_SCIENTIFIC_NAME),
      Rank.valueOf(doc.get(FIELD_RANK)),
      doc.get(FIELD_CANONICAL_NAME)
    ));

    if (doc.get(FIELD_ACCEPTED_ID) != null){
      synonym = true;
      Document accDoc = getByUsageId(doc.get(FIELD_ACCEPTED_ID));
      u.setAcceptedUsage(new RankedName(
        accDoc.get(FIELD_ID),
        accDoc.get(FIELD_SCIENTIFIC_NAME),
        Rank.valueOf(accDoc.get(FIELD_RANK)),
        accDoc.get(FIELD_CANONICAL_NAME)
      ));
      canonical = accDoc.get(FIELD_CANONICAL_NAME);
    }

    // set the higher classification
    String parentID = doc.get(FIELD_PARENT_ID);
    List<RankedName> classification = loadHigherTaxa(parentID);
    u.setClassification(classification);
    classification.add(0, new RankedName(u.getUsage().getKey(), canonical, u.getUsage().getRank()));
    u.setSynonym(synonym);

    String status = doc.get(FIELD_STATUS);
    u.setStatus(TaxonomicStatus.valueOf(status));

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
