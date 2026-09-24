package life.catalogue.matching.person.harvest;

import life.catalogue.api.model.Person;
import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonRelationType;
import life.catalogue.api.vocab.PersonSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Persons from Wikidata: everyone with a botanist author abbreviation (P428), a zoologist author citation (P835), an
 * IPNI author id (P586) or a ZooBank author id (P2006), the ids paged one property at a time through the query service.
 * Everything else comes from the Wikidata API, 50 persons at a time: the English label and aliases, and the statements
 * family and given names (P734, P735), birth and death (P569, P570), active years (P2031, P2032, P1317), field of work
 * (P101), parents (P22, P25) and siblings (P3373), deprecated ones left out. The labels of the name and field items
 * follow. The query service took half a minute to a minute for the statements or labels of a hundred persons.
 */
public class WikidataPersonSource implements HarvestSource {
  private static final Logger LOG = LoggerFactory.getLogger(WikidataPersonSource.class);
  static final String SPARQL = "https://query.wikidata.org/sparql";
  static final String API = "https://www.wikidata.org/w/api.php";
  static final String ENTITY = "http://www.wikidata.org/entity/";
  static final int PAGE = 5000;
  // redirects are asked the query service: it answers HTTP 431 to a request line above 8 KB, 100 Q-ids stay well below
  static final int BATCH = 100;
  // the most wbgetentities takes at once
  static final int ENTITY_BATCH = 50;

  enum IdProperty {
    P428, P835, P586, P2006
  }

  /**
   * The name and field items of the persons, whose labels are asked for once all statements are in.
   */
  record Pending(Map<String, List<String>> family, Map<String, List<String>> given, Map<String, List<String>> field) {
    Pending() {
      this(new LinkedHashMap<>(), new LinkedHashMap<>(), new LinkedHashMap<>());
    }

    Set<String> items() {
      Set<String> items = new LinkedHashSet<>();
      for (var m : List.of(family, given, field)) {
        m.values().forEach(items::addAll);
      }
      return items;
    }
  }

  private final Fetcher fetcher;
  private final Map<String, Integer> unmappedFields = new TreeMap<>();
  private int persons;

  public WikidataPersonSource(Fetcher fetcher) {
    this.fetcher = fetcher;
  }

  @Override
  public String name() {
    return "wikidata";
  }

  @Override
  public List<PersonRecord> read() throws Exception {
    Map<String, PersonRecord.Builder> builders = new TreeMap<>();
    for (IdProperty p : IdProperty.values()) {
      for (int offset = 0; ; offset += PAGE) {
        int rows = addIds(query("SELECT ?person ?v WHERE { ?person wdt:" + p + " ?v } ORDER BY ?person ?v LIMIT " + PAGE
          + " OFFSET " + offset), p, builders);
        LOG.info("wikidata {} offset {}: {} rows", p, offset, rows);
        if (rows < PAGE) break;
      }
    }
    List<String> qids = new ArrayList<>(builders.keySet());
    Pending pending = new Pending();
    for (int i = 0; i < qids.size(); i += ENTITY_BATCH) {
      addEntities(entities(qids.subList(i, Math.min(i + ENTITY_BATCH, qids.size())), "labels|aliases|claims", "en"), builders, pending);
      progress("persons", i, ENTITY_BATCH, qids.size());
    }
    // names without an English label are mostly kept in "mul", the label for all languages
    List<String> unlabelled = builders.values().stream().filter(b -> b.label == null).map(b -> b.wikidata).toList();
    for (int i = 0; i < unlabelled.size(); i += ENTITY_BATCH) {
      addEntities(entities(unlabelled.subList(i, Math.min(i + ENTITY_BATCH, unlabelled.size())), "labels|aliases", "mul"), builders, pending);
    }
    List<String> items = new ArrayList<>(pending.items());
    Map<String, String> labels = new HashMap<>();
    for (int i = 0; i < items.size(); i += ENTITY_BATCH) {
      labels.putAll(labels(entities(items.subList(i, Math.min(i + ENTITY_BATCH, items.size())), "labels", "en")));
      progress("item labels", i, ENTITY_BATCH, items.size());
    }
    List<String> unlabelledItems = items.stream().filter(i -> !labels.containsKey(i)).toList();
    for (int i = 0; i < unlabelledItems.size(); i += ENTITY_BATCH) {
      labels.putAll(labels(entities(unlabelledItems.subList(i, Math.min(i + ENTITY_BATCH, unlabelledItems.size())), "labels", "mul")));
    }
    resolve(builders, pending, labels);
    persons = builders.size();
    return builders.values().stream().map(PersonRecord.Builder::build).toList();
  }

