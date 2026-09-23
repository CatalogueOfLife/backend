package life.catalogue.matching.person.harvest;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.person.*;

import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;

import javax.annotation.Nullable;

import static life.catalogue.matching.person.Provenance.*;

/**
 * Merges the records of a harvest into the registry files.
 * <ul>
 *   <li>Records and persons are joined on shared authority ids only, never on names.</li>
 *   <li>A value in the files is never overwritten and a line never removed, curated or not: the harvest only fills
 *   empty cells and adds lines.</li>
 *   <li>Where the records of one run disagree, IPNI wins for a person with an IPNI and no ZooBank id, ZooBank for one
 *   with a ZooBank and no IPNI id, Wikidata otherwise, and the disagreement is reported.</li>
 *   <li>Two persons with different ids of one authority are never merged.</li>
 * </ul>
 */
public class PersonMerger {
  private static final int YEAR_TOLERANCE = 2;
  private final MergeReport report = new MergeReport();

  public record Result(PersonFiles.Content content, MergeReport report) {
  }

  private static final class Draft {
    String id;
    String wikidata;
    String ipni;
    String zoobank;
    final List<String> formerIds = new ArrayList<>();
    String family;
    String given;
    String suffix;
    Integer born;
    Integer died;
    Integer activeFrom;
    Integer activeTo;
    final Set<TaxGroup> groups = EnumSet.noneOf(TaxGroup.class);
    Provenance source;
    boolean existing;
    final List<PersonRecord> records = new ArrayList<>();

    static Draft of(Person p) {
      Draft d = new Draft();
      d.id = p.id();
      d.wikidata = p.wikidata();
      d.ipni = p.ipni();
      d.zoobank = p.zoobank();
      d.formerIds.addAll(p.formerIds());
      d.family = p.family();
      d.given = p.given();
      d.suffix = p.suffix();
      d.born = p.born();
      d.died = p.died();
      d.activeFrom = p.activeFrom();
      d.activeTo = p.activeTo();
      d.groups.addAll(p.groups());
      d.source = p.source();
      d.existing = true;
      return d;
    }

    boolean local() {
      return id != null && id.startsWith(Person.LOCAL);
    }

    Set<String> authorityIds() {
      Set<String> ids = new LinkedHashSet<>();
      if (wikidata != null) ids.add(Person.WIKIDATA + wikidata);
      if (ipni != null) ids.add(Person.IPNI + ipni);
      if (zoobank != null) ids.add(Person.ZOOBANK + zoobank);
      return ids;
    }

    Person toPerson() {
      return new Person(id, wikidata, ipni, zoobank, List.copyOf(formerIds), family, given, suffix, born, died, activeFrom,
        activeTo, Set.copyOf(groups), source);
    }
  }

  /**
   * @param wikidataRedirects Wikidata items of the files that became a redirect, old Q-id to new Q-id
   */
  public Result merge(PersonFiles.Content existing, List<PersonRecord> records, Map<String, String> wikidataRedirects) {
    List<Draft> drafts = new ArrayList<>();
    Map<String, Draft> index = new HashMap<>();
    for (Person p : existing.persons()) {
      Draft d = Draft.of(p);
      String moved = d.wikidata == null ? null : wikidataRedirects.get(d.wikidata);
      if (moved != null) {
        d.wikidata = moved;
        report.redirected++;
      }
      drafts.add(d);
      d.authorityIds().forEach(id -> index.put(id, d));
    }
    report.existing = drafts.size();

    // Wikidata records carry the links between the authorities, so they go first
    List<PersonRecord> ordered = new ArrayList<>(records);
    ordered.sort(Comparator.comparingInt(r -> r.source() == WIKIDATA ? 0 : 1));
    for (PersonRecord r : ordered) {
      if (r.ids().isEmpty()) {
        report.withoutId++;
        continue;
      }
      attach(r, drafts, index).records.add(r);
    }

    Map<String, String> rekeyed = assignIds(drafts);
    for (Draft d : drafts) {
      fill(d);
    }
    List<PersonName> names = names(existing, drafts, rekeyed);
    Set<String> named = new HashSet<>();
    names.forEach(n -> named.add(rekeyed.getOrDefault(n.person(), n.person())));
    List<Draft> kept = new ArrayList<>();
    for (Draft d : drafts) {
      if (named.contains(d.id) || d.existing) {
        kept.add(d);
        if (!d.existing) report.added++;
      } else {
        report.withoutName++;
      }
    }
    List<PersonRelation> relations = relations(existing, kept, rekeyed);
    return new Result(new PersonFiles.Content(kept.stream().map(Draft::toPerson).toList(), names, relations), report);
  }

