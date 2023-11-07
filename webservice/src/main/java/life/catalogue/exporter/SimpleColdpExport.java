package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.img.ImageService;

import java.util.List;

import org.apache.ibatis.session.SqlSessionFactory;

public class SimpleColdpExport extends SimpleExport {
  public SimpleColdpExport(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(ColdpTerm.NameUsage, req, userKey, factory, cfg, imageService, List.of(
      ColdpTerm.ID,
      ColdpTerm.parentID,
      ColdpTerm.status,
      ColdpTerm.rank,
      ColdpTerm.scientificName,
      ColdpTerm.authorship
    ));
    if (req.getFormat() != DataFormat.COLDP) {
      throw new IllegalArgumentException("Unsupported simple export format " + req.getFormat());
    }
  }

  @Override
  void write(SimpleName sn) {
    writer.set(ColdpTerm.ID, sn.getId());
    writer.set(ColdpTerm.parentID, sn.getParentId());
    writer.set(ColdpTerm.status, sn.getStatus());
    writer.set(ColdpTerm.rank, sn.getRank());
    writer.set(ColdpTerm.scientificName, sn.getName());
    writer.set(ColdpTerm.authorship, sn.getAuthorship());
  }
}