  private static void progress(String what, int i, int batch, int total) {
    if ((i / batch) % 100 == 0) {
      LOG.info("wikidata {} {} of {}", what, i, total);
    }
  }

  /**
   * @return the number of rows, for paging
   */
  static int addIds(JsonNode json, IdProperty p, Map<String, PersonRecord.Builder> builders) {
    int rows = 0;
    for (JsonNode b : json.path("results").path("bindings")) {
      rows++;
      String q = qid(text(b, "person"));
      String v = text(b, "v");
      if (q == null || v == null) continue;
      PersonRecord.Builder pb = builders.computeIfAbsent(q, k -> {
        var x = new PersonRecord.Builder(PersonSource.WIKIDATA);
        x.wikidata = k;
        return x;
      });
      switch (p) {
        case P428 -> pb.name(v, PersonNameKind.STANDARD, PersonFormCode.BOT);
        case P835 -> pb.name(v, PersonNameKind.CITATION, PersonFormCode.ZOO);
        // an item with several IPNI or ZooBank ids keeps the first as its own, the others are the same person
        case P586 -> {
          if (pb.ipni == null) pb.ipni = v;
          else if (!pb.ipni.equals(v)) pb.otherId(Person.IPNI + v);
        }
        case P2006 -> {
          String z = v.toUpperCase(Locale.ROOT);
          if (pb.zoobank == null) pb.zoobank = z;
          else if (!pb.zoobank.equals(z)) pb.otherId(Person.ZOOBANK + z);
        }
      }
    }
    return rows;
  }

  private static void pend(Map<String, List<String>> map, String person, String item) {
    if (item != null) {
      List<String> items = map.computeIfAbsent(person, k -> new ArrayList<>());
      if (!items.contains(item)) {
        items.add(item);
      }
    }
  }

  private static void link(PersonRecord.Builder pb, PersonRelationType type, String q) {
    if (q != null) {
      pb.link(type, Person.WIKIDATA + q);
    }
  }

  /**
   * Adds the English label, the aliases and the statements of wbgetentities answers to the persons. Name and field
   * items go to pending, their labels are asked for afterwards.
   */
  static void addEntities(JsonNode api, Map<String, PersonRecord.Builder> builders, Pending pending) {
    for (JsonNode e : api.path("entities")) {
      PersonRecord.Builder pb = builders.get(e.path("id").asText(null));
      if (pb == null) continue;
      String label = label(e);
      if (label != null && pb.label == null) {
        pb.label(label);
      }
      for (String lang : List.of("en", "mul")) {
        for (JsonNode a : e.path("aliases").path(lang)) {
          pb.name(a.path("value").asText(null), PersonNameKind.VARIANT, PersonFormCode.ANY);
        }
      }
      JsonNode claims = e.path("claims");
      values(claims, "P569").forEach(v -> pb.born(year(v)));
      values(claims, "P570").forEach(v -> pb.died(year(v)));
      values(claims, "P2031").forEach(v -> pb.activeFrom(year(v)));
      values(claims, "P2032").forEach(v -> pb.activeTo(year(v)));
      values(claims, "P1317").forEach(v -> {
        pb.activeFrom(year(v));
        pb.activeTo(year(v));
      });
      for (String p : List.of("P22", "P25")) {
        values(claims, p).forEach(v -> link(pb, PersonRelationType.PARENT, v.path("id").asText(null)));
      }
      values(claims, "P3373").forEach(v -> link(pb, PersonRelationType.SIBLING, v.path("id").asText(null)));
      values(claims, "P734").forEach(v -> pend(pending.family(), pb.wikidata, v.path("id").asText(null)));
      values(claims, "P735").forEach(v -> pend(pending.given(), pb.wikidata, v.path("id").asText(null)));
      values(claims, "P101").forEach(v -> pend(pending.field(), pb.wikidata, v.path("id").asText(null)));
    }
  }

  private static Integer year(JsonNode time) {
    return Years.wikidata(time.path("time").asText(null), time.path("precision").asInt(11));
  }

