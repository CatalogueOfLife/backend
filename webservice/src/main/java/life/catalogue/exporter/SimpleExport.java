package life.catalogue.exporter;

import life.catalogue.WsServerConfig;
import life.catalogue.api.model.ExportRequest;
import life.catalogue.api.model.SimpleName;
import life.catalogue.api.vocab.DataFormat;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.io.TermWriter;
import life.catalogue.db.PgUtils;
import life.catalogue.db.mapper.NameUsageMapper;
import life.catalogue.img.ImageService;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.Term;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.function.Consumer;

abstract class SimpleExport extends DatasetExportJob implements Consumer<SimpleName> {
  private static final Logger LOG = LoggerFactory.getLogger(SimpleExport.class);

  private final Term rowType;
  private final List<Term> cols;
  TermWriter writer;

  public SimpleExport(Term rowType, ExportRequest req, int userKey, SqlSessionFactory factory, WsServerConfig cfg, ImageService imageService, List<Term> cols) {
    super(req, userKey, req.getFormat(), false, factory, cfg, imageService);
    if (!req.isSimple()) {
      throw new IllegalArgumentException("Requested export is not simple");
    }
    this.rowType = rowType;
    this.cols = cols;
  }

  @Override
  protected void export() throws Exception {
    writer = new TermWriter.TSV(tmpDir, rowType, cols);
    try (SqlSession session = factory.openSession()) {
      NameUsageMapper num = session.getMapper(NameUsageMapper.class);
      PgUtils.consume(() -> num.processTreeSimple(req.toTreeTraversalParameter(), false, false), this);
      LOG.info("Written {} records to simple {} for dataset {}", counter.size(), rowType.prefixedName(), req.getDatasetKey());
    } finally {
      writer.close();
    }
  }

  @Override
  public void accept(SimpleName sn) {
    counter.inc(sn);
    write(sn);
  }

  abstract void write(SimpleName sn);
}
