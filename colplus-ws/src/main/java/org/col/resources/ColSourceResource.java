package org.col.resources;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.Context;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.session.SqlSession;
import org.col.api.model.ColSource;
import org.col.db.mapper.ColSourceMapper;
import org.col.dw.auth.Roles;
import org.col.dw.jersey.MoreMediaTypes;
import org.col.img.ImageService;
import org.col.img.ImgConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/colsource")
@Produces(MediaType.APPLICATION_JSON)
@SuppressWarnings("static-method")
public class ColSourceResource extends CRUDResource<ColSource> {

  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(ColSourceResource.class);
  private final ImageService imgService;

  public ColSourceResource(ImageService imgService) {
    super(ColSource.class, ColSourceMapper.class);
    this.imgService = imgService;
  }
  
  @GET
  public List<ColSource> list(@Context SqlSession session, @QueryParam("datasetKey") Integer datasetKey) {
    return session.getMapper(ColSourceMapper.class).list(datasetKey);
  }

  @GET
  @Path("{key}/edit")
  public ColSource getEditable(@Context SqlSession session, @Param("key") int key) {
    return session.getMapper(ColSourceMapper.class).getEditable(key);
  }
  
  @GET
  @Path("{key}/logo")
  @Produces("image/png")
  public BufferedImage logo(@PathParam("key") int key, @QueryParam("size") @DefaultValue("small") ImgConfig.Scale scale) {
    //return Response.ok(imgService.datasetLogo(key, scale)).build();
    return imgService.colSourceLogo(key, scale);
  }
  
  @POST
  @Path("{key}/logo")
  @Consumes({MediaType.APPLICATION_OCTET_STREAM,
      MoreMediaTypes.IMG_BMP, MoreMediaTypes.IMG_PNG, MoreMediaTypes.IMG_GIF,
      MoreMediaTypes.IMG_JPG ,MoreMediaTypes.IMG_PSD, MoreMediaTypes.IMG_TIFF
  })
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Response uploadLogo(@PathParam("key") int key, InputStream img) throws IOException {
    imgService.putColSourceLogo(key, ImageService.read(img));
    return Response.ok().build();
  }
  
  @DELETE
  @Path("{key}/logo")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Response deleteLogo(@PathParam("key") int key) throws IOException {
    imgService.putColSourceLogo(key, null);
    return Response.ok().build();
  }
}
