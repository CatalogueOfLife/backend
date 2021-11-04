package life.catalogue.parser;

import com.google.common.collect.Lists;
import life.catalogue.api.vocab.Gazetteer;
import org.junit.Test;

import java.util.List;

public class AreaParserTest extends ParserTestBase<AreaParser.Area> {
  
  public AreaParserTest() {
    super(AreaParser.PARSER);
  }
  
  @Test
  public void parse() throws Exception {
    assertParse(new AreaParser.Area("AGS", Gazetteer.TDWG), "tdwg:AGS");
    assertParse(new AreaParser.Area("AGS", Gazetteer.TDWG), "TDWG:ags ");
    assertParse(new AreaParser.Area("3", Gazetteer.TDWG), "TDWG:3");
    assertParse(new AreaParser.Area("14", Gazetteer.TDWG), "TDWG : 14");
    assertParse(new AreaParser.Area("RUN-OO", Gazetteer.TDWG), "TDWG : RUN - OO");
    assertParse(new AreaParser.Area("CRL-PA", Gazetteer.TDWG), "tdwg:crl-pa");
    assertParse(new AreaParser.Area("DE", Gazetteer.ISO), "iso:de");
    assertParse(new AreaParser.Area("DE", Gazetteer.ISO), "fao:ger");
    assertParse(new AreaParser.Area("IT-82", Gazetteer.ISO), "iso:it-82");
    assertParse(new AreaParser.Area("FR-H", Gazetteer.ISO), "ISO:fr-h");
    assertParse(new AreaParser.Area("FM-PNI", Gazetteer.ISO), "ISO:FM-PNI");
    assertParse(new AreaParser.Area("37.4.1", Gazetteer.FAO), "fish:37.4.1");
    assertParse(new AreaParser.Area("27.12.a.4", Gazetteer.FAO), "fish:27.12.a.4");
    assertParse(new AreaParser.Area("27.12.c", Gazetteer.FAO), "fish:27.12.C");
    assertParse(new AreaParser.Area("27.3.d.28.2", Gazetteer.FAO), "fish:27.3.d.28.2");
    assertParse(new AreaParser.Area("AZ-TAR", Gazetteer.ISO), "iso:AZ-tar");
  }
  
  @Override
  List<String> unparsableValues() {
    return Lists.newArrayList(".", "?", "---", "öüä", "#67#", "wtf", "nothing", "t ru e", "a", "2",
        "Nig", "har:123", "iso:gggg", "tdwg:432", "f:37.4.1", "fish:7458923", "iso:NE-ANATOLIA");
  }
}