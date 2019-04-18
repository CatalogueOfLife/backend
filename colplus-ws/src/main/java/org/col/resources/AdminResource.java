package org.col.resources;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.col.api.vocab.Datasets;
import org.col.config.NormalizerConfig;
import org.col.common.io.DownloadUtil;
import org.col.dao.TaxonDao;
import org.col.dw.auth.Roles;
import org.col.img.ImageService;
import org.col.img.LogoUpdateJob;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Path("/admin")
@Produces(MediaType.APPLICATION_JSON)
@RolesAllowed({Roles.ADMIN})
public class AdminResource {
  
  @SuppressWarnings("unused")
  private static final Logger LOG = LoggerFactory.getLogger(AdminResource.class);
  private final SqlSessionFactory factory;
  private final DownloadUtil downloader;
  private final NormalizerConfig cfg;
  private final ImageService imgService;
  private final TaxonDao tdao;
  
  public AdminResource(SqlSessionFactory factory, DownloadUtil downloader, NormalizerConfig cfg, ImageService imgService, TaxonDao tdao) {
    this.factory = factory;
    this.imgService = imgService;
    this.cfg = cfg;
    this.downloader = downloader;
    this.tdao = tdao;
  }
  
  @POST
  @Path("/logo-update")
  public String updateAllLogos() {
    LogoUpdateJob.updateAllAsync(factory, downloader, cfg::scratchFile, imgService);
    return "Started Logo Updater";
  }
  
  @POST
  @Path("/sector-count-update")
  public boolean updateAllSectorCounts() {
    try (SqlSession session = factory.openSession()) {
      tdao.updateAllSectorCounts(Datasets.DRAFT_COL, factory);
      session.commit();
      return true;
    }
  }
}
