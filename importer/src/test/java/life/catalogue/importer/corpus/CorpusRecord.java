package life.catalogue.importer.corpus;

import life.catalogue.api.model.DatasetSettings;
import life.catalogue.api.model.VerbatimRecord;

import org.gbif.dwc.terms.Term;

import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

/**
 * One input row of the interpreter corpus: the name terms of a verbatim record, how many identical records it stands
 * for and the settings of its dataset.
 *
 * @param line the line of the input file the row was read from, which identifies it across runs on the same input
 * @param n the number of verbatim records with exactly these terms in the dataset
 * @param datasetKey null for the parser corpus, which does not say
 */
public record CorpusRecord(long line, int n, @Nullable Integer datasetKey, Term type, Map<Term, String> terms,
                           DatasetSettings settings) {

  public VerbatimRecord toVerbatim() {
    VerbatimRecord v = new VerbatimRecord(line, null, type);
    v.setId((int) Math.min(line, Integer.MAX_VALUE));
    terms.forEach(v::put);
    return v;
  }

  /**
   * @return the terms in a stable, readable order as one string, the input as the reports show it
   */
  public String input() {
    return terms.entrySet().stream()
      .collect(Collectors.toMap(e -> e.getKey().simpleName(), Map.Entry::getValue, (a, b) -> a, TreeMap::new))
      .entrySet().stream()
      .map(e -> e.getKey() + "=" + e.getValue())
      .collect(Collectors.joining(" | "));
  }
}