  private Draft attach(PersonRecord r, List<Draft> drafts, Map<String, Draft> index) {
    List<Draft> matches = new ArrayList<>();
    for (String id : r.ids()) {
      Draft d = index.get(id);
      if (d == null || matches.contains(d)) continue;
      if (compatible(d, r)) {
        matches.add(d);
      } else {
        // another item of the same authority claims this id: two persons until somebody merges them upstream
        report.ambiguous.add(describe(d) + " and a " + r.source().value() + " record of " + String.join(", ", r.ids())
          + " share " + id);
      }
    }
    Draft d;
    if (matches.isEmpty()) {
      d = new Draft();
      drafts.add(d);
    } else {
      d = matches.get(0);
      for (Draft other : matches.subList(1, matches.size())) {
        if (compatible(d, other)) {
          absorb(d, other, drafts, index);
        } else {
          report.ambiguous.add(describe(other) + " and " + describe(d) + " are both linked by a " + r.source().value()
            + " record of " + String.join(", ", r.ids()));
        }
      }
    }
    link(d, r, index);
    return d;
  }

  private static boolean compatible(Draft a, Draft b) {
    return !differs(a.wikidata, b.wikidata) && !differs(a.ipni, b.ipni) && !differs(a.zoobank, b.zoobank);
  }

  private static boolean compatible(Draft d, PersonRecord r) {
    return !differs(d.wikidata, r.wikidata()) && !differs(d.ipni, r.ipni()) && !differs(d.zoobank, r.zoobank());
  }

  private static boolean differs(@Nullable String a, @Nullable String b) {
    return a != null && b != null && !a.equals(b);
  }

  private static String describe(Draft d) {
    return d.id != null ? d.id : String.join("/", d.authorityIds());
  }

  /** the other draft is the same person: its ids, values and records move over */
  private void absorb(Draft d, Draft other, List<Draft> drafts, Map<String, Draft> index) {
    if (d.wikidata == null) d.wikidata = other.wikidata;
    if (d.ipni == null) d.ipni = other.ipni;
    if (d.zoobank == null) d.zoobank = other.zoobank;
    if (other.id != null) d.formerIds.add(other.id);
    d.formerIds.addAll(other.formerIds);
    if (d.family == null) d.family = other.family;
    if (d.given == null) d.given = other.given;
    if (d.suffix == null) d.suffix = other.suffix;
    if (d.born == null) d.born = other.born;
    if (d.died == null) d.died = other.died;
    if (d.activeFrom == null) d.activeFrom = other.activeFrom;
    if (d.activeTo == null) d.activeTo = other.activeTo;
    if (d.groups.isEmpty()) d.groups.addAll(other.groups);
    if (d.source == null || (!d.existing && other.existing)) d.source = other.source;
    d.existing |= other.existing;
    d.records.addAll(other.records);
    drafts.remove(other);
    d.authorityIds().forEach(id -> index.put(id, d));
    report.merged++;
  }

  /** the draft takes the ids of the record it lacks, unless another person holds them */
  private void link(Draft d, PersonRecord r, Map<String, Draft> index) {
    d.wikidata = linkId(d, d.wikidata, r.wikidata(), Person.WIKIDATA, index);
    d.ipni = linkId(d, d.ipni, r.ipni(), Person.IPNI, index);
    d.zoobank = linkId(d, d.zoobank, r.zoobank(), Person.ZOOBANK, index);
  }

  private String linkId(Draft d, @Nullable String current, @Nullable String value, String prefix, Map<String, Draft> index) {
    if (value == null) return current;
    if (current != null) {
      if (!current.equals(value)) {
        report.ambiguous.add(describe(d) + " has " + prefix + current + " while a record gives " + prefix + value);
      }
      return current;
    }
    Draft holder = index.get(prefix + value);
    if (holder != null && holder != d) {
      return null;
    }
    index.put(prefix + value, d);
    return value;
  }

  /**
   * @return old id to new id of every draft whose id changed
   */
  private static Map<String, String> assignIds(List<Draft> drafts) {
    Map<String, String> rekeyed = new HashMap<>();
    for (Draft d : drafts) {
      if (d.local()) continue;
      String id = Person.idFor(d.wikidata, d.ipni, d.zoobank);
      if (d.id != null && !d.id.equals(id) && !d.formerIds.contains(d.id)) {
        d.formerIds.add(d.id);
      }
      d.id = id;
      d.formerIds.remove(id);
      for (String former : d.formerIds) {
        rekeyed.put(former, id);
      }
    }
    return rekeyed;
  }

  private static int rank(Draft d, Provenance p) {
    List<Provenance> order = d.ipni != null && d.zoobank == null ? List.of(IPNI, WIKIDATA, ZOOBANK)
      : d.zoobank != null && d.ipni == null ? List.of(ZOOBANK, WIKIDATA, IPNI)
      : List.of(WIKIDATA, IPNI, ZOOBANK);
    int i = order.indexOf(p);
    return i < 0 ? order.size() : i;
  }

