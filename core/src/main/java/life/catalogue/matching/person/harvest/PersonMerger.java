package life.catalogue.matching.person.harvest;

import life.catalogue.api.model.Person;
import life.catalogue.api.model.PersonName;
import life.catalogue.api.model.PersonRelation;
import life.catalogue.api.vocab.PersonRelationType;
import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.person.*;

import java.time.LocalDate;
import java.util.*;
import java.util.function.BiPredicate;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import static life.catalogue.api.vocab.PersonSource.*;

/**
 * Merges the records of a harvest into the registry, following the sources.
 * <ul>
 *   <li>Records and persons are joined on shared authority ids only, never on names.</li>
 *   <li>Every harvested value, form and relation is what its sources say now: a changed year, name or group is updated,
 *   a form or relation no source gives any more goes. The registry of before only carries the ids on: former ids,
 *   Wikidata redirects, joins of two persons.</li>
 *   <li>Curated persons, forms and relations win: a curated person keeps its values as they are, empty ones included,
 *   and what the sources say otherwise is only reported.</li>
 *   <li>A person no source has any more, or none names, is kept and retired: it keeps its ids and values and loses its
 *   harvested forms. One a source names again is no longer retired.</li>
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
  private final LocalDate today;

  public PersonMerger() {
    this(LocalDate.now());
  }

  /**
   * @param today the day a person no source has any more is retired on
   */
  public PersonMerger(LocalDate today) {
    this.today = today;
  }

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
    PersonSource source;
    boolean existing;
    LocalDate retired;
    String successor;
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
      d.retired = p.retired();
      d.successor = p.successor();
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
        activeTo, Set.copyOf(groups), source, retired, successor);
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

    assignIds(drafts);
    for (Draft d : drafts) {
      fill(d);
    }
    // every id a draft answers to, for the lines that refer to a person by another than its own
    Map<String, String> own = new HashMap<>();
    for (Draft d : drafts) {
      d.formerIds.forEach(id -> own.put(id, d.id));
      d.authorityIds().forEach(id -> own.put(id, d.id));
      own.put(d.id, d.id);
    }
    Set<String> before = new HashSet<>();
    drafts.stream().filter(d -> d.existing).forEach(d -> before.add(d.id));
    List<PersonName> names = names(existing, drafts, own, before);
    Set<String> named = new HashSet<>();
    names.forEach(n -> named.add(own.getOrDefault(n.person(), n.person())));
    List<Draft> kept = new ArrayList<>();
    for (Draft d : drafts) {
      if (d.existing) {
        kept.add(d);
        retire(d, named.contains(d.id));
      } else if (named.contains(d.id)) {
        kept.add(d);
        report.added++;
        report.addedPersons.add(d.id);
      } else {
        report.withoutName++;
      }
    }
    List<PersonRelation> relations = relations(existing, kept, own, before);
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
    if (other.existing) {
      report.joined.add(describe(other) + " into " + describe(keep) + ": " + why);
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
  private static void assignIds(List<Draft> drafts) {
    for (Draft d : drafts) {
      if (d.local()) continue;
      String id = Person.idFor(d.wikidata, d.ipni, d.zoobank);
      if (d.id != null && !d.id.equals(id) && !d.formerIds.contains(d.id)) {
        d.formerIds.add(d.id);
      }
      d.id = id;
      d.formerIds.remove(id);
    }
  }

  private static int rank(Draft d, PersonSource p) {
    List<PersonSource> order = d.ipni != null && d.zoobank == null ? List.of(IPNI, WIKIDATA, ZOOBANK)
      : d.zoobank != null && d.ipni == null ? List.of(ZOOBANK, WIKIDATA, IPNI)
      : List.of(WIKIDATA, IPNI, ZOOBANK);
    int i = order.indexOf(p);
    return i < 0 ? order.size() : i;
  }

  /**
   * The values of a person are what its best ranked sources say now. A curated person keeps its own, and a person no
   * source has keeps the ones it had.
   */
  private void fill(Draft d) {
    if (d.records.isEmpty()) return;
    List<PersonRecord> recs = new ArrayList<>(d.records);
    recs.sort(Comparator.comparingInt(r -> rank(d, r.source())));
    BiPredicate<String, String> sameText = String::equalsIgnoreCase;
    BiPredicate<Integer, Integer> sameYear = (a, b) -> Math.abs(a - b) <= YEAR_TOLERANCE;
    String family = pick(d, "family", recs, PersonRecord::family, sameText);
    String given = pick(d, "given", recs, PersonRecord::given, sameText);
    String suffix = pick(d, "suffix", recs, PersonRecord::suffix, sameText);
    Integer born = pick(d, "born", recs, PersonRecord::born, sameYear);
    Integer died = pick(d, "died", recs, PersonRecord::died, sameYear);
    Integer activeFrom = pick(d, "activeFrom", recs, PersonRecord::activeFrom, sameYear);
    Integer activeTo = pick(d, "activeTo", recs, PersonRecord::activeTo, sameYear);
    Set<TaxGroup> groups = EnumSet.noneOf(TaxGroup.class);
    recs.forEach(r -> groups.addAll(r.groups()));
    if (d.source == CURATED) {
      // a curated line wins as a whole, what the sources say otherwise is only reported
      kept(d, "family", d.family, family);
      kept(d, "given", d.given, given);
      kept(d, "suffix", d.suffix, suffix);
      kept(d, "born", d.born, born);
      kept(d, "died", d.died, died);
      kept(d, "activeFrom", d.activeFrom, activeFrom);
      kept(d, "activeTo", d.activeTo, activeTo);
      kept(d, "groups", d.groups, groups.isEmpty() ? null : groups);
      return;
    }
    // years of two sources, or a source's own error, must not make an impossible person: unknown beats wrong
    String impossible = born != null && died != null && born > died ? "born " + born + " after died " + died
      : born != null && activeFrom != null && activeFrom < born ? "active from " + activeFrom + " before born " + born
      : null;
    if (impossible != null) {
      report.conflicts.add(d.id + " " + impossible + ": the years are left out");
      born = null;
      died = null;
      activeFrom = null;
      activeTo = null;
    }
    d.family = follow(d, "family", d.family, family);
    d.given = follow(d, "given", d.given, given);
    d.suffix = follow(d, "suffix", d.suffix, suffix);
    d.born = follow(d, "born", d.born, born);
    d.died = follow(d, "died", d.died, died);
    d.activeFrom = follow(d, "activeFrom", d.activeFrom, activeFrom);
    d.activeTo = follow(d, "activeTo", d.activeTo, activeTo);
    if (!groups.equals(d.groups)) {
      follow(d, "groups", Set.copyOf(d.groups), groups);
      d.groups.clear();
      d.groups.addAll(groups);
    }
    PersonSource source = recs.get(0).source();
    if (d.source != null && d.source != source) {
      follow(d, "source", d.source.value(), source.value());
    }
    d.source = source;
  }

  /**
   * @return the value of the best ranked record that has one; values other records give otherwise are reported
   */
  @Nullable
  private <T> T pick(Draft d, String field, List<PersonRecord> recs, Function<PersonRecord, T> getter, BiPredicate<T, T> same) {
    T chosen = null;
    PersonSource from = null;
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
    return chosen;
  }

  /**
   * @return the value the sources give now, a change to a person of before reported
   */
  @Nullable
  private <T> T follow(Draft d, String field, @Nullable T current, @Nullable T now) {
    if (d.existing && !Objects.equals(current, now)) {
      report.changed.add(d.id + " " + field + ": " + current + " -> " + now);
    }
    return now;
  }

  private void kept(Draft d, String field, @Nullable Object curated, @Nullable Object sources) {
    if (sources != null && !sources.equals(curated)) {
      report.curatedKept.add(d.id + " " + field + ": curated " + curated + ", sources " + sources);
    }
  }

  /**
   * A person no source has any more, or none names, is retired on this day: it keeps its row and ids and is found by
   * them only. One a source names again is no longer retired. Curated and local persons are never retired.
   */
  private void retire(Draft d, boolean named) {
    if (d.local() || d.source == CURATED) return;
    boolean gone = d.records.isEmpty() || !named;
    if (gone && d.retired == null) {
      d.retired = today;
      report.retired.add(d.records.isEmpty() ? d.id : d.id + ": no source names it");
    } else if (!gone && d.retired != null) {
      d.retired = null;
      report.unretired.add(d.id);
    }
  }

  /**
   * Curated forms stay as written, harvested ones are what the records give now. What changed for the persons of before
   * is reported.
   */
  private List<PersonName> names(PersonFiles.Content existing, List<Draft> drafts, Map<String, String> own, Set<String> before) {
    List<PersonName> names = new ArrayList<>();
    Set<List<Object>> seen = new HashSet<>();
    Set<List<Object>> old = new LinkedHashSet<>();
    for (PersonName n : existing.names()) {
      List<Object> k = List.of(own.getOrDefault(n.person(), n.person()), n.form(), n.kind(), n.code());
      if (n.source() == CURATED) {
        if (seen.add(k)) {
          names.add(n);
        }
      } else {
        old.add(k);
      }
    }
    Set<List<Object>> harvested = new LinkedHashSet<>();
    for (Draft d : drafts) {
      for (PersonRecord r : d.records) {
        for (PersonRecord.Form f : r.names()) {
          List<Object> k = List.of(d.id, f.form(), f.kind(), f.code());
          if (seen.add(k)) {
            names.add(new PersonName(d.id, f.form(), f.kind(), f.code(), r.source()));
            harvested.add(k);
          }
        }
      }
    }
    diff(old, harvested, seen, before, report.formsAdded, report.formsRemoved);
    return names;
  }

  /**
   * Curated relations stay as written, harvested ones are what the records give now.
   */
  private List<PersonRelation> relations(PersonFiles.Content existing, List<Draft> kept, Map<String, String> own,
                                         Set<String> before) {
    Map<String, Draft> byAnyId = new HashMap<>();
    for (Draft d : kept) {
      byAnyId.put(d.id, d);
      d.formerIds.forEach(id -> byAnyId.put(id, d));
      d.authorityIds().forEach(id -> byAnyId.put(id, d));
    }
    List<PersonRelation> relations = new ArrayList<>();
    Set<List<Object>> seen = new HashSet<>();
    Set<List<Object>> old = new LinkedHashSet<>();
    for (PersonRelation r : existing.relations()) {
      List<Object> k = key(own.getOrDefault(r.person(), r.person()), r.relation(), own.getOrDefault(r.other(), r.other()));
      if (r.source() == CURATED) {
        if (seen.add(k)) {
          relations.add(r);
        }
      } else {
        old.add(k);
      }
    }
    Set<List<Object>> harvested = new LinkedHashSet<>();
    for (Draft d : kept) {
      for (PersonRecord r : d.records) {
        for (PersonRecord.Link l : r.relations()) {
          Draft other = byAnyId.get(l.other());
          if (other == null || other == d) {
            report.relationsDropped++;
            continue;
          }
          List<Object> k = key(d.id, l.relation(), other.id);
          boolean fresh = seen.add(k);
          if (l.relation() == PersonRelationType.SIBLING) {
            fresh &= seen.add(key(other.id, l.relation(), d.id));
          }
          if (fresh) {
            relations.add(new PersonRelation(d.id, l.relation(), other.id, r.source()));
            harvested.add(k);
          }
        }
      }
    }
    diff(old, harvested, seen, before, report.relationsAdded, report.relationsRemoved);
    return relations;
  }

  /**
   * Reports the harvested lines of persons of before that are new, and the old harvested lines no source and no curated
   * line gives any more. A line is its key, whose first element is the person.
   */
  private static void diff(Set<List<Object>> old, Set<List<Object>> harvested, Set<List<Object>> seen, Set<String> before,
                           List<String> added, List<String> removed) {
    for (List<Object> k : harvested) {
      if (before.contains((String) k.get(0)) && !old.contains(k)) {
        added.add(line(k));
      }
    }
    for (List<Object> k : old) {
      if (!harvested.contains(k) && !seen.contains(k)) {
        removed.add(line(k));
      }
    }
  }

  private static String line(List<Object> k) {
    return k.stream().map(Object::toString).collect(Collectors.joining(" "));
  }

  private static List<Object> key(String a, PersonRelationType t, String b) {
    return List.of(a, t, b);
  }
}
