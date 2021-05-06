package life.catalogue.resources.parser;

import life.catalogue.common.id.IdConverter;

import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

@Path("/parser/idconverter")
@Produces(MediaType.APPLICATION_JSON)
public class IdEncoderResource {

  @GET
  @Path("encode")
  public String encode(@QueryParam("id") int id, @QueryParam("format") String format) {
    return findConverter(format).encode(id);
  }

  @GET
  @Path("decode")
  public Integer decode(@QueryParam("id") String id, @QueryParam("format") String format) {
    return findConverter(format).decode(id);
  }

  static IdConverter findConverter(String name){
    if (name != null) {
      switch (name.toUpperCase().trim()) {
        case "LATIN36":
          return IdConverter.LATIN36;
        case "LATIN32":
          return IdConverter.LATIN32;
        case "HEX":
          return IdConverter.HEX;
        case "BASE64":
          return IdConverter.BASE64;
      }
    }
    return IdConverter.LATIN29;
  }
}
