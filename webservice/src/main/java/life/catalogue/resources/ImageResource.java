package life.catalogue.resources;

import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.common.ws.MoreMediaTypes;
import life.catalogue.dao.DatasetInfoCache;
import life.catalogue.db.mapper.DatasetMapper;
import life.catalogue.dw.auth.Roles;
import life.catalogue.img.ImageService;
import life.catalogue.img.ImageServiceFS;
import life.catalogue.img.ImgConfig;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;

import jakarta.annotation.security.RolesAllowed;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/dataset/{key}/logo")
@SuppressWarnings("static-method")
public class ImageResource {
  private static final Logger LOG = LoggerFactory.getLogger(ImageResource.class);
  private final ImageService imgService;
  private final SqlSessionFactory factory;

  public ImageResource(ImageService imgService, SqlSessionFactory factory) {
    this.imgService = imgService;
    this.factory = factory;
  }

  @GET
  @Produces("image/png")
  public BufferedImage logo(@PathParam("key") int key, @QueryParam("size") @DefaultValue("small") ImgConfig.Scale scale) {
    return imgService.datasetLogo(key, scale);
  }
  
  @POST
  @Consumes({MediaType.APPLICATION_OCTET_STREAM,
      MoreMediaTypes.IMG_BMP, MoreMediaTypes.IMG_PNG, MoreMediaTypes.IMG_GIF,
      MoreMediaTypes.IMG_JPG, MoreMediaTypes.IMG_PSD, MoreMediaTypes.IMG_TIFF
  })
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Response uploadLogo(@PathParam("key") int key, InputStream img) throws IOException {
    imgService.putDatasetLogo(key, ImageServiceFS.read(img));
    updateDatasetMetadata(key, imgService.logoUrl(key));
    return Response.ok().build();
  }
  
  @DELETE
  @RolesAllowed({Roles.ADMIN, Roles.EDITOR})
  public Response deleteLogo(@PathParam("key") int key) throws IOException {
    imgService.putDatasetLogo(key, null);
    updateDatasetMetadata(key, null);
    return Response.ok().build();
  }

  private void updateDatasetMetadata(int datasetKey, URI logoUrl) {
    try (SqlSession session = factory.openSession(true)) {
      var dm = session.getMapper(DatasetMapper.class);
      var d =dm.get(datasetKey);
      d.setLogo(logoUrl);
      dm.update(d);
    }
  }

  @GET
  @Path("/source/{id}")
  @Produces("image/png")
  public BufferedImage sourceLogo(@PathParam("key") int datasetKey, @PathParam("id") int id, @QueryParam("size") @DefaultValue("small") ImgConfig.Scale scale) {
    DatasetOrigin origin = DatasetInfoCache.CACHE.info(datasetKey).origin;
    if (!origin.isProjectOrRelease()) {
      throw new IllegalArgumentException("Dataset "+datasetKey+" is " + origin);
    } else if (origin.isRelease()) {
      return imgService.datasetLogoArchived(id, datasetKey, scale);
    }
    // show the latest for projects
    return imgService.datasetLogo(id, scale);
  }
}
