package life.catalogue.api.model;

import life.catalogue.api.vocab.PersonSource;
import life.catalogue.api.vocab.TaxGroup;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import javax.annotation.Nullable;

/**
 * An author as an authority knows them.
 *
 * @param id        the best identifier the person has, prefixed: wd:, else ipni:, else zb:, else a curated clb:
 * @param formerIds ids the person had before it gained a better one, so lines that refer to them keep resolving
 * @param family    the family name as an authority records it, never split off a full name
 * @param suffix    a generation or filius: "II", "Jr.", "f."
 * @param groups    the taxonomic groups an authority records the person worked on, never mined from names
 * @param retired   the day a harvest found no source having or naming the person any more, null while one does. A
 *                  retired person keeps its ids and is found by them, but no longer by its harvested or derived forms
 * @param successor the id of the person a retired one was joined into, where known
 */
public record Person(
  String id,
  @Nullable String wikidata,
  @Nullable String ipni,
  @Nullable String zoobank,
  List<String> formerIds,
  @Nullable String family,
  @Nullable String given,
  @Nullable String suffix,
  @Nullable Integer born,
  @Nullable Integer died,
  @Nullable Integer activeFrom,
  @Nullable Integer activeTo,
  Set<TaxGroup> groups,
  PersonSource source,
  @Nullable LocalDate retired,
  @Nullable String successor
) {
  public static final String WIKIDATA = "wd:";
  public static final String IPNI = "ipni:";
  public static final String ZOOBANK = "zb:";
  public static final String LOCAL = "clb:";

  /**
   * A person no harvest retired.
   */
  public Person(String id, @Nullable String wikidata, @Nullable String ipni, @Nullable String zoobank, List<String> formerIds,
                @Nullable String family, @Nullable String given, @Nullable String suffix, @Nullable Integer born,
                @Nullable Integer died, @Nullable Integer activeFrom, @Nullable Integer activeTo, Set<TaxGroup> groups,
                PersonSource source) {
    this(id, wikidata, ipni, zoobank, formerIds, family, given, suffix, born, died, activeFrom, activeTo, groups, source, null, null);
  }

  /**
   * @return the id of a person with these authority ids: the best of them, prefixed. Null without any
   */
  @Nullable
  public static String idFor(@Nullable String wikidata, @Nullable String ipni, @Nullable String zoobank) {
    if (wikidata != null) return WIKIDATA + wikidata;
    if (ipni != null) return IPNI + ipni;
    if (zoobank != null) return ZOOBANK + zoobank;
    return null;
  }

  /**
   * @return every id the person can be referred to by: its id, its former ids and its prefixed authority ids
   */
  public Set<String> allIds() {
    Set<String> ids = new LinkedHashSet<>();
    ids.add(id);
    ids.addAll(formerIds);
    if (wikidata != null) ids.add(WIKIDATA + wikidata);
    if (ipni != null) ids.add(IPNI + ipni);
    if (zoobank != null) ids.add(ZOOBANK + zoobank);
    return ids;
  }
}
