package org.col.admin.resources;

import javax.annotation.security.RolesAllowed;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;

import org.apache.ibatis.session.SqlSessionFactory;
import org.col.admin.assembly.DecisionRematcher;
import org.col.admin.config.NormalizerConfig;
import org.col.common.io.DownloadUtil;
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
  
  public AdminResource(SqlSessionFactory factory, DownloadUtil downloader, NormalizerConfig cfg, ImageService imgService) {
    this.factory = factory;
    this.imgService = imgService;
    this.cfg = cfg;
    this.downloader = downloader;
  }
  
  @POST
  @Path("/logo-update")
  public String updateAllLogos() {
    LogoUpdateJob.updateAllAsync(factory, downloader, cfg::scratchDir, imgService);
    return "Started Logo Updater";
  }
  
  @POST
  @Path("/rematch-decisions")
  public String updateDecisions(@QueryParam("datasetKey") Integer datasetKey) {
    DecisionRematcher rem = new DecisionRematcher(factory, datasetKey);
    Thread t = new Thread(rem, "decision-rematcher");
    t.start();
    return "Started Decision Rematcher";
  }
  
}
