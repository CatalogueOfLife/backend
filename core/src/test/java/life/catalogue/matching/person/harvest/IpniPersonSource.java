package life.catalogue.matching.person.harvest;

import life.catalogue.api.vocab.PersonFormCode;
import life.catalogue.api.vocab.PersonNameKind;
import life.catalogue.api.vocab.PersonSource;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.*;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Persons from IPNI. There is no bulk download, but the author search pages with a cursor and stops after 10,000
 * records per query. So it is asked by surname prefix, A to Z, and a prefix with more authors is split into longer
 * prefixes. Authors whose surname starts with no letter A to Z are what the total of all authors shows missing.
 */
public class IpniPersonSource implements HarvestSource {
  static final String ENDPOINT = "https://www.ipni.org/api/1/search";
  static final int PAGE = 500;
  static final int MAX_RECORDS = 10000;
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private final Fetcher fetcher;
  private final Map<String, Integer> unmappedGroups = new TreeMap<>();
  private final List<String> unparsedDates = new ArrayList<>();
  private int total;
  private int harvested;
  private int suppressed;

  public IpniPersonSource(Fetcher fetcher) {
    this.fetcher = fetcher;
  }

  @Override
  public String name() {
    return "ipni";
  }

  @Override
  public List<PersonRecord> read() throws Exception {
    total = page("", "*").path("totalResults").asInt();
    Map<String, PersonRecord> byId = new TreeMap<>();
    for (char c = 'A'; c <= 'Z'; c++) {
      harvest(String.valueOf(c), byId);
    }
    harvested = byId.size();
    return List.copyOf(byId.values());
  }

  private void harvest(String prefix, Map<String, PersonRecord> byId) throws Exception {
    JsonNode page = page(prefix, "*");
    if (page.path("totalResults").asInt() > MAX_RECORDS) {
      for (char c = 'a'; c <= 'z'; c++) {
        harvest(prefix + c, byId);
      }
      return;
    }
    System.out.printf("  ipni %s: %d authors%n", prefix, page.path("totalResults").asInt());
    while (true) {
      JsonNode results = page.path("results");
      for (JsonNode a : results) {
        PersonRecord r = parse(a);
        if (r != null) {
          byId.putIfAbsent(r.ipni(), r);
        }
      }
      String cursor = page.path("cursor").isTextual() ? page.path("cursor").asText() : null;
      if (cursor == null || results.size() == 0) break;
      page = page(prefix, cursor);
    }
  }

  private JsonNode page(String prefix, String cursor) throws Exception {
    String q = "author surname:" + prefix + "*";
    return MAPPER.readTree(fetcher.get(ENDPOINT + "?perPage=" + PAGE + "&f=f_authors&cursor="
      + URLEncoder.encode(cursor, StandardCharsets.UTF_8) + "&q=" + URLEncoder.encode(q, StandardCharsets.UTF_8)));
  }

  /**
   * @return the person of one IPNI author record, null for a suppressed record or one without id
   */
  @Nullable
  PersonRecord parse(JsonNode a) {
    String id = StringUtils.trimToNull(a.path("id").asText(null));
    if (id == null) return null;
    if (a.path("suppressed").asBoolean(false)) {
      suppressed++;
      return null;
    }
    var b = new PersonRecord.Builder(PersonSource.IPNI);
    b.ipni = id;
    String std = StringUtils.trimToNull(a.path("standardForm").asText(null));
    String forename = StringUtils.trimToNull(a.path("forename").asText(null));
    String surname = StringUtils.trimToNull(a.path("surname").asText(null));
    b.name(std, PersonNameKind.STANDARD, PersonFormCode.BOT);
    if (surname != null) {
      b.name(forename == null ? surname : forename + " " + surname, PersonNameKind.FULL, PersonFormCode.ANY);
    }
    for (String alt : a.path("alternativeNames").asText("").split(";")) {
      b.name(Names.surnameFirst(alt), PersonNameKind.VARIANT, PersonFormCode.ANY);
    }
    b.family(surname);
    b.given(forename);
    if (std != null && std.matches(".*\\bf\\.$")) {
      b.suffix = "f.";
    }
    String dates = StringUtils.trimToNull(a.path("dates").asText(null));
    Years.Span span = Years.ipni(dates);
    if (dates != null && span.equals(Years.NONE) && unparsedDates.size() < 100) {
      unparsedDates.add(dates);
    }
    b.born(span.born());
    b.died(span.died());
    b.activeFrom(span.activeFrom());
    b.activeTo(span.activeTo());
    b.groups.addAll(Groups.ipni(a.path("taxonGroups").asText(null), g -> unmappedGroups.merge(g, 1, Integer::sum)));
    return b.build();
  }

  @Override
  public String stats() {
    return String.format("ipni: IPNI finds %,d authors, %,d harvested, %,d suppressed%n", total, harvested, suppressed)
      + "taxon groups mapped to nothing: " + unmappedGroups + "\n"
      + "dates not understood, the first 100: " + unparsedDates + "\n";
  }
}
