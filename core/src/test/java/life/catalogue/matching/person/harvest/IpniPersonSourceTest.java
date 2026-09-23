package life.catalogue.matching.person.harvest;

import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.matching.person.FormCode;
import life.catalogue.matching.person.NameKind;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Set;

import org.junit.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

import static org.junit.Assert.*;

public class IpniPersonSourceTest {
  private static final ObjectMapper MAPPER = new ObjectMapper();

  static String author(String id, String std, String forename, String surname, String dates, String groups, String alt) {
    return String.format("{\"id\":\"%s\",\"standardForm\":\"%s\",\"forename\":\"%s\",\"surname\":\"%s\",\"dates\":\"%s\","
      + "\"taxonGroups\":\"%s\",\"alternativeNames\":\"%s\",\"suppressed\":false}", id, std, forename, surname, dates, groups, alt);
  }

  static String page(int total, String cursor, String... authors) {
    return "{\"totalResults\":" + total + ",\"cursor\":" + (cursor == null ? "null" : "\"" + cursor + "\"")
      + ",\"results\":[" + String.join(",", authors) + "]}";
  }

  @Test
  public void parse() throws Exception {
    var source = new IpniPersonSource(url -> "");
    PersonRecord r = source.parse(MAPPER.readTree(author("9934-1", "J.C.Sowerby", "James de Carle", "Sowerby", "1787-1871",
      "Mycology, Algae, Fossils", "Sowerby, James DeCarle")));
    assertEquals("9934-1", r.ipni());
    assertEquals("Sowerby", r.family());
    assertEquals("James de Carle", r.given());
    assertNull(r.suffix());
    assertEquals(Integer.valueOf(1787), r.born());
    assertEquals(Integer.valueOf(1871), r.died());
    assertEquals(Set.of(TaxGroup.Fungi, TaxGroup.Algae), r.groups());
    assertEquals(List.of(new PersonRecord.Form("J.C.Sowerby", NameKind.STANDARD, FormCode.BOT),
      new PersonRecord.Form("James de Carle Sowerby", NameKind.FULL, FormCode.ANY),
      new PersonRecord.Form("James DeCarle Sowerby", NameKind.VARIANT, FormCode.ANY)), r.names());
    assertEquals("f.", source.parse(MAPPER.readTree(author("4084-1", "Hook.f.", "Joseph Dalton", "Hooker", "1817-1911", "", ""))).suffix());
    assertNull(source.parse(MAPPER.readTree("{\"id\":\"1-1\",\"suppressed\":true,\"standardForm\":\"X\"}")));
  }

  /** a prefix with more than 10,000 authors is split into longer prefixes instead of being cut off */
  @Test
  public void splitsBigPrefixes() throws Exception {
    var source = new IpniPersonSource(url -> {
      String q = URLDecoder.decode(url, StandardCharsets.UTF_8);
      if (q.contains("surname:*")) return page(3, null);
      if (q.contains("surname:S*")) return page(12000, "c1", author("1-1", "S.", "", "Sa", "", "", ""));
      if (q.contains("surname:Sa*") && q.contains("cursor=*")) return page(2, "c2", author("2-1", "Sa.", "", "Saa", "", "", ""));
      if (q.contains("surname:Sa*") && q.contains("cursor=c2")) return page(2, "c3", author("3-1", "Sab.", "", "Sab", "", "", ""));
      if (q.contains("surname:Sa*")) return page(2, "c4");
      return page(0, null);
    });
    List<PersonRecord> records = source.read();
    assertEquals(List.of("2-1", "3-1"), records.stream().map(PersonRecord::ipni).toList());
    assertTrue(source.stats(), source.stats().contains("IPNI finds 3 authors"));
  }
}