  /**
   * @return the English label, else the one Wikidata keeps for all languages ("mul"), else null
   */
  private static String label(JsonNode e) {
    JsonNode labels = e.path("labels");
    String en = labels.path("en").path("value").asText(null);
    return en != null ? en : labels.path("mul").path("value").asText(null);
  }

  /**
   * @return the values of a property's statements, without deprecated ones and unknown or no values
   */
  private static List<JsonNode> values(JsonNode claims, String property) {
    List<JsonNode> values = new ArrayList<>();
    for (JsonNode c : claims.path(property)) {
      JsonNode v = c.path("mainsnak").path("datavalue").path("value");
      if (!"deprecated".equals(c.path("rank").asText()) && !v.isMissingNode()) {
        values.add(v);
      }
    }
    return values;
  }

  /**
   * @return the English label of every entity of a wbgetentities answer that has one
   */
  static Map<String, String> labels(JsonNode api) {
    Map<String, String> labels = new HashMap<>();
    for (JsonNode e : api.path("entities")) {
      String label = label(e);
      if (label != null) {
        labels.put(e.path("id").asText(), label);
      }
    }
    return labels;
  }

  /**
   * Gives the persons the family and given names and the groups of their pending items.
   */
  void resolve(Map<String, PersonRecord.Builder> builders, Pending pending, Map<String, String> labels) {
    pending.family().forEach((q, items) -> items.forEach(i -> builders.get(q).family(labels.get(i))));
    pending.given().forEach((q, items) -> items.forEach(i -> builders.get(q).given(labels.get(i))));
    pending.field().forEach((q, items) -> items.forEach(i -> {
      String label = labels.get(i);
      if (label == null) return;
      var groups = Groups.field(label);
      if (groups.isEmpty()) {
        unmappedFields.merge(label.toLowerCase(), 1, Integer::sum);
      }
      builders.get(q).groups.addAll(groups);
    }));
  }

  /**
   * @return old Q-id to the Q-id it redirects to, for the items given that became a redirect
   */
  public Map<String, String> redirects(Collection<String> qids) throws Exception {
    Map<String, String> redirects = new HashMap<>();
    List<String> list = new ArrayList<>(qids);
    for (int i = 0; i < list.size(); i += BATCH) {
      for (JsonNode b : query(redirectQuery(list.subList(i, Math.min(i + BATCH, list.size())))).path("results").path("bindings")) {
        String old = qid(text(b, "old"));
        String now = qid(text(b, "new"));
        if (old != null && now != null) {
          redirects.put(old, now);
        }
      }
    }
    return redirects;
  }

  static String redirectQuery(List<String> qids) {
    String values = qids.stream().map(q -> "wd:" + q).collect(Collectors.joining(" "));
    return "SELECT ?old ?new WHERE { VALUES ?old { " + values + " } ?old owl:sameAs ?new }";
  }

  private JsonNode query(String sparql) throws Exception {
    return Json.MAPPER.readTree(fetcher.get(SPARQL + "?format=json&query=" + URLEncoder.encode(sparql, StandardCharsets.UTF_8)));
  }

  /**
   * Without maxlag: it is meant for writers, and Wikidata counts the update lag of its query service into it, which
   * refused every request for hours when the query service was loaded. These are serial reads a second apart.
   */
  private JsonNode entities(List<String> ids, String props, String language) throws Exception {
    return Json.MAPPER.readTree(fetcher.get(API + "?action=wbgetentities&format=json&languages=" + language + "&props="
      + URLEncoder.encode(props, StandardCharsets.UTF_8) + "&ids=" + URLEncoder.encode(String.join("|", ids), StandardCharsets.UTF_8)));
  }

  private static String qid(String iri) {
    return iri != null && iri.startsWith(ENTITY) ? iri.substring(ENTITY.length()) : null;
  }

  private static String text(JsonNode b, String field) {
    JsonNode n = b.path(field).path("value");
    return n.isMissingNode() ? null : n.asText();
  }

  @Override
  public String stats() {
    StringBuilder sb = new StringBuilder(String.format("wikidata: %,d persons%n", persons));
    sb.append("fields of work mapped to no group, by persons:\n");
    unmappedFields.entrySet().stream()
      .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
      .limit(50)
      .forEach(e -> sb.append(String.format("  %6d  %s%n", e.getValue(), e.getKey())));
    return sb.toString();
  }
}
