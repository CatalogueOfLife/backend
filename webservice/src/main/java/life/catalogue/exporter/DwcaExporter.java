package life.catalogue.exporter;

import freemarker.template.Template;
import freemarker.template.TemplateException;
import it.unimi.dsi.fastutil.ints.Int2IntMap;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.*;
import life.catalogue.common.io.TermWriter;
import life.catalogue.common.io.UTF8IoUtils;
import life.catalogue.db.mapper.NameRelationMapper;
import life.catalogue.db.mapper.ProjectSourceMapper;
import life.catalogue.db.mapper.SectorMapper;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.gbif.dwc.Archive;
import org.gbif.dwc.ArchiveField;
import org.gbif.dwc.ArchiveFile;
import org.gbif.dwc.MetaDescriptorWriter;
import org.gbif.dwc.terms.DcTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.GbifTerm;
import org.gbif.dwc.terms.Term;
import org.gbif.nameparser.api.NomCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class DwcaExporter extends ArchiveExporter {
  private static final Logger LOG = LoggerFactory.getLogger(DwcaExporter.class);
  private static final String EML_FILENAME = "eml.xml";
  private TermWriter writer2;
  private ProjectSourceMapper projectSourceMapper;
  private NameRelationMapper nameRelMapper;
  private SectorMapper sectorMapper;
  private final Archive arch = new Archive();
  private final Int2IntMap sector2datasetKeys = new Int2IntOpenHashMap();

  public DwcaExporter(ExportRequest req, SqlSessionFactory factory, File exportDir) {
    super(req, factory, exportDir);
  }

  public static DwcaExporter dataset(int datasetKey, int userKey, SqlSessionFactory factory, File exportDir) {
    ExportRequest req = new ExportRequest();
    req.setDatasetKey(datasetKey);
    req.setUserKey(userKey);
    return new DwcaExporter(req, factory, exportDir);
  }

  @Override
  protected void init(SqlSession session) {
    sectorMapper = session.getMapper(SectorMapper.class);
    projectSourceMapper = session.getMapper(ProjectSourceMapper.class);
    nameRelMapper = session.getMapper(NameRelationMapper.class);
    additionalWriter(defineSpeciesProfile());
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
  void exportMetadata(Dataset d) throws IOException {
    // main dataset metadata
    EmlWriter.write(d, new File(tmpDir, EML_FILENAME));

    // extract unique source datasets if sectors were given
    Set<Integer> sourceKeys = new HashSet<>(sector2datasetKeys.values());
    // for releases and projects also include an EML for each source dataset as defined by all sectors
    for (Integer key : sourceKeys) {
      ArchivedDataset src = null;
      if (DatasetOrigin.MANAGED == d.getOrigin()) {
        src = projectSourceMapper.getProjectSource(key, datasetKey);
      } else if (DatasetOrigin.RELEASED == d.getOrigin()) {
        src = projectSourceMapper.getReleaseSource(key, datasetKey);
      }
      if (src == null) {
        LOG.warn("Skip missing dataset {} for archive metadata", key);
        return;
      }
      File f = new File(tmpDir, String.format("dataset/%s.xml", key));
      EmlWriter.write(projectSourceMapper.getReleaseSource(key, datasetKey), f);
    }
  }

  @Override
  Term[] define(EntityType entity) {
    switch (entity) {
      case NAME_USAGE:
        return new Term[]{DwcTerm.Taxon, DwcTerm.taxonID,
          DwcTerm.parentNameUsageID,
          DwcTerm.acceptedNameUsageID,
          DwcTerm.originalNameUsageID,
          DwcTerm.datasetID,
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
        return new Term[]{GbifTerm.VernacularName, DwcTerm.taxonID,
          DcTerm.language,
          DwcTerm.vernacularName};
      case DISTRIBUTION:
        return new Term[]{GbifTerm.Distribution, DwcTerm.taxonID,
          DwcTerm.occurrenceStatus,
          DwcTerm.locationID,
          DwcTerm.locality,
          DwcTerm.countryCode,
          DcTerm.source};
    }
    return null;
  }

  Term[] defineSpeciesProfile() {
    return new Term[]{GbifTerm.SpeciesProfile, DwcTerm.taxonID,
      GbifTerm.isExtinct,
      GbifTerm.isMarine,
      GbifTerm.isFreshwater,
      GbifTerm.isTerrestrial
    };
  }

  void write(NameUsageBase u) {
    Name n = u.getName();
    writer.set(DwcTerm.taxonID, u.getId());

    if (u.getSectorKey() != null) {
      int sk = u.getSectorKey();
      if (!sector2datasetKeys.containsKey(sk)) {
        Sector s = sectorMapper.get(DSID.of(datasetKey, u.getSectorKey()));
        sector2datasetKeys.put(sk, (int) s.getSubjectDatasetKey());
      }
      writer.set(DwcTerm.datasetID, sector2datasetKeys.get(sk));
    }

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

    for (NameRelation rel : nameRelMapper.listByType(n, NomRelType.BASIONYM)) {
      writer.set(DwcTerm.originalNameUsageID, rel.getRelatedNameId());
    }
  }

  void write(String taxonID, VernacularName vn) {
    writer.set(DwcTerm.taxonID, taxonID);
    writer.set(DcTerm.language, vn.getLanguage());
    writer.set(DwcTerm.vernacularName, vn.getName());
  }

  void write(String taxonID, Distribution d) {
    writer.set(DwcTerm.taxonID, taxonID);
    writer.set(DwcTerm.occurrenceStatus, d.getStatus());
    if (d.getGazetteer() == Gazetteer.TEXT) {
        writer.set(DwcTerm.locality, d.getArea());
    } else if (d.getGazetteer() == Gazetteer.ISO) {
        writer.set(DwcTerm.countryCode, d.getArea());
    } else {
        writer.set(DwcTerm.locationID, d.getGazetteer().locationID(d.getArea()));
    }
    if (d.getReferenceId() != null) {
      writer.set(DcTerm.source, refCache.getUnchecked(d.getReferenceId()));
    }
  }

  @Override
  protected void bundle() throws IOException {
    if (writer2 != null) {
      writer2.close();
    }
    addMeta();
    super.bundle();
  }

  private void addMeta() throws IOException {
    arch.setMetadataLocation(EML_FILENAME);
    arch.setCore(buildArchiveFile(EntityType.NAME_USAGE));
    for (EntityType type : List.of(EntityType.VERNACULAR, EntityType.DISTRIBUTION)) {
      arch.addExtension(buildArchiveFile(type));
    }
    arch.addExtension(buildArchiveFile(defineSpeciesProfile()));

    File metaFile = new File(tmpDir, "meta.xml");
    MetaDescriptorWriter.writeMetaFile(metaFile, arch);
  }

  private ArchiveField field(Term term, int idx) {
    ArchiveField f = new ArchiveField();
    f.setIndex(idx);
    f.setTerm(term);
    return f;
  }

  private ArchiveFile buildArchiveFile(EntityType type) {
    return buildArchiveFile(define(type));
  }

  private ArchiveFile buildArchiveFile(Term... def) {
    ArchiveFile af = ArchiveFile.buildTabFile();
    af.setArchive(arch);
    af.setEncoding("utf-8");
    af.setIgnoreHeaderLines(1);
    af.setRowType(def[0]);
    af.addLocation(TermWriter.filename(af.getRowType()));
    af.setId(field(def[1], 0));

    int idx=1;
    while (idx < def.length) {
      af.addField(field(def[idx], idx-1));
      idx++;
    }

    return af;
  }

}
