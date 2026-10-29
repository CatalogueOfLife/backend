package life.catalogue.basgroup;

import life.catalogue.api.model.Name;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.NomRelType;
import life.catalogue.basgroup.NameRelationJudge.Judged;
import life.catalogue.basgroup.NameRelationRules.Rel;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.Test;

import static life.catalogue.api.vocab.NomRelType.*;
import static life.catalogue.basgroup.NameRelationRulesTest.genus;
import static life.catalogue.basgroup.NameRelationRulesTest.sp;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class NameRelationJudgeTest {
  /**
   * A relation of some other model, with its own key.
   */
  record Relation(int key, NomRelType type, String from, String to) {
  }

  final NameRelationJudge<Relation> judge = new NameRelationJudge<>(r -> new Rel(r.type(), r.from(), r.to()));

  static Relation rel(int key, NomRelType type, Name from, Name to) {
    return new Relation(key, type, from.getId(), to.getId());
  }

  static Function<String, Name> lookup(Name... names) {
    Map<String, Name> map = Stream.of(names).collect(Collectors.toMap(Name::getId, n -> n));
    return map::get;
  }

  static String str(Judged<Relation> j) {
    return (j.remove() ? "remove " : "flag ") + j.relation().key() + " " + j.issue();
  }

  @Test
  public void judgePair() throws Exception {
    var spar = sp("Tilapia sparrmanii Smith, 1840");
    var sparmani = sp("Tilapia sparmani Smith, 1840");
    var res = judge.judgePair(spar, sparmani, List.of(
      rel(1, SPELLING_CORRECTION, spar, sparmani),
      rel(2, BASIONYM, sparmani, spar),
      rel(3, SPELLING_CORRECTION, spar, sparmani)
    ));
    assertEquals(List.of(3), res.duplicates().stream().map(Relation::key).toList());
    assertEquals(List.of("remove 2 NAME_RELATION_CONFLICT"), res.verdicts().stream().map(NameRelationJudgeTest::str).toList());
  }

  @Test
  public void singleRelation() throws Exception {
    var comb = sp("Chromis sparrmanii (Smith, 1840)");
    var orig = sp("Tilapia sparrmanii Smith, 1840");
    // a single relation has nothing to contradict, its exact duplicate still goes
    var res = judge.judgePair(comb, orig, List.of(rel(1, BASIONYM, comb, orig), rel(2, BASIONYM, comb, orig)));
    assertEquals(List.of(2), res.duplicates().stream().map(Relation::key).toList());
    assertTrue(res.verdicts().isEmpty());
    // a single spelling correction is judged
    var hippurus = sp("Coryphaena hippurus Linnaeus, 1758");
    var equiselis = sp("Coryphaena equiselis Linnaeus, 1758");
    res = judge.judgePair(hippurus, equiselis, List.of(rel(3, SPELLING_CORRECTION, hippurus, equiselis)));
    assertEquals(List.of("remove 3 SPELLING_CORRECTION_IMPLAUSIBLE"), res.verdicts().stream().map(NameRelationJudgeTest::str).toList());
  }

  @Test
  public void judge() throws Exception {
    var spar = sp("Tilapia sparrmanii Smith, 1840");
    var sparmani = sp("Tilapia sparmani Smith, 1840");
    var lupa = genus("Lupa Leach, 1814");
    var lupea = genus("Lupea Leach, 1814");
    var missing = genus("Missing Smith, 1900");
    var results = judge.judge(List.of(
      rel(1, SPELLING_CORRECTION, spar, sparmani),
      rel(2, BASIONYM, lupea, lupa),
      // the same pair in the other direction
      rel(3, BASIONYM, sparmani, spar),
      rel(4, SPELLING_CORRECTION, lupa, lupea),
      // a name relating to itself, no related name and a name the lookup does not know are all skipped
      rel(5, BASIONYM, spar, spar),
      new Relation(6, SPELLING_CORRECTION, lupa.getId(), null),
      rel(7, SPELLING_CORRECTION, lupa, missing),
      // a pair with nothing to judge is not returned
      rel(8, TYPE, lupa, spar)
    ), lookup(spar, sparmani, lupa, lupea));
    assertEquals(2, results.size());
    assertEquals(List.of("remove 3 NAME_RELATION_CONFLICT"), results.get(0).verdicts().stream().map(NameRelationJudgeTest::str).toList());
    assertEquals(List.of("remove 2 NAME_RELATION_CONFLICT"), results.get(1).verdicts().stream().map(NameRelationJudgeTest::str).toList());
    // the names of a pair come sorted by their id
    assertTrue(results.get(1).n1().getId().compareTo(results.get(1).n2().getId()) < 0);
  }

  @Test(expected = IllegalArgumentException.class)
  public void otherNames() throws Exception {
    var spar = sp("Tilapia sparrmanii Smith, 1840");
    var sparmani = sp("Tilapia sparmani Smith, 1840");
    var lupa = genus("Lupa Leach, 1814");
    judge.judgePair(spar, sparmani, List.of(rel(1, BASIONYM, lupa, spar)));
  }

  @Test
  public void issueOfTheVerdict() throws Exception {
    var typhaeus = genus("Typhaeus");
    var typhoeus = genus("Typhoeus Beddard, 1883");
    var res = judge.judgePair(typhaeus, typhoeus, List.of(rel(1, BASIONYM, typhaeus, typhoeus), rel(2, TYPE, typhaeus, typhoeus)));
    assertEquals(List.of("flag 1 NAME_RELATION_CONFLICT", "flag 2 NAME_RELATION_CONFLICT"),
      res.verdicts().stream().map(NameRelationJudgeTest::str).sorted().toList());
    assertTrue(res.verdicts().stream().allMatch(j -> j.issue() == Issue.NAME_RELATION_CONFLICT));
  }
}