  private void fill(Draft d) {
    if (d.records.isEmpty()) {
      if (d.existing && !d.local() && d.source != CURATED) {
        report.notSeen.add(d.id);
      }
      return;
    }
    List<PersonRecord> recs = new ArrayList<>(d.records);
    recs.sort(Comparator.comparingInt(r -> rank(d, r.source())));
    BiPredicate<String, String> sameText = String::equalsIgnoreCase;
    BiPredicate<Integer, Integer> sameYear = (a, b) -> Math.abs(a - b) <= YEAR_TOLERANCE;
    d.family = fill(d, "family", d.family, recs, PersonRecord::family, sameText);
    d.given = fill(d, "given", d.given, recs, PersonRecord::given, sameText);
    d.suffix = fill(d, "suffix", d.suffix, recs, PersonRecord::suffix, sameText);
    d.born = fill(d, "born", d.born, recs, PersonRecord::born, sameYear);
    d.died = fill(d, "died", d.died, recs, PersonRecord::died, sameYear);
    d.activeFrom = fill(d, "activeFrom", d.activeFrom, recs, PersonRecord::activeFrom, sameYear);
    d.activeTo = fill(d, "activeTo", d.activeTo, recs, PersonRecord::activeTo, sameYear);
    if (d.groups.isEmpty()) {
      recs.forEach(r -> d.groups.addAll(r.groups()));
    }
    if (d.source == null) {
      d.source = recs.get(0).source();
    }
  }

  private <T> T fill(Draft d, String field, @Nullable T current, List<PersonRecord> recs, Function<PersonRecord, T> getter,
                     BiPredicate<T, T> same) {
    T chosen = null;
    Provenance from = null;
    for (PersonRecord r : recs) {
      T v = getter.apply(r);
      if (v == null) continue;
      if (chosen == null) {
        chosen = v;
        from = r.source();
      } else if (!same.test(chosen, v)) {
        report.conflicts.add(d.id + " " + field + ": " + from.value() + " " + chosen + ", " + r.source().value() + " " + v);
      }
    }
    return current != null ? current : chosen;
  }

  private static List<PersonName> names(PersonFiles.Content existing, List<Draft> drafts, Map<String, String> rekeyed) {
    List<PersonName> names = new ArrayList<>();
    Set<List<Object>> seen = new HashSet<>();
    for (PersonName n : existing.names()) {
      String resolved = rekeyed.getOrDefault(n.person(), n.person());
      PersonName m = n.source() == CURATED ? n : new PersonName(resolved, n.form(), n.kind(), n.code(), n.source());
      if (seen.add(List.of(resolved, n.form(), n.kind(), n.code()))) {
        names.add(m);
      }
    }
    for (Draft d : drafts) {
      for (PersonRecord r : d.records) {
        for (PersonRecord.Form f : r.names()) {
          if (seen.add(List.of(d.id, f.form(), f.kind(), f.code()))) {
            names.add(new PersonName(d.id, f.form(), f.kind(), f.code(), r.source()));
          }
        }
      }
    }
    return names;
  }

  private List<PersonRelation> relations(PersonFiles.Content existing, List<Draft> kept, Map<String, String> rekeyed) {
    Map<String, Draft> byAnyId = new HashMap<>();
    for (Draft d : kept) {
      byAnyId.put(d.id, d);
      d.formerIds.forEach(id -> byAnyId.put(id, d));
      d.authorityIds().forEach(id -> byAnyId.put(id, d));
    }
    List<PersonRelation> relations = new ArrayList<>();
    Set<List<Object>> seen = new HashSet<>();
    for (PersonRelation r : existing.relations()) {
      String a = rekeyed.getOrDefault(r.person(), r.person());
      String b = rekeyed.getOrDefault(r.other(), r.other());
      if (seen.add(key(a, r.relation(), b))) {
        relations.add(r.source() == CURATED ? r : new PersonRelation(a, r.relation(), b, r.source()));
      }
    }
    for (Draft d : kept) {
      for (PersonRecord r : d.records) {
        for (PersonRecord.Link l : r.relations()) {
          Draft other = byAnyId.get(l.other());
          if (other == null || other == d) {
            report.relationsDropped++;
            continue;
          }
          boolean fresh = seen.add(key(d.id, l.relation(), other.id));
          if (l.relation() == RelationType.SIBLING) {
            fresh &= seen.add(key(other.id, l.relation(), d.id));
          }
          if (fresh) {
            relations.add(new PersonRelation(d.id, l.relation(), other.id, r.source()));
          }
        }
      }
    }
    return relations;
  }

  private static List<Object> key(String a, RelationType t, String b) {
    return List.of(a, t, b);
  }
}
