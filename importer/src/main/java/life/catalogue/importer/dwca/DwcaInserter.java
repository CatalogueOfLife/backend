package life.catalogue.importer.dwca;

import com.google.common.base.Splitter;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;

import life.catalogue.api.model.*;
import life.catalogue.api.util.ObjectUtils;
import life.catalogue.api.vocab.*;
import life.catalogue.api.vocab.terms.EolDocumentTerm;
import life.catalogue.api.vocab.terms.EolReferenceTerm;
import life.catalogue.coldp.ColdpTerm;
import life.catalogue.common.text.StringUtils;
import life.catalogue.csv.CsvReader;
import life.catalogue.csv.DwcaReader;
import life.catalogue.dao.ReferenceFactory;
import life.catalogue.importer.DataCsvInserter;
import life.catalogue.importer.NormalizationFailedException;
import life.catalogue.importer.store.ImportStore;
import life.catalogue.importer.store.model.NameUsageData;
import life.catalogue.importer.store.model.RelationData;
import life.catalogue.importer.store.model.UsageData;
import life.catalogue.metadata.coldp.ColdpMetadataParser;
import life.catalogue.metadata.eml.EmlParser;

import life.catalogue.parser.NameParser;

import org.gbif.dwc.terms.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.commons.io.FilenameUtils;

import org.gbif.nameparser.api.NomCode;
import org.gbif.nameparser.api.Rank;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static life.catalogue.common.collection.CollectionUtils.isEmpty;
import static life.catalogue.common.lang.Exceptions.runtimeInterruptIfCancelled;

/**
 *
 */
public class DwcaInserter extends DataCsvInserter {
  private static final Logger LOG = LoggerFactory.getLogger(DwcaInserter.class);
  private static final UnknownTerm DNA_EXTENSION = UnknownTerm.build("http://rs.gbif.org/terms/1.0/DnaDerivedData", "DnaDerivedData", true);
  private static final List<Splitter> COMMON_SPLITTER = Lists.newArrayList();
  static {
    for (char del : "[|;, ]".toCharArray()) {
      COMMON_SPLITTER.add(Splitter.on(del).trimResults().omitEmptyStrings());
    }
  }

  private DwcInterpreter inter;

  public DwcaInserter(ImportStore store, Path folder, DatasetSettings settings, ReferenceFactory refFactory) throws IOException {
    super(folder, DwcaReader.from(folder), store, settings, refFactory);
  }
  
