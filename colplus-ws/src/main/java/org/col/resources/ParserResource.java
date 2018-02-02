package org.col.resources;

import com.codahale.metrics.annotation.Timed;
import com.google.common.collect.Lists;
import org.col.api.model.Name;
import org.col.parser.NameParser;
import org.glassfish.jersey.media.multipart.FormDataParam;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Path("/parser/name")
@Produces(MediaType.APPLICATION_JSON)
public class ParserResource {

	@SuppressWarnings("unused")
	private static final Logger LOG = LoggerFactory.getLogger(ParserResource.class);
  private static final NameParser parser = NameParser.PARSER;

  /**
   * Parsing names as GET query parameters.
   */
  @GET
  @Timed
  public List<Name> parseGet(@QueryParam("name") List<String> names) {
    return parse(names.stream());
  }

  /**
   * Parsing names as a json array.
   */
  @POST
  @Consumes(MediaType.APPLICATION_JSON)
  public List<Name> parseJson(List<String> names) {
    return parse(names.stream());
  }

  /**
   * Parsing names by uploading a plain UTF-8 text file using one line per scientific name.
   * <pre>
   * curl -F names=@scientific_names.txt http://apidev.gbif.org/parser/name
   * </pre>
   */
  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public List<Name> parseFile(@FormDataParam("names") InputStream file) throws UnsupportedEncodingException {
    if (file == null) {
      LOG.debug("No names file uploaded");
      return Lists.newArrayList();
    }
    BufferedReader reader = new BufferedReader(new InputStreamReader(file, Charset.forName("UTF8")));
    return parse(reader.lines());
  }


  /**
   * Parsing names by posting plain text content using one line per scientific name.
   * Make sure to preserve new lines (\n) in the posted data, for example use --data-binary with curl:
   * <pre>
   * curl POST -H "Content-Type:text/plain" --data-binary @scientific_names.txt http://api.gbif.org/parser/name
   * </pre>
   */
  @POST
  @Consumes(MediaType.TEXT_PLAIN)
  public List<Name> parsePlainText(InputStream names) throws UnsupportedEncodingException {
    return parseFile(names);
  }


  @Timed
  private List<Name> parse(Stream<String> names) {
    return names
        .peek(n -> LOG.info("Parse: {}", n))
        .map(parser::parse)
        .filter(Optional::isPresent)
        .map(Optional::get)
        .collect(Collectors.toList());
  }

}
