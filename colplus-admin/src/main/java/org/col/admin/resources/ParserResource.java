package org.col.admin.resources;

import com.codahale.metrics.annotation.Timed;
import org.col.api.model.CslItemData;
import org.col.dw.anystyle.AnystyleParserWrapper;
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
  private final AnystyleParserWrapper parser;

  public ParserResource(AnystyleParserWrapper parser) {
    this.parser = parser;
  }

  /**
   * Parsing citations as GET query parameters.
   */
  @GET
  @Timed
  public CslItemData parse(@QueryParam("ref") String citation) {
    return parser.parse(citation);
  }

}
