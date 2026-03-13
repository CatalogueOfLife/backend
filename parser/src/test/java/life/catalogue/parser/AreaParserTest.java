package life.catalogue.parser;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import life.catalogue.api.vocab.area.*;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class AreaParserTest extends ParserTestBase<Area> {
  
  public AreaParserTest() {
    super(AreaParser.PARSER);
  }
  
  @Test
  public void parse() throws Exception {
    assertParse(TdwgArea.of("AGS"), "tdwg:AGS");
    assertParse(TdwgArea.of("AGS"), "TDWG:ags ");
    assertParse(TdwgArea.of("3"), "TDWG:3");
    assertParse(TdwgArea.of("14"), "TDWG : 14");
    assertParse(TdwgArea.of("RUN-OO"), "TDWG : RUN-OO");
    assertParse(TdwgArea.of("CRL-PA"), "tdwg:crl-pa");
    assertParse(Country.GERMANY, "iso:de");
    assertParse(new GenericArea(Gazetteer.FAO, "ger"), "fao:ger");
    assertParse(iso("IT-82"), "iso:it-82");
    assertParse(iso("FR-H"), "ISO:fr-h");
    assertParse(iso("FM-PNI"), "ISO:FM-PNI");
    assertParse(new GenericArea(Gazetteer.FAO,"37.4.1"), "fao:37.4.1");
    assertParse(new GenericArea(Gazetteer.FAO,"27.12.a.4"), "fao:27.12.a.4");
    assertParse(new GenericArea(Gazetteer.FAO,"27.12.C"), "fao:27.12.C");
    assertParse(new GenericArea(Gazetteer.FAO,"27.3.d.28.2"), "fao:27.3.d.28.2");
    assertParse(new GenericArea(Gazetteer.FAO,"37.4.1"), "FAO:37.4.1");
    assertParse(iso("AZ-TAR"), "iso:AZ-tar");
    assertParse(new GenericArea(Gazetteer.MRGID,"3351"), "http://marineregions.org/mrgid/3351");
    assertParse(new GenericArea(Gazetteer.MRGID,"3351"), "https://marineregions.org/mrgid/3351");
    assertParse(new GenericArea(Gazetteer.MRGID,"3351"), "mrgid:3351");
    assertParse(new GenericArea("shipwreck"), "mrgid:shipwreck");
    assertParse(BioGeoRealm.Neotropic, "realm:Neotropic");
    assertParse(BioGeoRealm.Neotropic, "bio:neotropic");
    assertUnparsable("realm:shipwreck");
    assertParse(BioGeoRealm.Oceania, "REALM:Oceania");

    assertEquals(Optional.empty(), parser.parse("iso: "));
    assertEquals(Optional.empty(), parser.parse("iso:"));
    assertUnparsable("foo:bar");

    assertParse(new GenericArea("French Polynesia:"), "French Polynesia:");
  }

  static GenericArea iso(String code) {
    return new GenericArea(Gazetteer.ISO, code);
  }

  @Override
  List<String> unparsableValues() {
    return new ArrayList<>();
  }
}