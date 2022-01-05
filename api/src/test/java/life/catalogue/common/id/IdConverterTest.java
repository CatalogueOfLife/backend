package life.catalogue.common.id;

import life.catalogue.common.io.UTF8IoUtils;

import java.io.BufferedWriter;
import java.io.File;
import java.util.*;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class IdConverterTest {

  @Test
  public void issueExamples() {
    for (int x : new int[]{18, 1089, 1781089, 4781089, 12781089, 2147483647}) {
      System.out.println(IdConverter.LATIN29.encode(x));
    }

    for (int x : new int[]{3, 1010, 2298, 3450, 25000}) {
      System.out.println(IdConverter.LATIN29.encode(x));
    }
  }

  @Test
  public void encode() {
    IdConverter dec = new IdConverter("0123456789");
    for (int x = 0; x<111; x++) {
      assertEquals(String.valueOf(x), dec.encode(x));
    }
    assertEquals("2", IdConverter.LATIN29.encode(0));
    assertEquals("35", IdConverter.LATIN29.encode(32));

    int[] ids = new int[]{0, 18, 1089, 1781089, 4781089, 12781089, Integer.MAX_VALUE};
    List<IdConverter> converters = new ArrayList<>();
    converters.add(IdConverter.HEX);
    converters.add(IdConverter.LATIN29);
    converters.add(IdConverter.LATIN32);
    converters.add(IdConverter.LATIN36);
    converters.add(IdConverter.BASE64);
  
    for (int id : ids) {
      System.out.println("\n" + id);
      for (IdConverter hid : converters) {
        System.out.println(hid.encode(id));
      }
      System.out.println(Proquint.encode(id));
    }
  }

  //@Test
  // THis is not a test but a tool to generate mappings between different ID CHARSETS
  public void map32to29() throws Exception {
    final var special = Map.ofEntries(
      Map.entry("A", "N"),
      Map.entry("B", "B"),
      Map.entry("C", "C"),
      Map.entry("F", "F"),
      Map.entry("M", "M"),
      Map.entry("P", "P"),
      Map.entry("R", "R"),
      Map.entry("V", "V"),
      Map.entry("Z", "Z"),

      Map.entry("ISO", "8P"),
      Map.entry("BAS", "BM"),
      Map.entry("BIV", "BV"),
      Map.entry("CUR", "CC"),
      Map.entry("CURC", "CC2"),
      Map.entry("CHA", "CD"),
      Map.entry("CHO", "CH"),
      Map.entry("ECH", "CHN"),
      Map.entry("COL", "CL"),
      Map.entry("CNI", "CN"),
      Map.entry("CAR", "CP"),
      Map.entry("ACT", "CT"),
      Map.entry("CYA", "CY"),
      Map.entry("DIP", "DP"),
      Map.entry("FAB", "FB"),
      Map.entry("FUL", "FL"),
      Map.entry("GEL", "GL"),
      Map.entry("GEO", "GM"),
      Map.entry("GEN", "GN"),
      Map.entry("GAS", "GP"),
      Map.entry("HEX", "H6"),
      Map.entry("HEM", "HP"),
      Map.entry("ICH", "IM"),
      Map.entry("ELA", "LB"),
      Map.entry("LIL", "LL"),
      Map.entry("LAM", "LM"),
      Map.entry("LEP", "LP"),
      Map.entry("ELAT", "LT"),
      Map.entry("MAL", "MC"),
      Map.entry("MAG", "MG"),
      Map.entry("MOL", "ML"),
      Map.entry("MAM", "MM"),
      Map.entry("APD", "MP"),
      Map.entry("MIR", "MR"),
      Map.entry("MES", "MS"),
      Map.entry("MAX", "MX"),
      Map.entry("NOC", "NC"),
      Map.entry("NEM", "NM"),
      Map.entry("ANN", "NN"),
      Map.entry("PER", "PC"),
      Map.entry("APO", "PD"),
      Map.entry("POR", "PF"),
      Map.entry("AMP", "PH"),
      Map.entry("POA", "PL"),
      Map.entry("PLA", "PM"),
      Map.entry("ARA", "RC"),
      Map.entry("ERI", "RCL"),
      Map.entry("RHO", "RH"),
      Map.entry("ROS", "RL"),
      Map.entry("ARN", "RN"),
      Map.entry("REP", "RP"),
      Map.entry("ART", "RT"),
      Map.entry("ORT", "RTH"),
      Map.entry("SCA", "SC"),
      Map.entry("SAR", "SF"),
      Map.entry("STA", "SL"),
      Map.entry("ASC", "SM"),
      Map.entry("ASP", "SP"),
      Map.entry("AST", "ST"),
      Map.entry("OST", "STC"),
      Map.entry("TRO", "TF"),
      Map.entry("TIP", "TL"),
      Map.entry("TRA", "TP"),
      Map.entry("TRI", "TRP"),
      Map.entry("AVE", "V1"),
      Map.entry("VES", "VP")
    );
    final var specialReverse = new HashSet<> (special.values());
    final var map = new LinkedHashMap<String, String> ();
    final var ids29 = new HashSet<String>();

    // 3981745 usages, highest ID = 5TJFT
    for (int x=1; x<3985000; x++) {
      String id32 = IdConverter.LATIN32.encode(x);
      String id29 = IdConverter.LATIN29.encode(x);
      map.put(id32, special.getOrDefault(id32, null));
      if (id29.length()==1) {
        System.out.println("Skip single char key " + id29);
      } else if (specialReverse.contains(id29)) {
        System.out.println("Skip special key " + id29);
      } else {
        ids29.add(id29);
      }
    }
    System.out.println(map.size() + " IDs generated");

    System.out.println("Map IDs that did not change");
    var iter = ids29.iterator();
    while (iter.hasNext()) {
      var id = iter.next();
      if (map.containsKey(id)) {
        map.put(id, id);
        iter.remove();
      }
    }
    System.out.println(ids29.size() + " IDs not previously existing");

    var iter29 = ids29.iterator();
    for (var e : map.entrySet()) {
      if (e.getValue() != null) continue;
      if (!iter29.hasNext()) break;
      e.setValue(iter29.next());
    }
    System.out.println("Print file");
    try (BufferedWriter w = UTF8IoUtils.writerFromFile(new File("/Users/markus/Downloads/idmap.tsv"))) {
      for (var e : map.entrySet()) {
        w.write(String.format("%s\t%s\n", e.getKey(), e.getValue()));
      }
    }
    System.out.println("Done");

  }

  @Test
  public void encodeNegative() {
    System.out.println("|" + IdConverter.LATIN29.encode(-999) + "|");
    System.out.println("|" + IdConverter.LATIN29.encode(-2147483648)+ "|");
  }

  /**
   * We use the alphabetical sorting
   */
  @Test
  public void sorting() {
    System.out.println("a comparesTo b: " + "a".compareTo("b"));
    System.out.println("a comparesTo a: " + "a".compareTo("a"));
    System.out.println("a comparesTo 1: " + "a".compareTo("1"));
    System.out.println("a comparesTo 1a: " + "a".compareTo("1a"));
    System.out.println("a comparesTo a1: " + "a".compareTo("1"));

    List<String> xxx = new ArrayList<>(List.of("a", "b", "1", "a1", "aa", "a1a", "ab", "b1", "1a"));
    Collections.sort(xxx);
    for (String id : xxx) System.out.println(id);

    List<IdConverter> converters = List.of(
      IdConverter.LATIN29,
      IdConverter.LATIN36,
      IdConverter.HEX
    );

    for (IdConverter conv : converters) {
      List<String> ids = new ArrayList<>();
      for (char c : conv.getChars()) {
        ids.add(Character.toString(c));
      }
      System.out.println("--- CONVERTER " + ids.size());
      for (String id : ids) System.out.println(id);

      List<String> ids2 = new ArrayList<>(ids);
      Collections.sort(ids2);
      System.out.println("-- SORTED ---");
      for (String id : ids2) System.out.println(id);
      assertEquals(ids2, ids);
    }
  }

  @Test
  public void roundtrip() {
    IdConverter conv = new IdConverter("0123456789");
    System.out.println("Decimal");
    for (int x = 0; x<111; x++) {
      String id = conv.encode(x);
      System.out.println(x + " -> " + id + " -> " + conv.decode(id));
      assertEquals(x, conv.decode(id));
    }
    assertEquals(0, IdConverter.LATIN29.decode("2"));

    int[] vals = new int[]{0, 18, 1089, 1781089, 4781089, 12781089, Integer.MAX_VALUE};
    List<IdConverter> converters = new ArrayList<>();
    converters.add(IdConverter.HEX);
    converters.add(IdConverter.LATIN29);
    converters.add(IdConverter.LATIN36);
    converters.add(IdConverter.BASE64);

    for (int val : vals) {
      System.out.println("\n" + val);
      for (IdConverter con : converters) {
        String id = con.encode(val);
        System.out.println(val + " -> " + id + " -> " + con.decode(id));
        assertEquals(val, con.decode(id));
      }
    }
  }
}