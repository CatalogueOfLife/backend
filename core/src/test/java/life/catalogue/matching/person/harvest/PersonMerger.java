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
 *   <li>Two persons with different ids of one authority are never merged, and a curated person is never merged into
 *   another: a source joining it to another person is reported instead.</li>
 *   <li>An authority is always right about its own id: a record joins the person holding its own id, whatever else it
 *   links to.</li>
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
      drafts.add(d);
      d.authorityIds().forEach(id -> index.put(id, d));
    }
    report.existing = drafts.size();
    // a redirect onto an item another person of the files holds makes the two one person
    for (Draft d : List.copyOf(drafts)) {
      String moved = d.wikidata == null ? null : wikidataRedirects.get(d.wikidata);
      if (moved == null || !drafts.contains(d)) continue;
      Draft holder = index.get(Person.WIKIDATA + moved);
      String old = d.wikidata;
      d.wikidata = moved;
      if (holder != null && holder != d) {
        if (join(holder, d, drafts, index, "a Wikidata redirect of " + old + " to " + moved) == null) {
          d.wikidata = old;
          continue;
        }
      } else {
        index.put(Person.WIKIDATA + moved, d);
      }
      report.redirected++;
    }

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
    List<String> ids = new ArrayList<>(r.ids());
    // an authority is always right about its own id, which comes first
    Draft d = index.get(ids.get(0));
    if (d == null) {
      for (String id : ids.subList(1, ids.size())) {
        Draft o = index.get(id);
        if (o != null && compatible(o, r)) {
          d = o;
          break;
        }
      }
    }
    if (d == null) {
      d = new Draft();
      drafts.add(d);
    }
    for (String id : ids) {
      Draft o = index.get(id);
      if (o == null || o == d) continue;
      // a new draft holds no ids yet, so the record speaks for it
      if (compatible(o, r) && compatible(d, o)) {
        Draft kept = join(d, o, drafts, index, "a " + r.source().value() + " record of " + String.join(", ", ids));
        d = kept == null ? d : kept;
      } else {
        report.ambiguous.add(describe(o) + " and " + describe(d) + " are both linked by a " + r.source().value()
          + " record of " + String.join(", ", ids) + ", sharing " + id);
      }
    }
    link(d, r, index);
    linkOtherIds(d, r, drafts, index);
    return d;
  }

  /**
   * An authority may give a person a second id of another authority - Wikidata lists two IPNI ids for IPNI's duplicate
   * records of one author. It becomes a former id, and a person holding it is joined. One another item holds as its own
   * stays with that item and is reported.
   */
  private void linkOtherIds(Draft d, PersonRecord r, List<Draft> drafts, Map<String, Draft> index) {
    for (String id : r.otherIds()) {
      Draft o = index.get(id);
      if (o != null && o != d) {
        if (differs(o.wikidata, d.wikidata)) {
          report.ambiguous.add(describe(o) + " holds " + id + ", which a " + r.source().value() + " record gives "
            + describe(d) + " as a second id");
          continue;
        }
        addFormer(d, id);
        if (join(d, o, drafts, index, "the second id " + id) == null) {
          d.formerIds.remove(id);
          continue;
        }
      } else {
        addFormer(d, id);
      }
      index.put(id, d);
    }
  }

  private static void addFormer(Draft d, @Nullable String id) {
    if (id != null && !id.equals(d.id) && !d.formerIds.contains(id)) {
      d.formerIds.add(id);
    }
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

  /**
   * Makes two drafts one person: the ids, values and records of the other move over to keep, which keeps its own values
   * and reports every one of the other it drops. A curated person is never joined, its line would change: the link is
   * reported instead.
   *
   * @return the draft that stays, null if the two were not joined
   */
  @Nullable
  private Draft join(Draft keep, Draft other, List<Draft> drafts, Map<String, Draft> index, String why) {
    if (keep.source == CURATED || other.source == CURATED) {
      report.ambiguous.add(describe(keep) + " and " + describe(other) + " are linked by " + why
        + ", but a curated person is never joined");
      return null;
    }
    // a redirect or a second id of an authority joins without asking whether the ids agree: a dropped id becomes a
    // former id, so it still finds keep, and is reported unless an authority gave it as a second id of the person
    keep.wikidata = keepId(keep, other, "wikidata", Person.WIKIDATA, keep.wikidata, other.wikidata);
    keep.ipni = keepId(keep, other, "ipni", Person.IPNI, keep.ipni, other.ipni);
    keep.zoobank = keepId(keep, other, "zoobank", Person.ZOOBANK, keep.zoobank, other.zoobank);
    addFormer(keep, other.id);
    other.formerIds.forEach(id -> addFormer(keep, id));
    keep.family = keep(keep, other, "family", keep.family, other.family);
    keep.given = keep(keep, other, "given", keep.given, other.given);
    keep.suffix = keep(keep, other, "suffix", keep.suffix, other.suffix);
    keep.born = keep(keep, other, "born", keep.born, other.born);
    keep.died = keep(keep, other, "died", keep.died, other.died);
    keep.activeFrom = keep(keep, other, "activeFrom", keep.activeFrom, other.activeFrom);
    keep.activeTo = keep(keep, other, "activeTo", keep.activeTo, other.activeTo);
    if (keep.groups.isEmpty()) keep.groups.addAll(other.groups);
    if (keep.source == null || (!keep.existing && other.existing)) keep.source = other.source;
    keep.existing |= other.existing;
    keep.records.addAll(other.records);
    drafts.remove(other);
    other.authorityIds().forEach(id -> index.put(id, keep));
    keep.authorityIds().forEach(id -> index.put(id, keep));
    report.merged++;
    return keep;
  }

  private String keepId(Draft keep, Draft other, String field, String prefix, @Nullable String kept, @Nullable String dropped) {
    if (kept == null) return dropped;
    if (dropped != null && !kept.equals(dropped)) {
      if (!keep.formerIds.contains(prefix + dropped)) {
        report.conflicts.add(describe(keep) + " " + field + ": kept " + kept + ", dropped " + dropped + " of " + describe(other));
      }
      addFormer(keep, prefix + dropped);
    }
    return kept;
  }

  private <T> T keep(Draft keep, Draft other, String field, @Nullable T kept, @Nullable T dropped) {
    if (kept == null) return dropped;
    if (dropped != null && !kept.equals(dropped)) {
      report.conflicts.add(describe(keep) + " " + field + ": kept " + kept + ", dropped " + dropped + " of " + describe(other));
    }
    return kept;
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
      if (!current.equals(value) && !d.formerIds.contains(prefix + value)) {
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
    Integer[] before = {d.born, d.died, d.activeFrom, d.activeTo};
    d.born = fill(d, "born", d.born, recs, PersonRecord::born, sameYear);
    d.died = fill(d, "died", d.died, recs, PersonRecord::died, sameYear);
    d.activeFrom = fill(d, "activeFrom", d.activeFrom, recs, PersonRecord::activeFrom, sameYear);
    d.activeTo = fill(d, "activeTo", d.activeTo, recs, PersonRecord::activeTo, sameYear);
    // years of two sources, or a source's own error, must not make an impossible person: unknown beats wrong
    String impossible = d.born != null && d.died != null && d.born > d.died ? "born " + d.born + " after died " + d.died
      : d.born != null && d.activeFrom != null && d.activeFrom < d.born ? "active from " + d.activeFrom + " before born " + d.born
      : null;
    if (impossible != null) {
      report.conflicts.add(d.id + " " + impossible + ": the filled years are left out");
      d.born = before[0];
      d.died = before[1];
      d.activeFrom = before[2];
      d.activeTo = before[3];
    }
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
    if (current != null && chosen != null && !same.test(current, chosen)) {
      report.changed.add(d.id + " " + field + ": files " + current + ", " + from.value() + " " + chosen);
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
