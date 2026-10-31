package life.catalogue.parser;

import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import life.catalogue.api.vocab.area.*;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThrows;

public class AreaParserTest extends ParserTestBase<Area> {
  
  @Rule
  public TemporaryFolder tmp = new TemporaryFolder();

  public AreaParserTest() {
    super(AreaParser.PARSER);
  }
  
  @Test
  public void parse() throws Exception {
    assertParse(new GenericArea(Gazetteer.TDWG, "AGS"), "tdwg:AGS");
    assertParse(new GenericArea(Gazetteer.TDWG, "AGS"), "TDWG:ags ");
    assertParse(new GenericArea(Gazetteer.TDWG, "3"), "TDWG:3");
    assertParse(new GenericArea(Gazetteer.TDWG, "14"), "TDWG : 14");
    assertParse(new GenericArea(Gazetteer.TDWG, "RUN-OO"), "TDWG : RUN-OO");
    assertParse(new GenericArea(Gazetteer.TDWG, "CRL-PA"), "tdwg:crl-pa");
    assertUnparsable("tdwg:milchbar");
    assertParse(Country.GERMANY, "iso:de");
    assertParse(new GenericArea(Gazetteer.FAO, "27"), "fao:27"); // Atlantic, Northeast 27
    assertUnparsable("fao:bar");
    assertParse(iso("IT-82"), "iso:it-82");
    assertParse(iso("FR-H"), "ISO:fr-h");
    assertParse(iso("FM-PNI"), "ISO:FM-PNI");
    assertParse(iso("CA-YT"), "ISO3166-2:CA-YT");
    assertUnparsable("iso:milchbar");
    assertParse(new GenericArea(Gazetteer.FAO,"37.4.1"), "fao:37.4.1");
    assertParse(new GenericArea(Gazetteer.FAO,"27.12.a.4"), "fao:27.12.a.4");
    assertParse(new GenericArea(Gazetteer.FAO,"27.12.c"), "fao:27.12.C");
    assertParse(new GenericArea(Gazetteer.FAO,"27.3.d.28.2"), "fao:27.3.d.28.2");
    assertParse(new GenericArea(Gazetteer.FAO,"37.4.1"), "FAO:37.4.1");
    assertParse(new GenericArea(Gazetteer.FAO,"21.0.A"), "fao:21.0.A");
    assertParse(new GenericArea(Gazetteer.FAO,"21.0.A"), "fao:21.0.a");
    assertParse(new GenericArea(Gazetteer.FAO,"21.3.P.n"), "fao:21.3.P.n");
    assertParse(new GenericArea(Gazetteer.FAO,"47.A.0"), "fao:47.A.0");
    assertParse(new GenericArea(Gazetteer.FAO,"47.A.0"), "fao:47.a.0");
    assertParse(iso("AZ-TAR"), "iso:AZ-tar");
    assertParse(new GenericArea(Gazetteer.MRGID,"3351"), "http://marineregions.org/mrgid/3351");
    assertParse(new GenericArea(Gazetteer.MRGID,"3351"), "https://marineregions.org/mrgid/3351");
    assertParse(new GenericArea(Gazetteer.MRGID,"3351"), "mrgid:3351");
    assertUnparsable("mrgid:shipwreck");
    assertParse(realm("Neotropic"), "realm:Neotropic");
    assertParse(realm("Neotropic"), "bio:neotropic");
    assertParse(realm("Neotropic"), "realm:NEOTROPIC");
    assertParse(realm("Oceania"), "REALM:Oceania");
    assertParse(realm("TemperateSouthAmerica"), "realm:TemperateSouthAmerica");
    assertUnparsable("realm:South America");

    assertEquals(Optional.empty(), parser.parse("iso: "));
    assertEquals(Optional.empty(), parser.parse("iso:"));
    assertUnparsable("foo:bar");

    assertParse(new GenericArea("French Polynesia:"), "French Polynesia:");
  }

  @Test
  public void labels() throws Exception {
    File dir = tmp.newFolder();
    Files.createDirectories(dir.toPath().resolve("iso"));
    Files.writeString(dir.toPath().resolve("iso/labels.tsv"), "DE-BE\tBerlin\nYU\tYugoslavia\n");
    Files.createDirectories(dir.toPath().resolve("fao"));
    Files.writeString(dir.toPath().resolve("fao/labels.tsv"), "21.0.A\tAtlantic, Northwest / 21.0.A\n47.A.0\tSEAFO division A.0\n");

    var p = new AreaParser();
    p.setLabelLookup(new AreaLabelLookup(dir));
    assertEquals("Berlin", p.parse("iso:DE-BE").orElseThrow().getName());
    assertEquals("Berlin", p.parse("iso:de-be").orElseThrow().getName());
    // historic codes are no countries, but known to the gazetteer
    assertEquals(new GenericArea(Gazetteer.ISO, "YU", "Yugoslavia"), p.parse("iso:yu").orElseThrow());
    assertEquals(Country.GERMANY, p.parse("iso:de").orElseThrow());
    assertThrows(UnparsableException.class, () -> p.parse("iso:ZZ"));
    assertEquals("Atlantic, Northwest / 21.0.A", p.parse("fao:21.0.a").orElseThrow().getName());
    assertEquals("SEAFO division A.0", p.parse("fao:47.A.0").orElseThrow().getName());
  }

  @Test
  public void realmLabels() throws Exception {
    File dir = tmp.newFolder();
    Files.createDirectories(dir.toPath().resolve("realm"));
    Files.writeString(dir.toPath().resolve("realm/labels.tsv"), "Neotropic\tNeotropic\nTropicalAtlantic\tTropical Atlantic\n");

    var p = new AreaParser();
    assertEquals(realm("Shipwreck"), p.parse("realm:shipwreck").orElseThrow());

    p.setLabelLookup(new AreaLabelLookup(dir));
    assertEquals(new GenericArea(Gazetteer.REALM, "Neotropic", "Neotropic"), p.parse("bio:NEOTROPIC").orElseThrow());
    assertEquals(new GenericArea(Gazetteer.REALM, "Neotropic", "Neotropic"), p.parse("biogeo:NEOTROPIC").orElseThrow());
    assertEquals(new GenericArea(Gazetteer.REALM, "TropicalAtlantic", "Tropical Atlantic"), p.parse("realm:TropicalAtlantic").orElseThrow());
    assertThrows(UnparsableException.class, () -> p.parse("realm:shipwreck"));
    // only realms are restricted to the list, the labels of other gazetteers are not used for validation
    assertEquals(new GenericArea(Gazetteer.TDWG, "99", "99"), p.parse("tdwg:99").orElseThrow());
  }

  static GenericArea realm(String id) {
    return new GenericArea(Gazetteer.REALM, id);
  }

  static GenericArea iso(String code) {
    return new GenericArea(Gazetteer.ISO, code);
  }

  @Override
  List<String> unparsableValues() {
    return new ArrayList<>();
  }
}