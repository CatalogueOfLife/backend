package life.catalogue.exporter;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.NomStatus;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NomCode;

import java.io.File;

public class ColdpExporter extends ArchiveExporter {

  public ColdpExporter(ExportRequest req, SqlSessionFactory factory, File exportDir) {
    super(req, factory, exportDir);
  }

  public static ColdpExporter dataset(int datasetKey, int userKey, SqlSessionFactory factory, File exportDir) {
    ExportRequest req = new ExportRequest();
    req.setDatasetKey(datasetKey);
    req.setUserKey(userKey);
    return new ColdpExporter(req, factory, exportDir);
  }

  @Override
  void exportMetadata(Dataset d) {

  }

  @Override
  Term[] define(EntityType entity) {
    switch (entity) {
      case NAME_USAGE:
        return new Term[]{DwcTerm.Taxon, DwcTerm.taxonID,
          DwcTerm.parentNameUsageID,
          DwcTerm.acceptedNameUsageID,
          DwcTerm.originalNameUsageID,
          DwcTerm.taxonomicStatus,
          DwcTerm.taxonRank,
          DwcTerm.scientificName,
          GbifTerm.genericName,
          DwcTerm.specificEpithet,
          DwcTerm.infraspecificEpithet,
          DwcTerm.nameAccordingTo,
          DwcTerm.namePublishedIn,
          DwcTerm.nomenclaturalCode,
          DwcTerm.nomenclaturalStatus,
          DwcTerm.taxonRemarks,
          DcTerm.references
        };
      case VERNACULAR:
        return new Term[]{GbifTerm.VernacularName, DwcTerm.taxonID, DcTerm.language, DwcTerm.vernacularName};
    }
    return null;
  }

  void write(NameUsageBase u) {
    Name n = u.getName();
    writer.set(DwcTerm.taxonID, u.getId());
    writer.set(DwcTerm.taxonomicStatus, u.getStatus());
    writer.set(DwcTerm.taxonRank, n.getRank());
    writer.set(DwcTerm.scientificName, u.getLabel());
    writer.set(DwcTerm.taxonRemarks, u.getRemarks());
    writer.set(DcTerm.references, u.getLink());
    if (n.getPublishedInId() != null) {
      writer.set(DwcTerm.namePublishedIn, refCache.getUnchecked(n.getPublishedInId()));
    }
    writer.set(DwcTerm.nameAccordingTo, u.getAccordingTo());
    if (n.isBinomial()) {
      writer.set(GbifTerm.genericName, n.getGenus());
      writer.set(DwcTerm.specificEpithet, n.getSpecificEpithet());
      writer.set(DwcTerm.infraspecificEpithet, n.getInfraspecificEpithet());
    }
    writer.set(DwcTerm.nomenclaturalCode, n.getCode(), NomCode::getAcronym);
    writer.set(DwcTerm.nomenclaturalStatus, n.getNomStatus(), NomStatus::getBotanicalLabel);

    if (u.isSynonym()) {
      Synonym s = (Synonym) u;
    } else {
      Taxon t = (Taxon) u;
    }
  }

  void write(String taxonID, VernacularName vn) {
    writer.set(DwcTerm.taxonID, taxonID);
    writer.set(DcTerm.language, vn.getLanguage());
    writer.set(DwcTerm.vernacularName, vn.getName());
  }

}
