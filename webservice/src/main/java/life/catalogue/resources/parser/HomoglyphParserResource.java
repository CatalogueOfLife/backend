package life.catalogue.resources.parser;

import org.apache.commons.lang3.StringUtils;

import org.gbif.nameparser.util.UnicodeUtils;

import java.util.ArrayList;
import java.util.List;

import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;

import com.fasterxml.jackson.annotation.JsonProperty;

@Path("/parser/homoglyph")
@Produces(MediaType.APPLICATION_JSON)
public class HomoglyphParserResource {

  public static class HomoglyphResult {
    public String original;
    public String canonical;
    public List<Integer> positions;

    @JsonProperty("hasHomoglyphs")
    public boolean hasHomoglyphs() {
      return !original.equals(canonical);
    }
  }


  @GET
  public HomoglyphResult parse(@QueryParam("q") String input, @QueryParam("hyphen") boolean hyphen, @QueryParam("keep") String keep) {
    if (input == null) {
      throw new IllegalArgumentException("Input string required");
    }
    HomoglyphResult result = new HomoglyphResult();
    result.original = input;
    result.canonical = UnicodeUtils.replaceHomoglyphs(input, hyphen, StringUtils.trimToNull(keep));
    if (result.hasHomoglyphs()) {
      result.positions = new ArrayList<>();
      int pos = 0;
      var iter = input.codePoints().iterator();
      var iter2 = result.canonical.codePoints().iterator();
      while(iter.hasNext()) {
        pos++;
        int cp = iter.nextInt();
        int cp2 = iter2.nextInt();
        if (cp != cp2) {
          result.positions.add(pos);
        }
      }
    }
    return result;
  }
  
  @POST
  @Consumes({MediaType.TEXT_PLAIN, MediaType.APPLICATION_JSON})
  public HomoglyphResult parseJson(@QueryParam("hyphen") boolean hyphen, @QueryParam("keep") String keep, String input) {
    return parse(input, hyphen, keep);
  }

}