  /**
   * Inserts DWCA data from a source folder into the normalizer store.
   * Before inserting it does a quick check to see if all required files are existing.
   */
  @Override
  protected void insert() throws NormalizationFailedException, InterruptedException {
    inter = new DwcInterpreter(settings, reader.getMappingFlags(), refFactory, store);

    // taxon core only, extensions are interpreted later
    insertEntities(reader, DwcTerm.Taxon,
        inter::interpretUsage,
        store::createNameAndUsage
    );

    // taxon core again, inserting only parent, accepted and basionym relations.
    // This 2nd iteration knows which taxon ids exist and which don't
    updateTaxonRelations();

    insertNameRelations(reader, ColdpTerm.NameRelation,
        inter::interpretNameRelation,
        Issue.NAME_ID_INVALID
    );

    interpretTypeMaterial(reader, DwcTerm.Occurrence,
      inter::interpretTypeMaterial
    );

    // https://github.com/CatalogueOfLife/backend/issues/1071
    // TODO: read type specimen extension and create type material or name relation for type names
    // http://rs.gbif.org/extension/gbif/1.0/typesandspecimen.xml

    insertTaxonEntities(reader, GbifTerm.Distribution,
        inter::interpretDistribution,
        inter::taxonID,
        (t, d) -> t.distributions.add(d)
    );

    insertTaxonEntities(reader, GbifTerm.VernacularName,
        inter::interpretVernacularName,
        inter::taxonID,
        (t, vn) -> t.vernacularNames.add(vn)
    );

    insertTaxonEntities(reader, GbifTerm.Multimedia,
        inter::interpretGbifMedia,
        inter::taxonID,
        (t, d) -> t.media.add(d)
    );
    insertTaxonEntities(reader, AcTerm.Multimedia,
      inter::interpretAcMedia,
      inter::taxonID,
      (t, d) -> t.media.add(d)
    );

    insertTaxonEntities(reader, DwcTerm.MeasurementOrFact,
      inter::interpretMeasurements,
      inter::taxonID,
      (t, p) -> t.properties.add(p)
    );

    insertTaxonEntities(reader, GbifTerm.Description,
      inter::interpretDescriptions,
      inter::taxonID,
      (t, p) -> t.properties.add(p)
    );
    // extract etymology from descriptions
    AtomicInteger cnt = new AtomicInteger();
    reader.stream(GbifTerm.Description).forEach(rec -> {
      runtimeInterruptIfCancelled(DataCsvInserter.INTERRUPT_MESSAGE);
      if (rec.getOrDefault(DcTerm.type, "").equalsIgnoreCase("etymology")) {
        String id = inter.taxonID(rec);
        if (id != null) {
          String description = rec.get(DcTerm.description);
          var nn = store.names().objByID(id);
          if (nn != null && nn.getName().getEtymology() == null && description != null) {
            nn.getName().setEtymology(description);
            store.names().update(nn);
            cnt.incrementAndGet();
          }
        }
      }
    });
    LOG.info("Update {} names with etymology from descriptions", cnt.get());

    insertTaxonEntities(reader, GbifTerm.Reference,
      inter::interpretReference,
      inter::taxonID,
      (t, r) -> {
        if (r.getId() == null || !store.references().contains(r.getId())) {
          store.references().create(r);
        }
        if (t.isNameUsageBase() && r.getId() != null) {
          t.asNameUsageBase().getReferenceIds().add(r.getId());
        }
      }
    );

    insertTaxonEntities(reader, EolReferenceTerm.Reference,
      inter::interpretEolReference,
      inter::taxonID,
      (t, r) -> {
        if (store.references().create(r) && t.isNameUsageBase()) {
          t.asNameUsageBase().getReferenceIds().add(r.getId());
        }
      }
    );

    interpretTreatment(reader, EolDocumentTerm.Document,
      inter::interpretTreatment
    );

    insertTaxonEntities(reader, GbifTerm.SpeciesProfile,
      inter::interpretSpeciesProfile,
      inter::taxonID,
      (u, sp) -> {
        if (u.usage.isTaxon()) {
          Taxon t = u.asTaxon();
          // we can get multiple species profile records - aggregate them!
          if (t.isExtinct() == null) {
            t.setExtinct(sp.isExtinct());
          }

          if (sp.getEnvironments() != null) {
            if (t.getEnvironments() != null) {
              t.getEnvironments().addAll(sp.getEnvironments());
            } else {
              t.setEnvironments(sp.getEnvironments());
            }
          }

          if (!sp.getReferenceIds().isEmpty()) {
            t.getReferenceIds().addAll(sp.getReferenceIds());
          }

          if (!sp.properties.isEmpty()) {
            for (var kv : sp.properties.entrySet()) {
              var tp = new TaxonProperty();
              tp.setVerbatimKey(sp.getVerbatimKey());
              tp.setProperty(kv.getKey().prefixedName());
              tp.setValue(kv.getValue());
              if (!sp.getReferenceIds().isEmpty()) {
                tp.setReferenceId(sp.getReferenceIds().get(0));
              }
              u.properties.add(tp);
            }
          }
        }
      }
    );


    insertTaxonEntities(reader, GbifTerm.Identifier,
      inter::interpretAltIdentifiers,
      inter::taxonID,
      (nu, alt) -> {
        if (!isEmpty(alt.getIdentifier())) {
          var u = nu.usage.asUsageBase();
          if (isEmpty(u.getIdentifier())) {
            u.setIdentifier(alt.getIdentifier());
          } else {
            u.getIdentifier().addAll(alt.getIdentifier());
          }
        }
      }
    );

    // just add verbatim data for these well know extensions without interpreting any data!
    insertVerbatimEntities(reader, GbifTerm.Image, GbifTerm.TypesAndSpecimen, DwcTerm.ResourceRelationship, DNA_EXTENSION);
  }

  /**
   * parentID can only hold one parent.
   * We prefer the acceptedID over the parentID in case of synonyms.
   */
  private void updateTaxonRelations() {
    final AtomicInteger counter = new AtomicInteger(0);
    runtimeInterruptIfCancelled(INTERRUPT_MESSAGE);
    if (getMappingFlags().isAcceptedNameMapped() || getMappingFlags().isParentNameMapped() || getMappingFlags().isOriginalNameMapped()) {
      store.usages().all().forEach(u -> {
        if (u.getVerbatimKey() != null) {
          final var v = store.getVerbatim(u.getVerbatimKey());
          final var nu = store.nameUsage(u);
          boolean modified = false;
          // accepted takes precedence
          if (getMappingFlags().isAcceptedNameMapped()) {
            modified = updateAccepted(v, nu);
          }
          // parent only if not set yet
          if (getMappingFlags().isParentNameMapped() && !nu.hasParentID()) {
            modified = updateParents(v, nu) || modified;
          }
          // basionym relation
          if (getMappingFlags().isOriginalNameMapped()) {
            modified = updateBasionyms(v, nu) || modified;
          }
          // persist
          if (modified) {
            store.updateNameAndUsage(nu);
            counter.incrementAndGet();
          }
          runtimeInterruptIfCancelled(INTERRUPT_MESSAGE);
        }
      });
      LOG.info("Updated {} taxon relations", counter.get());
      runtimeInterruptIfCancelled(INTERRUPT_MESSAGE);
    }
  }

