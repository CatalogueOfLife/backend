package life.catalogue.exporter;

import life.catalogue.api.model.NameUsageBase;
import life.catalogue.api.model.Synonym;
import life.catalogue.api.model.Taxon;
import life.catalogue.api.model.VernacularName;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Environment;
import life.catalogue.common.io.TermWriter;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class DwcaExporter extends ArchiveExporter {

  private TermWriter writer2;

  public DwcaExporter(ExportRequest req, SqlSessionFactory factory, File exportDir) {
    super(req, factory, exportDir);
  }

  public static DwcaExporter dataset(int datasetKey, int userKey, SqlSessionFactory factory, File exportDir) {
    ExportRequest req = new ExportRequest();
    req.setDatasetKey(datasetKey);
    req.setUserKey(userKey);
    return new DwcaExporter(req, factory, exportDir);
  }

  private void additionalWriter(Term[] terms) {
    try {
      if (writer2 != null) {
        writer2.close();
      }
      writer2 = new TermWriter(tmpDir, terms[0], terms[1], List.of(Arrays.copyOfRange(terms, 2, terms.length)));
    } catch (IOException e) {
      throw new RuntimeException(e);
    }
  }

  @Override
  Term[] define(EntityType entity) {
    switch (entity) {
      case NAME_USAGE:
        additionalWriter(new Term[]{GbifTerm.SpeciesProfile, DwcTerm.taxonID, GbifTerm.isExtinct, GbifTerm.isMarine, GbifTerm.isFreshwater, GbifTerm.isTerrestrial});
        return new Term[]{DwcTerm.Taxon, DwcTerm.taxonID, DwcTerm.parentNameUsageID, DwcTerm.acceptedNameUsageID, DwcTerm.taxonomicStatus, DwcTerm.taxonRank, DwcTerm.scientificName, DwcTerm.taxonRemarks};
      case VERNACULAR:
        return new Term[]{GbifTerm.VernacularName, DwcTerm.taxonID, DcTerm.language, DwcTerm.vernacularName};
    }
    return null;
  }

  void write(NameUsageBase u) {
    writer.set(DwcTerm.taxonID, u.getId());
    if (u.isSynonym()) {
      writer.set(DwcTerm.acceptedNameUsageID, u.getParentId());
      ((Synonym)u).getAccepted().setExtinct(null); // this removes the dagger symbol from label below !!!

    } else {
      writer.set(DwcTerm.parentNameUsageID, u.getParentId());
      Taxon t = (Taxon) u;
      if (t.isExtinct() != null || (t.getEnvironments() != null && !t.getEnvironments().isEmpty())) {
        writer2.set(DwcTerm.taxonID, u.getId());
        writer2.set(GbifTerm.isExtinct, t.isExtinct());
        if (t.getEnvironments() != null && !t.getEnvironments().isEmpty()) {
          writer2.set(GbifTerm.isMarine, t.getEnvironments().contains(Environment.MARINE));
          writer2.set(GbifTerm.isFreshwater, t.getEnvironments().contains(Environment.FRESHWATER));
          writer2.set(GbifTerm.isTerrestrial, t.getEnvironments().contains(Environment.TERRESTRIAL));
        }
        try {
          writer2.next();
        } catch (IOException e) {
          throw new RuntimeException(e);
        }
        t.setExtinct(null); // this removes the dagger symbol from label below !!!
      }
    }
    writer.set(DwcTerm.taxonomicStatus, u.getStatus());
    writer.set(DwcTerm.taxonRank, u.getName().getRank());
    writer.set(DwcTerm.scientificName, u.getLabel());
    writer.set(DwcTerm.taxonRemarks, u.getRemarks());
  }

  void write(String taxonID, VernacularName vn) {
    writer.set(DwcTerm.taxonID, taxonID);
    writer.set(DcTerm.language, vn.getLanguage());
    writer.set(DwcTerm.vernacularName, vn.getName());
  }

  @Override
  protected void bundle() throws IOException {
    if (writer2 != null) {
      writer2.close();
    }
    super.bundle();
  }
}
