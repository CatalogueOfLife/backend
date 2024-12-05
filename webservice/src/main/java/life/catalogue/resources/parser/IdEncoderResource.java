package life.catalogue.resources.parser;

import life.catalogue.common.id.IdConverter;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;

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
