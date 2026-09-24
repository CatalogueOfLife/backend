package life.catalogue.matching.authorship.corpus;

import life.catalogue.matching.Equality;
import life.catalogue.matching.authorship.AuthorComparator;
import life.catalogue.matching.authorship.corpus.LabelRules.Label;

import org.gbif.nameparser.api.NomCode;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs the comparator over labelled pairs. Shared by the report and the guard test, so both measure the same thing.
 */
public class CorpusEvaluator {
  public static final String ALL = "ALL";
  public static final String OTHER = "OTHER";
  public static final List<String> SCOPES = List.of(ALL, NomCode.BOTANICAL.name(), NomCode.ZOOLOGICAL.name(), OTHER);

  /**
   * @param verdict       with the years and the code of the names, which is what production gets to see
   * @param verdictNoYear with the years taken out. The labels speak about authors, this is the author logic alone
   */
  public record Verdict(AuthorPair pair, Equality verdict, Equality verdictNoYear) {
  }

  public static class Result {
    private final Map<String, ConfusionMatrix> withYears = new LinkedHashMap<>();
    private final Map<String, ConfusionMatrix> noYears = new LinkedHashMap<>();
    private final List<Verdict> verdicts = new ArrayList<>();
    private final boolean keepVerdicts;

    /**
     * @param keepVerdicts false to keep the matrices only, for a corpus too large to hold
     */
    public Result(boolean keepVerdicts) {
      this.keepVerdicts = keepVerdicts;
      for (String scope : SCOPES) {
        withYears.put(scope, new ConfusionMatrix());
        noYears.put(scope, new ConfusionMatrix());
      }
    }

    public void add(Verdict v) {
      if (keepVerdicts) {
        verdicts.add(v);
      }
      AuthorPair p = v.pair();
      if (p.label() != Label.SAME && p.label() != Label.DIFF) {
        return;
      }
      for (String scope : List.of(ALL, scope(p.code()))) {
        withYears.get(scope).add(p.label(), v.verdict(), p.weight());
        noYears.get(scope).add(p.label(), v.verdictNoYear(), p.weight());
      }
    }

    public ConfusionMatrix matrix(String scope, boolean withYears) {
      return (withYears ? this.withYears : noYears).get(scope);
    }

    public List<Verdict> verdicts() {
      return verdicts;
    }
  }

  static String scope(NomCode code) {
    return code == NomCode.BOTANICAL || code == NomCode.ZOOLOGICAL ? code.name() : OTHER;
  }

  private final AuthorComparator comparator;

  public CorpusEvaluator(AuthorComparator comparator) {
    this.comparator = comparator;
  }

  public Verdict evaluate(AuthorPair p) {
    return new Verdict(p,
      comparator.compare(p.a().toName(p.code(), true), p.b().toName(p.code(), true), p.group()),
      comparator.compare(p.a().toName(p.code(), false), p.b().toName(p.code(), false), p.group())
    );
  }

  public Result evaluate(List<AuthorPair> pairs) {
    Result result = new Result(true);
    for (AuthorPair p : pairs) {
      result.add(evaluate(p));
    }
    return result;
  }
}
