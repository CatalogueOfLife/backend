package life.catalogue.parser;

import life.catalogue.api.vocab.TreatmentFormat;

import java.util.List;

import org.junit.Test;

import com.google.common.collect.Lists;

public class TreatmentFormatParserTest extends ParserTestBase<TreatmentFormat> {
  
  public TreatmentFormatParserTest() {
    super(TreatmentFormatParser.PARSER);
  }
  
  @Test
  public void parse() throws Exception {
    assertParse(TreatmentFormat.XML, "xml");
    assertParse(TreatmentFormat.XML, "Text/XML");
    assertParse(TreatmentFormat.HTML, "html");
    assertParse(TreatmentFormat.HTML, "text/html");
    assertParse(TreatmentFormat.TAXON_X, "TaxonX");
  }
  
  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}