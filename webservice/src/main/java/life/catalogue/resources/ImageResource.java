package life.catalogue.resources;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.dw.auth.Roles;
import life.catalogue.dw.jersey.MoreMediaTypes;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.img.ImgConfig;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/image")
@SuppressWarnings("static-method")
public class ImageResource {
  private static final Logger LOG = LoggerFactory.getLogger(ImageResource.class);
  private final ImageService imgService;

  public ImageResource(ImageService imgService) {
    this.imgService = imgService;
  }

  @GET
  @Path("{key}/logo")
  @Produces("image/png")
  public BufferedImage logo(@PathParam("key") int key, @QueryParam("size") @DefaultValue("small") ImgConfig.Scale scale) {
    return imgService.datasetLogo(key, scale);
  }
  
  @POST
  @Path("{key}/logo")
  @Consumes({MediaType.APPLICATION_OCTET_STREAM,
      MoreMediaTypes.IMG_BMP, MoreMediaTypes.IMG_PNG, MoreMediaTypes.IMG_GIF,
      MoreMediaTypes.IMG_JPG, MoreMediaTypes.IMG_PSD, MoreMediaTypes.IMG_TIFF
  })
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Response uploadLogo(@PathParam("key") int key, InputStream img) throws IOException {
    imgService.putDatasetLogo(key, ImageServiceFS.read(img));
    return Response.ok().build();
  }
  
  @DELETE
  @Path("{key}/logo")
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Response deleteLogo(@PathParam("key") int key) throws IOException {
    imgService.putDatasetLogo(key, null);
    return Response.ok().build();
  }

  @GET
  @Path("/{key}/source/{id}/logo")
  @Produces("image/png")
  public BufferedImage sourceLogo(@PathParam("key") int datasetKey, @PathParam("id") int id, @QueryParam("size") @DefaultValue("small") ImgConfig.Scale scale) {
    DatasetOrigin origin = DatasetInfoCache.CACHE.info(datasetKey).origin;
    if (!origin.isManagedOrRelease()) {
      throw new IllegalArgumentException("Dataset "+datasetKey+" is not a project");
    } else if (origin == DatasetOrigin.RELEASE) {
      return imgService.archiveDatasetLogo(id, datasetKey, scale);
    }
    return imgService.datasetLogo(id, scale);
  }
}