  private boolean updateAccepted(VerbatimRecord v, NameUsageData nu) {
    var accepted = usagesByIdOrName(v, nu, true, DwcTerm.acceptedNameUsageID, Issue.ACCEPTED_ID_INVALID, DwcTerm.acceptedNameUsage, Origin.VERBATIM_ACCEPTED);
    if (accepted.isEmpty()) {
      // if status is synonym but we ain't got no idea of the accepted flag it
      if (nu.ud.isSynonym() || v.contains(Issue.ACCEPTED_ID_INVALID)) {
        v.add(Issue.ACCEPTED_NAME_MISSING);
        // now remove any denormed classification from this synonym to avoid parent relations
        nu.ud.classification = null;
        return true;
      }

    } else {
      // change the taxonomic status?
      if (!nu.ud.isSynonym()) {
        nu.ud.convertToSynonym(TaxonomicStatus.SYNONYM);
      }
      var iter = accepted.listIterator();
      while (iter.hasNext()) {
        int idx = iter.nextIndex();
        var acc = iter.next();
        if (idx > 0) {
          // if we have multiple accepted names we do not need to create
          nu.ud.proParteAcceptedIDs.add(acc.ud.getId());
        } else {
          nu.ud.asSynonym().setParentId(acc.ud.getId());
        }
        // for homotypic synonyms also create a homotypic name relation
        if (nu.nd.homotypic) {
          var rel = new RelationData<>(NomRelType.HOMOTYPIC, nu.ud.nameID, acc.nd.getId());
          rel.setVerbatimKey(v.getId());
          nu.nd.relations.add(rel);
        }
      }
      return true;
    }
    return false;
  }

  private boolean updateParents(VerbatimRecord v, NameUsageData nu) {
    var parents = usagesByIdOrName(v, nu, false, DwcTerm.parentNameUsageID, Issue.PARENT_ID_INVALID, DwcTerm.parentNameUsage, Origin.VERBATIM_PARENT);
    if (!parents.isEmpty()) {
      nu.ud.asNameUsageBase().setParentId(parents.getFirst().ud.getId());
      return true;
    }
    return false;
  }

  private boolean updateBasionyms(VerbatimRecord v, NameUsageData nu) {
    var basionyms = usagesByIdOrName(v, nu, false, DwcTerm.originalNameUsageID, Issue.BASIONYM_ID_INVALID, DwcTerm.originalNameUsage, Origin.VERBATIM_BASIONYM);
    if (!basionyms.isEmpty()) {
      var bas = basionyms.getFirst();
      var rel = new RelationData<>(NomRelType.BASIONYM, nu.ud.nameID, bas.nd.getId());
      rel.setVerbatimKey(v.getId());
      nu.nd.relations.add(rel);
      return true;
    }
    return false;
  }

  /**
   * Reads a verbatim given term that should represent a foreign key to another record via the taxonID.
   * If the value is not the same as the original records taxonID it tries to split the ids into multiple keys and lookup the matching nodes.
   *
   * Ignores IDs and names which are exactly the same as the child - often the terms are used to point to itself for accepted names or basionyms.
   *
   * @return list of potentially split ids with their matching usage found, otherwise null
   */
  private List<NameUsageData> usagesByIdOrName(VerbatimRecord v, NameUsageData t, boolean allowMultiple, DwcTerm idTerm, Issue invalidIdIssue, DwcTerm nameTerm, Origin createdNameOrigin) {
    List<NameUsageData> usages = Lists.newArrayList();
    final String unsplitIds = v.getRaw(idTerm);
    final String taxonID = t.ud.getId();
    boolean pointsToSelf = unsplitIds != null && unsplitIds.equals(taxonID);
    if (pointsToSelf) return usages;

    if (unsplitIds != null) {
      if (allowMultiple && getMappingFlags().getMultiValueDelimiters().containsKey(idTerm)) {
        usages.addAll(usagesByIds(getMappingFlags().getMultiValueDelimiters().get(idTerm).splitToList(unsplitIds), taxonID));
      } else {
        // match by taxonID to see if this is an existing identifier or if we should try to split it
        var nu = store.nameUsage(unsplitIds);
        if (nu != null) {
          usages.add(nu);

        } else if (allowMultiple){
          for (Splitter splitter : COMMON_SPLITTER) {
            List<String> vals = splitter.splitToList(unsplitIds);
            if (vals.size() > 1) {
              usages.addAll(usagesByIds(vals, taxonID));
              break;
            }
          }
        }
      }
      // could not find anything?
      if (usages.isEmpty()) {
        v.add(invalidIdIssue);
        LOG.info("{} {} not existing", idTerm.simpleName(), unsplitIds);
      }
    }

    if (usages.isEmpty() && v.hasTerm(nameTerm)) {
      // try to setup rel via the name if it is different
      String relatedName = v.get(nameTerm);
      pointsToSelf = relatedName.equals(t.nd.getName().getLabel());
      if (!pointsToSelf) {
        var ru = usageByName(nameTerm, v, t, createdNameOrigin);
        if (ru != null) {
          usages.add(ru);
        }
      }
    }
    return usages;
  }

