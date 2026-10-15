package life.catalogue.resources.matching.gbif;

import life.catalogue.api.model.Dataset;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.model.SimpleNameCached;
import life.catalogue.api.vocab.TaxonomicStatus;
import life.catalogue.common.tax.AuthorshipNormalizer;
import life.catalogue.matching.UsageMatcher;
import life.catalogue.matching.UsageMatcherMemStore;
import life.catalogue.matching.nidx.NameIndex;
import life.catalogue.matching.nidx.NameIndexFactory;
import life.catalogue.matching.nidx.NamesIndexConfig;
import life.catalogue.parser.NameParser;
import life.catalogue.resources.matching.GbifSpeciesMatchResource;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * The test index of GBIF's matching-ws (https://github.com/gbif/matching-ws, Apache License 2.0) as an in-memory
 * ChecklistBank matcher store. The fixtures are GBIF species match responses: every usage, classification entry and
 * alternative in them becomes one usage, following the rules of matching-ws' own MatchingTestConfiguration,
 * so the ported test cases see the same index.
 */
public class MatchingWsIndex {
  private static final String DIR = "matching-ws/index/";
  private static final ObjectMapper MAPPER = new ObjectMapper();
  // flat v1 rank fields with their keys, highest first
  private static final List<Rank> V1_RANKS = List.of(Rank.KINGDOM, Rank.PHYLUM, Rank.CLASS, Rank.ORDER, Rank.FAMILY, Rank.GENUS, Rank.SPECIES);

  private final Map<String, SimpleNameCached> usages = new HashMap<>();

  /**
   * @return a resource for the GBIF compatible species match over all matching-ws index fixtures
   */
  public static GbifSpeciesMatchResource resource() throws IOException {
    var idx = new MatchingWsIndex();
    idx.loadV1();
    idx.loadV2();
    NameIndex ni = NameIndexFactory.build(NamesIndexConfig.memory(1024), null, AuthorshipNormalizer.createWithoutAuthormap()).started();
    var store = new UsageMatcherMemStore(1);
    for (var u : idx.usages.values()) {
      var m = ni.match(SimpleName.sn(u.getRank(), u.getName(), u.getAuthorship()), true, false);
      u.setNamesIndexId(m.getNidx());
      store.add(u);
    }
    var d = new Dataset();
    d.setKey(1);
    d.setTitle("matching-ws test index");
    return new GbifSpeciesMatchResource(d, new UsageMatcher(1, ni, store, true), null);
  }

  private static JsonNode read(String file) throws IOException {
    try (InputStream in = MatchingWsIndex.class.getClassLoader().getResourceAsStream(DIR + file)) {
      return in == null ? null : MAPPER.readTree(in);
    }
  }

  private static String text(JsonNode n, String field) {
    var v = n.get(field);
    return v == null || v.isNull() ? null : v.asText();
  }

  private static boolean isViral(String name) {
    if (name == null) return false;
    var pnu = NameParser.PARSER.parse(SimpleName.sn(name));
    return pnu.isPresent() && pnu.get().getName().getCode() == NomCode.VIRUS;
  }

  /**
   * Keeps the first usage of an id, unless only a later one carries an authorship.
   */
  private void put(Map<String, SimpleNameCached> map, SimpleNameCached u) {
    var existing = map.get(u.getId());
    if (existing == null || existing.getAuthorship() == null && u.getAuthorship() != null) {
      map.put(u.getId(), u);
    }
  }

  private static SimpleNameCached usage(String id, String name, String authorship, String rank, TaxonomicStatus status, String parent) {
    var u = new SimpleNameCached(id, name, rank == null ? Rank.UNRANKED : Rank.valueOf(rank.toUpperCase()));
    u.setAuthorship(authorship);
    u.setStatus(status);
    u.setParent(parent);
    return u;
  }

  /**
   * nubNN.json: flat v1 responses with their alternatives, see MatchingTestConfiguration.loadIndexFromV1Responses
   */
  private void loadV1() throws IOException {
    for (int i = 1; i < 300; i++) {
      var m = read("nub" + i + ".json");
      if (m == null) continue;
      Map<String, SimpleNameCached> local = new HashMap<>();
      addV1(local, m);
      var alts = m.get("alternatives");
      if (alts != null) {
        for (var a : alts) {
          addV1(local, a);
        }
      }
      local.values().forEach(u -> put(usages, u));
    }
  }

  private void addV1(Map<String, SimpleNameCached> local, JsonNode m) {
    boolean synonym = m.path("synonym").asBoolean(false);
    TaxonomicStatus status = synonym ? TaxonomicStatus.SYNONYM
      : "DOUBTFUL".equals(text(m, "status")) ? TaxonomicStatus.PROVISIONALLY_ACCEPTED : TaxonomicStatus.ACCEPTED;
    String key = text(m, "usageKey");
    // the usage itself always replaces what an earlier entry of the same file said about it
    local.put(key, parsedUsage(key, text(m, "scientificName"), text(m, "rank"), status, v1Parent(m, synonym)));
    // the flat classification
    for (var r : V1_RANKS) {
      String rkey = text(m, keyField(r));
      if (rkey != null && !local.containsKey(rkey)) {
        local.put(rkey, usage(rkey, text(m, nameField(r)), null, r.name(), TaxonomicStatus.ACCEPTED, v1ParentKey(m, r)));
      }
    }
  }

  private static String keyField(Rank r) {
    return r.name().toLowerCase() + "Key";
  }

  private static String nameField(Rank r) {
    return r == Rank.CLASS ? "class" : r.name().toLowerCase();
  }

