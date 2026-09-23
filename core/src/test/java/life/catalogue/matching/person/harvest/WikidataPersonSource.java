package life.catalogue.matching.person.harvest;

import life.catalogue.matching.person.FormCode;
import life.catalogue.matching.person.NameKind;
import life.catalogue.matching.person.Person;
import life.catalogue.matching.person.Provenance;
import life.catalogue.matching.person.RelationType;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

import com.fasterxml.jackson.core.json.JsonReadFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.json.JsonMapper;

/**
 * Persons from Wikidata: everyone with a botanist author abbreviation (P428), a zoologist author citation (P835), an
 * IPNI author id (P586) or a ZooBank author id (P2006). The ids are paged one property at a time. The facts of the
 * persons are then asked for in batches of items, one UNION per fact so that multi valued facts never multiply rows:
 * English label and aliases, family and given names (P734, P735), birth and death (P569, P570), active years (P2031,
 * P2032, P1317), field of work (P101), parents (P22, P25) and siblings (P3373).
 */
public class WikidataPersonSource implements PersonSource {
  static final String ENDPOINT = "https://query.wikidata.org/sparql";
  static final String ENTITY = "http://www.wikidata.org/entity/";
  static final int PAGE = 5000;
  static final int BATCH = 400;
  // Wikidata labels occasionally hold raw control characters that strict JSON rejects
  private static final ObjectMapper MAPPER = JsonMapper.builder().enable(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS).build();

  enum IdProperty {
    P428, P835, P586, P2006
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
        System.out.printf("  wikidata %s offset %d: %d rows%n", p, offset, rows);
        if (rows < PAGE) break;
      }
    }
    List<String> qids = new ArrayList<>(builders.keySet());
    for (int i = 0; i < qids.size(); i += BATCH) {
      addFacts(query(factQuery(qids.subList(i, Math.min(i + BATCH, qids.size())))), builders);
      if ((i / BATCH) % 20 == 0) {
        System.out.printf("  wikidata facts %d of %d persons%n", i, qids.size());
      }
    }
    persons = builders.size();
    return builders.values().stream().map(PersonRecord.Builder::build).toList();
  }

  static String factQuery(List<String> qids) {
    String values = qids.stream().map(q -> "wd:" + q).collect(Collectors.joining(" "));
    return "SELECT ?person ?p ?v WHERE { VALUES ?person { " + values + " }"
      + " { ?person rdfs:label ?v FILTER(LANG(?v) = \"en\") BIND(\"label\" AS ?p) }"
      + " UNION { ?person skos:altLabel ?v FILTER(LANG(?v) = \"en\") BIND(\"alias\" AS ?p) }"
      + " UNION { ?person wdt:P734 ?x . ?x rdfs:label ?v FILTER(LANG(?v) = \"en\") BIND(\"family\" AS ?p) }"
      + " UNION { ?person wdt:P735 ?x . ?x rdfs:label ?v FILTER(LANG(?v) = \"en\") BIND(\"given\" AS ?p) }"
      + " UNION { ?person wdt:P569 ?v BIND(\"born\" AS ?p) }"
      + " UNION { ?person wdt:P570 ?v BIND(\"died\" AS ?p) }"
      + " UNION { ?person wdt:P2031 ?v BIND(\"activeFrom\" AS ?p) }"
      + " UNION { ?person wdt:P2032 ?v BIND(\"activeTo\" AS ?p) }"
      + " UNION { ?person wdt:P1317 ?v BIND(\"floruit\" AS ?p) }"
      + " UNION { ?person wdt:P101 ?x . ?x rdfs:label ?v FILTER(LANG(?v) = \"en\") BIND(\"field\" AS ?p) }"
      + " UNION { ?person wdt:P22 ?v BIND(\"parent\" AS ?p) }"
      + " UNION { ?person wdt:P25 ?v BIND(\"parent\" AS ?p) }"
      + " UNION { ?person wdt:P3373 ?v BIND(\"sibling\" AS ?p) }"
      + " }";
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
        var x = new PersonRecord.Builder(Provenance.WIKIDATA);
        x.wikidata = k;
        return x;
      });
      switch (p) {
        case P428 -> pb.name(v, NameKind.STANDARD, FormCode.BOT);
        case P835 -> pb.name(v, NameKind.CITATION, FormCode.ZOO);
        // an item with several IPNI or ZooBank ids keeps the first in id order, deterministically
        case P586 -> pb.ipni = pb.ipni == null ? v : pb.ipni;
        case P2006 -> pb.zoobank = pb.zoobank == null ? v.toUpperCase() : pb.zoobank;
      }
    }
    return rows;
  }

  void addFacts(JsonNode json, Map<String, PersonRecord.Builder> builders) {
    for (JsonNode b : json.path("results").path("bindings")) {
      PersonRecord.Builder pb = builders.get(qid(text(b, "person")));
      String p = text(b, "p");
      String v = text(b, "v");
      if (pb == null || p == null || v == null) continue;
      switch (p) {
        case "label" -> pb.label(v);
        case "alias" -> pb.name(v, NameKind.VARIANT, FormCode.ANY);
        case "family" -> pb.family(v);
        case "given" -> pb.given(v);
        case "born" -> pb.born(Years.wikidata(v));
        case "died" -> pb.died(Years.wikidata(v));
        case "activeFrom" -> pb.activeFrom(Years.wikidata(v));
        case "activeTo" -> pb.activeTo(Years.wikidata(v));
        case "floruit" -> {
          pb.activeFrom(Years.wikidata(v));
          pb.activeTo(Years.wikidata(v));
        }
        case "field" -> {
          var groups = Groups.field(v);
          if (groups.isEmpty()) {
            unmappedFields.merge(v.toLowerCase(), 1, Integer::sum);
          }
          pb.groups.addAll(groups);
        }
        case "parent" -> link(pb, RelationType.PARENT, v);
        case "sibling" -> link(pb, RelationType.SIBLING, v);
        default -> {
        }
      }
    }
  }

  private static void link(PersonRecord.Builder pb, RelationType type, String iri) {
    String q = qid(iri);
    if (q != null) {
      pb.link(type, Person.WIKIDATA + q);
    }
  }

  /**
   * @return old Q-id to the Q-id it redirects to, for the items given that became a redirect
   */
  public Map<String, String> redirects(Collection<String> qids) throws Exception {
    Map<String, String> redirects = new HashMap<>();
    List<String> list = new ArrayList<>(qids);
    for (int i = 0; i < list.size(); i += BATCH) {
      String values = list.subList(i, Math.min(i + BATCH, list.size())).stream().map(q -> "wd:" + q).collect(Collectors.joining(" "));
      for (JsonNode b : query("SELECT ?old ?new WHERE { VALUES ?old { " + values + " } ?old owl:sameAs ?new }")
        .path("results").path("bindings")) {
        String old = qid(text(b, "old"));
        String now = qid(text(b, "new"));
        if (old != null && now != null) {
          redirects.put(old, now);
        }
      }
    }
    return redirects;
  }

  private JsonNode query(String sparql) throws Exception {
    return MAPPER.readTree(fetcher.get(ENDPOINT + "?format=json&query=" + URLEncoder.encode(sparql, StandardCharsets.UTF_8)));
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
