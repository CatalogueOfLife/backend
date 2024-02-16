package life.catalogue.parser;

import life.catalogue.api.vocab.TaxGroup;

import life.catalogue.common.io.Resources;

import life.catalogue.common.io.UTF8IoUtils;

import org.apache.commons.lang3.StringUtils;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

public class TaxGroupParserTest extends ParserTestBase<TaxGroup> {

  public TaxGroupParserTest() {
    super(TaxGroupParser.PARSER);
  }

  @Test
  public void parse() throws Exception {
    assertParse(TaxGroup.Arthropods, "Arthropoda");
    assertParse(TaxGroup.Arthropods, "arthropods");
    assertParse(TaxGroup.Animals, "animalia");
    assertParse(null, "anima");

    assertParse(TaxGroup.Birds, "Falconidae");
  }

  @Test
  public void dictsExist() throws Exception {
    for (TaxGroup tg : TaxGroup.values()) {
      var res = getClass().getResourceAsStream("/parser/dicts/taxgroup/" + tg.name().toLowerCase() + ".txt");
      assertNotNull(tg.name(), res);
    }
  }

  @Test
  public void dictsClash() throws Exception {
    Set<String> entries = new HashSet<>();
    for (TaxGroup tg : TaxGroup.values()) {
      var res = getClass().getResourceAsStream("/parser/dicts/taxgroup/" + tg.name().toLowerCase() + ".txt");
      assertNotNull("missing parser file for " + tg, res);
      try (BufferedReader br = UTF8IoUtils.readerFromStream(res)) {
        br.lines().forEach( name -> {
          if (!StringUtils.isBlank(name)) {
            assertTrue(tg + ": " + name, entries.add(name));
          }
        });
      }
      assertNotNull(tg.name(), res);
    }
  }

  @Test
  @Override
  public void testUnparsable() throws Exception {
    // dont do anything
  }

}