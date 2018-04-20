package org.col.admin.resources;

import com.codahale.metrics.annotation.Timed;
import org.col.api.model.CslItemData;
import org.col.parser.Parser;
import org.col.parser.UnparsableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("/parser/csl")
@Produces(MediaType.APPLICATION_JSON)
public class ParserResource {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(ParserResource.class);
  private final Parser<CslItemData> parser;

  public ParserResource(Parser<CslItemData> parser) {
    this.parser = parser;
  }

  /**
   * Parsing citations as GET query parameters.
   */
  @GET
  @Timed
  public CslItemData parse(@QueryParam("ref") String citation) throws UnparsableException {
    return parser.parse(citation).orElse(null);
  }

}
