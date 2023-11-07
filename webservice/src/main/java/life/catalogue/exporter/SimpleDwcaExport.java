package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.img.ImageService;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.dwc.terms.DwcTerm;

import java.util.List;

public class SimpleDwcaExport extends SimpleExport {
  public SimpleDwcaExport(ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService) {
    super(DwcTerm.Taxon, req, userKey, factory, cfg, imageService, List.of(
      DwcTerm.taxonID,
      DwcTerm.parentNameUsageID,
      DwcTerm.acceptedNameUsageID,
      DwcTerm.taxonomicStatus,
      DwcTerm.taxonRank,
      DwcTerm.scientificName,
      DwcTerm.scientificNameAuthorship
    ));
    if (req.getFormat() != DataFormat.DWCA) {
      throw new IllegalArgumentException("Unsupported simple export format " + req.getFormat());
    }
  }

  @Override
  void write(SimpleName sn) {
    writer.set(DwcTerm.taxonID, sn.getId());
    if (sn.getStatus() == null || sn.getStatus().isTaxon()) {
      writer.set(DwcTerm.parentNameUsageID, sn.getParentId());
    } else {
      writer.set(DwcTerm.acceptedNameUsageID, sn.getParentId());
    }
    writer.set(DwcTerm.taxonomicStatus, sn.getStatus());
    writer.set(DwcTerm.taxonRank, sn.getRank());
    writer.set(DwcTerm.scientificName, sn.getName());
    writer.set(DwcTerm.scientificNameAuthorship, sn.getAuthorship());
  }
}
