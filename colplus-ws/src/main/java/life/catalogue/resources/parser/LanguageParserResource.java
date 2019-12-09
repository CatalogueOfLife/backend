package life.catalogue.resources.parser;

import life.catalogue.api.vocab.Language;
import life.catalogue.parser.LanguageParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/parser/country")
@Produces(MediaType.APPLICATION_JSON)
public class LanguageParserResource extends AbstractParserResource<Language> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(LanguageParserResource.class);

  public LanguageParserResource() {
    super(LanguageParser.PARSER);
  }
  
}