  /**
   * The parent of the usage itself, see MatchingTestConfiguration.setParent
   */
  private static String v1Parent(JsonNode m, boolean synonym) {
    String rank = text(m, "rank");
    if (synonym) {
      String acc = text(m, "acceptedUsageKey");
      if (acc != null) return acc;
      if (rank != null) {
        for (var r : V1_RANKS) {
          if (r != Rank.KINGDOM && rank.equals(r.name()) && text(m, keyField(r)) != null) {
            return text(m, keyField(r));
          }
        }
      }
      return null;
    }
    if (rank == null) return null;
    Rank rnk = Rank.valueOf(rank.toUpperCase());
    if (rnk == Rank.SUBSPECIES && text(m, "speciesKey") != null) return text(m, "speciesKey");
    if (rnk == Rank.SPECIES && text(m, "genusKey") != null) return text(m, "genusKey");
    // the family key for genus and below, the order key for family and below, and so on - so a subfamily
    // hangs from the order, as in matching-ws
    Rank[][] thresholds = {{Rank.GENUS, Rank.FAMILY}, {Rank.FAMILY, Rank.ORDER}, {Rank.ORDER, Rank.CLASS},
      {Rank.CLASS, Rank.PHYLUM}, {Rank.PHYLUM, Rank.KINGDOM}};
    for (Rank[] t : thresholds) {
      if (rnk.ordinal() >= t[0].ordinal() && text(m, keyField(t[1])) != null) {
        return text(m, keyField(t[1]));
      }
    }
    return null;
  }

  /**
   * The parent of a flat classification entry, the nearest key below it that is neither the usage nor its accepted name,
   * see MatchingTestConfiguration.getParentKey
   */
  private static String v1ParentKey(JsonNode m, Rank aboveRank) {
    String usageKey = text(m, "usageKey");
    String acceptedKey = text(m, "acceptedUsageKey");
    List<Rank> ranks = List.of(Rank.SPECIES, Rank.GENUS, Rank.FAMILY, Rank.ORDER, Rank.CLASS, Rank.PHYLUM, Rank.KINGDOM);
    for (var r : ranks) {
      String k = text(m, keyField(r));
      // matching-ws compares the species key against the genus key here, an upstream quirk kept for the same index
      String compared = r == Rank.SPECIES ? text(m, "genusKey") : k;
      if (aboveRank.ordinal() > r.ordinal() && k != null && !k.equals(usageKey) && !Objects.equals(compared, acceptedKey)) {
        return k;
      }
    }
    return null;
  }

  /**
   * matchNN.json: v2 responses with their alternatives, see MatchingTestConfiguration.loadIndexFromV2Responses
   */
  private void loadV2() throws IOException {
    for (int i = 1; i < 10; i++) {
      var m = read("match" + i + ".json");
      if (m == null) continue;
      Map<String, SimpleNameCached> local = new HashMap<>(addV2(m));
      var alts = m.path("diagnostics").get("alternatives");
      if (alts != null) {
        for (var a : alts) {
          local.putAll(addV2(a));
        }
      }
      local.values().forEach(u -> put(usages, u));
    }
  }

  private static Map<String, SimpleNameCached> addV2(JsonNode m) {
    Map<String, SimpleNameCached> local = new HashMap<>();
    String pid = null;
    // the classification first as it has the least details and gets overwritten below
    var cl = m.get("classification");
    if (cl != null) {
      for (var rn : cl) {
        local.put(text(rn, "key"), usage(text(rn, "key"), text(rn, "name"), null, text(rn, "rank"), TaxonomicStatus.ACCEPTED, pid));
        pid = text(rn, "key");
      }
    }
    var acc = m.get("acceptedUsage");
    if (acc != null && !acc.isNull()) {
      String key = text(acc, "key");
      var known = local.get(key);
      local.put(key, v2Usage(acc, known == null ? null : known.getParent(), TaxonomicStatus.ACCEPTED));
    }
    var u = m.get("usage");
    if (u != null && !u.isNull()) {
      String key = text(u, "key");
      var known = local.get(key);
      String parent = acc != null && !acc.isNull() ? text(acc, "key") : known == null ? null : known.getParent();
      var status = m.path("synonym").asBoolean(false) ? TaxonomicStatus.SYNONYM : TaxonomicStatus.ACCEPTED;
      local.put(key, v2Usage(u, parent, status));
    }
    return local;
  }

  private static SimpleNameCached v2Usage(JsonNode u, String parent, TaxonomicStatus status) {
    return parsedUsage(text(u, "key"), text(u, "name"), text(u, "rank"), status, parent);
  }

  /**
   * matching-ws cuts the canonical name off the full name to get the authorship, which garbles names with a rank
   * marker. The ChecklistBank parser splits the full name instead, as ChecklistBank stores it: the scientific name
   * with its rank marker, the authorship apart. Viral names stay whole.
   */
  private static SimpleNameCached parsedUsage(String id, String fullName, String rank, TaxonomicStatus status, String parent) {
    String name = fullName;
    String authorship = null;
    if (fullName != null && !isViral(fullName)) {
      Rank rnk = rank == null ? null : Rank.valueOf(rank.toUpperCase());
      var pnu = NameParser.PARSER.parse(SimpleName.sn(rnk, fullName, null));
      if (pnu.isPresent() && pnu.get().getName().getScientificName() != null) {
        name = pnu.get().getName().getScientificName();
        authorship = pnu.get().getName().getAuthorship();
      }
    }
    return usage(id, name, authorship, rank, status, parent);
  }
}
