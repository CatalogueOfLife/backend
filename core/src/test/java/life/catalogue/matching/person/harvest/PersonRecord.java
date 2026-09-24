package life.catalogue.matching.person.harvest;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.person.FormCode;
import life.catalogue.matching.person.NameKind;
import life.catalogue.matching.person.Person;
import life.catalogue.matching.person.Provenance;
import life.catalogue.matching.person.RelationType;

import java.util.*;

import javax.annotation.Nullable;

/**
 * One person as one authority knows them.
 *
 * @param otherIds  further prefixed ids the source gives the person, e.g. a second IPNI id of one Wikidata item: IPNI's
 *                  duplicate records of one author
 * @param relations to other persons by a prefixed authority id of the same source, e.g. wd:Q42
 */
public record PersonRecord(
  Provenance source,
  @Nullable String wikidata,
  @Nullable String ipni,
  @Nullable String zoobank,
  Set<String> otherIds,
  @Nullable String family,
  @Nullable String given,
  @Nullable String suffix,
  @Nullable Integer born,
  @Nullable Integer died,
  @Nullable Integer activeFrom,
  @Nullable Integer activeTo,
  Set<TaxGroup> groups,
  List<Form> names,
  List<Link> relations
) {
  public record Form(String form, NameKind kind, FormCode code) {
  }

  public record Link(RelationType relation, String other) {
  }

  /**
   * @return the prefixed authority ids, the one of the record's own source first
   */
  public Set<String> ids() {
    Set<String> ids = new LinkedHashSet<>();
    String own = switch (source) {
      case WIKIDATA -> wikidata == null ? null : Person.WIKIDATA + wikidata;
      case IPNI -> ipni == null ? null : Person.IPNI + ipni;
      case ZOOBANK -> zoobank == null ? null : Person.ZOOBANK + zoobank;
      case CURATED -> null;
    };
    if (own != null) ids.add(own);
    if (wikidata != null) ids.add(Person.WIKIDATA + wikidata);
    if (ipni != null) ids.add(Person.IPNI + ipni);
    if (zoobank != null) ids.add(Person.ZOOBANK + zoobank);
    return ids;
  }

  /**
   * Collects a person across several answers of a source.
   */
  public static final class Builder {
    final Provenance source;
    public String wikidata;
    public String ipni;
    public String zoobank;
    public String suffix;
    final Set<String> otherIds = new LinkedHashSet<>();
    String label;
    final List<String> family = new ArrayList<>();
    final List<String> given = new ArrayList<>();
    Integer born;
    Integer died;
    Integer activeFrom;
    Integer activeTo;
    final Set<TaxGroup> groups = EnumSet.noneOf(TaxGroup.class);
    final List<Form> names = new ArrayList<>();
    final List<Link> relations = new ArrayList<>();

    public Builder(Provenance source) {
      this.source = source;
    }

    /** the full name the person is known by, which also orders family and given names */
    void label(String label) {
      this.label = label;
      name(label, NameKind.FULL, FormCode.ANY);
    }

    void name(@Nullable String form, NameKind kind, FormCode code) {
      if (form == null || form.isBlank()) return;
      Form f = new Form(form.trim(), kind, code);
      if (!names.contains(f)) {
        names.add(f);
      }
    }

    /** a second id of an authority for the same person, prefixed */
    public void otherId(String prefixedId) {
      otherIds.add(prefixedId);
    }

    void family(@Nullable String x) {
      if (x != null && !x.isBlank() && !family.contains(x.trim())) family.add(x.trim());
    }

    void given(@Nullable String x) {
      if (x != null && !x.isBlank() && !given.contains(x.trim())) given.add(x.trim());
    }

    void born(@Nullable Integer y) {
      born = min(born, y);
    }

    void died(@Nullable Integer y) {
      died = max(died, y);
    }

    void activeFrom(@Nullable Integer y) {
      activeFrom = min(activeFrom, y);
    }

    void activeTo(@Nullable Integer y) {
      activeTo = max(activeTo, y);
    }

    void link(RelationType type, String other) {
      Link l = new Link(type, other);
      if (!relations.contains(l)) {
        relations.add(l);
      }
    }

    // no ternaries here: mixing Integer and int in one unboxes a null into a NullPointerException
    private static Integer min(Integer a, Integer b) {
      if (a == null) return b;
      if (b == null) return a;
      return Math.min(a, b);
    }

    private static Integer max(Integer a, Integer b) {
      if (a == null) return b;
      if (b == null) return a;
      return Math.max(a, b);
    }

    public PersonRecord build() {
      // insertion ordered: the merger joins in this order, and Set.copyOf iterates in an order that changes between runs
      return new PersonRecord(source, wikidata, ipni, zoobank, Collections.unmodifiableSet(new LinkedHashSet<>(otherIds)),
        Names.family(family, label), Names.ordered(given, label),
        suffix != null ? suffix : Names.suffix(label), born, died, activeFrom, activeTo, Set.copyOf(groups),
        List.copyOf(names), List.copyOf(relations));
    }
  }
}
