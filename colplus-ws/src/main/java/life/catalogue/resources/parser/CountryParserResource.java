package life.catalogue.resources.parser;

import life.catalogue.api.vocab.Country;
import life.catalogue.parser.CountryParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Path("/parser/country")
@Produces(MediaType.APPLICATION_JSON)
public class CountryParserResource extends AbstractParserResource<Country> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(CountryParserResource.class);

  public CountryParserResource() {
    super(CountryParser.PARSER);
  }
  
}
