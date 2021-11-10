package life.catalogue.parser;

import life.catalogue.api.vocab.*;

import java.util.ArrayList;
import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

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
    assertParse(TdwgArea.of("RUN-OO"), "TDWG : RUN - OO");
    assertParse(TdwgArea.of("CRL-PA"), "tdwg:crl-pa");
    assertParse(Country.GERMANY, "iso:de");
    assertParse(new AreaImpl(Gazetteer.FAO, "ger"), "fao:ger");
    assertParse(Country.ITALY, "iso:it-82");
    assertParse(Country.FRANCE, "ISO:fr-h");
    assertParse(Country.MICRONESIA, "ISO:FM-PNI");
    assertParse(new AreaImpl(Gazetteer.FAO,"37.4.1"), "fish:37.4.1");
    assertParse(new AreaImpl(Gazetteer.FAO,"27.12.a.4"), "fish:27.12.a.4");
    assertParse(new AreaImpl(Gazetteer.FAO,"27.12.C"), "fish:27.12.C");
    assertParse(new AreaImpl(Gazetteer.FAO,"27.3.d.28.2"), "fish:27.3.d.28.2");
    assertParse(Country.AZERBAIJAN, "iso:AZ-tar");
  }

  @Override
  List<String> unparsableValues() {
    return new ArrayList<>();
  }
}