  private List<NameUsageData> usagesByIds(Iterable<String> taxonIDs, String fromID) {
    List<NameUsageData> usages = Lists.newArrayList();
    for (String id : taxonIDs) {
      if (!id.equals(fromID)) {
        var nu = store.nameUsage(id);
        if (nu != null) {
          usages.add(nu);
        }
      }
    }
    return usages;
  }

  /**
   * Reads a verbatim given term that should represent a scientific name pointing to another record via the scientificName.
   * It first tries to lookup existing records by the canonical name with author, but falls back to authorless lookup if no matches.
   * If the name is the same as the original records scientificName it is ignored.
   *
   * If a name cannot be found it is created as explicit names
   *
   * @param nameTerm the term to read the scientific name from
   * @return the accepted node with its name. Null if no accepted name was mapped or equals the record itself
   */
  private NameUsageData usageByName(DwcTerm nameTerm, VerbatimRecord v, NameUsageData source, final Origin createdOrigin) {
    if (v.hasTerm(nameTerm)) {
      NomCode code = settings.getEnum(Setting.NOMENCLATURAL_CODE);

      try {
        final Name name = NameParser.PARSER.parse(v.get(nameTerm), Rank.UNRANKED, code, IssueContainer.VOID).get().getName();
        // force unranked name for non binomials or unparsed names, avoiding wrong parser decisions
        if (!name.isParsed() || !name.isBinomial()) {
          name.setRank(Rank.UNRANKED);
        }
        if (!name.getScientificName().equalsIgnoreCase(source.nd.getName().getScientificName())) {
          var matches = store.names().nameIdsByName(name.getScientificName()).stream()
            .map(store::nameUsage)
            .collect(Collectors.toCollection(ArrayList::new));
          // remove other authors, but allow names without authors
          if (!matches.isEmpty() && name.hasAuthorship()) {
            matches.removeIf(n -> n.nd.getName().hasAuthorship()
              && !StringUtils.equalsIgnoreCaseAndSpace(n.nd.getName().getAuthorship(), name.getAuthorship())
            );
          }
          // if we got one match, use it!
          if (matches.isEmpty()) {
            // create name
            LOG.debug("{} {} not existing, materialize it", nameTerm.simpleName(), name);
            return store.createProvisionalUsageFromSource(createdOrigin, name, source.ud, source.nd.getRank());

          } else{
            if (matches.size() > 1) {
              // still multiple matches, pick first and log critical issue!
              v.add(Issue.NAME_NOT_UNIQUE);
            }
            return matches.getFirst();
          }
        }
      } catch (InterruptedException e) {
        LOG.warn("NameParser got interrupted");
        Thread.currentThread().interrupt();
      }
    }
    return null;
  }

  /**
   * Reads the dataset metadata and puts it into the store
   */
  @Override
  public Optional<DatasetWithSettings> readMetadata() {
    // first try COL overrides, e.g. metadata.yaml
    Optional<DatasetWithSettings> ds = super.readMetadata();
    if (!ds.isPresent()) {
      // now look into the meta.xml for some other filename
      Optional<Path> mf = ((DwcaReader)reader).getMetadataFile();
      if (mf.isPresent()) {
        Path metadataPath = mf.get();
        if (Files.exists(metadataPath)) {
          try {
            String ext = FilenameUtils.getExtension(metadataPath.getFileName().toString());
            if (ext.equalsIgnoreCase("yaml") || ext.equalsIgnoreCase("yml")) {
              LOG.info("Read dataset metadata from YAML file {}", metadataPath);
              ds = ColdpMetadataParser.readYAML(Files.newInputStream(metadataPath));

            } else if (ext.equalsIgnoreCase("json")) {
              LOG.info("Read dataset metadata from JSON file {}", metadataPath);
              ds = ColdpMetadataParser.readJSON(Files.newInputStream(metadataPath));

            } else {
              ds = EmlParser.parse(metadataPath);
            }

          } catch (IOException | RuntimeException e) {
            LOG.error("Unable to read dataset metadata from dwc archive: {}", e.getMessage(), e);
          }
        } else {
          LOG.warn("Declared dataset metadata file {} does not exist.", metadataPath);
        }
      } else {
        LOG.info("No dataset metadata available");
      }
    }
    return ds;
  }
  
}
