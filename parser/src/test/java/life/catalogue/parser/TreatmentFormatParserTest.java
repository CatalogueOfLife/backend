package life.catalogue.parser;

import java.util.List;

import com.google.common.collect.Lists;
import life.catalogue.api.vocab.TreatmentFormat;
import org.junit.Test;

public class TreatmentFormatParserTest extends ParserTestBase<TreatmentFormat> {
  
  public TreatmentFormatParserTest() {
    super(TextFormatParser.PARSER);
  }
  
  @Test
  public void parse() throws Exception {
    assertParse(TreatmentFormat.XML, "text");
    assertParse(TreatmentFormat.XML, "plain_text");
    assertParse(TreatmentFormat.HTML, "html");
    assertParse(TreatmentFormat.HTML, "text/html");
    assertParse(TreatmentFormat.TAXON_X, "md");
  }
  
  @Override
  List<String> additionalUnparsableValues() {
    return Lists.newArrayList("term", "deuter");
  }
}