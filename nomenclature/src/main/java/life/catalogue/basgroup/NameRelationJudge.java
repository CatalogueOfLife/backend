package life.catalogue.basgroup;

import life.catalogue.api.model.Name;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.basgroup.NameRelationRules.Rel;

import java.util.*;
import java.util.function.Function;

/**
 * Judges name relations pair by pair of names with the rules of {@link NameRelationRules}, for any relation type.
 * It only decides: callers load the relations and names and apply the verdicts themselves,
 * e.g. an extended release removes the relations judged wrong while the importer only flags them.
 * <p>
 * Exact duplicates are collapsed, keeping the first, and returned apart from the verdicts.
 * A pair is only judged if it holds several distinct relations or a spelling correction, a single other relation
 * has nothing to contradict. A relation gets removed by one verdict at most, but can be flagged by several.
 *
 * @param <R> the relation type of the caller
 */
public class NameRelationJudge<R> {
  private final Function<R, Rel> toRel;

  /**
   * @param toRel converts a relation of the caller into the directed relation between two name ids the rules judge
   */
  public NameRelationJudge(Function<R, Rel> toRel) {
    this.toRel = toRel;
  }

  /**
   * @param relation the relation of the caller
   * @param issue what is wrong with it
   * @param remove true if the relation should go, false if it only deserves a flag as nothing tells which relation is right
   */
  public record Judged<R>(R relation, Issue issue, boolean remove) {
  }

  /**
   * @param duplicates relations that repeat an earlier one exactly and can go without an issue
   * @param verdicts the verdicts on the remaining relations, a relation can be judged by more than one rule
   */
  public record PairResult<R>(Name n1, Name n2, List<R> duplicates, List<Judged<R>> verdicts) {
    public boolean isEmpty() {
      return duplicates.isEmpty() && verdicts.isEmpty();
    }
  }

  /**
   * The two names of a relation, in no particular order.
   */
  private record NamePair(String id1, String id2) {
    static NamePair of(Rel rel) {
      return rel.from().compareTo(rel.to()) <= 0 ? new NamePair(rel.from(), rel.to()) : new NamePair(rel.to(), rel.from());
    }
  }

  /**
   * Groups the relations by their pair of names and judges each pair, see {@link #judgePair(Name, Name, Collection)}.
   * Relations without a related name or relating a name to itself are skipped, so are pairs with a name the lookup
   * does not know.
   *
   * @param relations the relations to judge, in any order
   * @param names looks up a name by its id, returning null for unknown ids
   * @return the results of all pairs with duplicates or verdicts, in the order the pairs first appeared
   */
  public List<PairResult<R>> judge(Iterable<R> relations, Function<String, ? extends Name> names) {
    Map<NamePair, List<R>> pairs = new LinkedHashMap<>();
    for (R r : relations) {
      var rel = toRel.apply(r);
      if (rel.from() != null && rel.to() != null && !rel.from().equals(rel.to())) {
        pairs.computeIfAbsent(NamePair.of(rel), k -> new ArrayList<>()).add(r);
      }
    }
    List<PairResult<R>> results = new ArrayList<>();
    for (var entry : pairs.entrySet()) {
      Name n1 = names.apply(entry.getKey().id1());
      Name n2 = names.apply(entry.getKey().id2());
      if (n1 == null || n2 == null) continue;
      var result = judgePair(n1, n2, entry.getValue());
      if (!result.isEmpty()) {
        results.add(result);
      }
    }
    return results;
  }

  /**
   * Judges all relations between two names.
   *
   * @param n1 one name of the pair
   * @param n2 the other name of the pair, never the same as n1
   * @param relations the relations between both names, in any direction
   * @throws IllegalArgumentException if a relation does not relate both names
   */
  public PairResult<R> judgePair(Name n1, Name n2, Collection<R> relations) {
    Map<Rel, R> rels = new LinkedHashMap<>();
    List<R> duplicates = new ArrayList<>();
    for (R r : relations) {
      var rel = toRel.apply(r);
      if (!(rel.from().equals(n1.getId()) && rel.to().equals(n2.getId()) || rel.from().equals(n2.getId()) && rel.to().equals(n1.getId()))) {
        throw new IllegalArgumentException("Relation " + rel + " does not relate names " + n1.getId() + " and " + n2.getId());
      }
      if (rels.putIfAbsent(rel, r) != null) {
        duplicates.add(r);
      }
    }
    List<Judged<R>> verdicts = new ArrayList<>();
    if (rels.size() > 1 || rels.keySet().stream().anyMatch(r -> r.type() == NomRelType.SPELLING_CORRECTION)) {
      for (var v : NameRelationRules.check(n1, n2, rels.keySet())) {
        verdicts.add(new Judged<>(rels.get(v.rel()), v.issue(), v.remove()));
      }
    }
    return new PairResult<>(n1, n2, duplicates, verdicts);
  }
}
