package life.catalogue.dao;

import com.ibm.icu.text.Transliterator;
import life.catalogue.api.datapackage.ColdpTerm;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Origin;
import life.catalogue.db.mapper.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import org.gbif.dwc.terms.AcefTerm;
import org.gbif.dwc.terms.DwcTerm;
import org.gbif.dwc.terms.DwcaTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

public class CatCopy {
  
  // Public so that the ES QMatcher class can us it and be guranteed it transliterates the Q exactly alike.
  public static final Transliterator transLatin = Transliterator.getInstance("Any-Latin; de-ascii; Latin-ASCII");
  
  private static final Logger LOG = LoggerFactory.getLogger(CatCopy.class);
  
  
  private static final Map<EntityType, Class<? extends TaxonExtensionMapper<? extends SectorScopedEntity<Integer>>>> extMapper = new HashMap<>();
  static {
    extMapper.put(EntityType.DISTRIBUTION, DistributionMapper.class);
    extMapper.put(EntityType.VERNACULAR, VernacularNameMapper.class);
    extMapper.put(EntityType.MEDIA, MediaMapper.class);
  }

  public static <T extends NameUsageBase> DSID<String> copyUsage(final SqlSession session, final T t, final DSID<String> targetParent, int user,
                                                                 Set<EntityType> include,
                                                                 Function<Reference, String> lookupReference,
                                                                 Function<String, String> lookupByIdReference) {
    return copyUsage(session, session, t, targetParent, user, false, include, lookupReference, lookupByIdReference);
  }

  /**
   * Copies the given source taxon into the dataset and under the parent of targetParent.
   * The taxon and name source instance will be modified to represent the newly generated taxon and finally persisted.
   * The original id is retained and finally returned.
   * An optional set of associated entity types can be indicated to be copied too.
   *
   * The sectorKey found on the main taxon will also be applied to associated name, reference and other copied entities.
   *
   * @param createVerbatim if true also creates a verbatim record for the name with the verbatim name & authorship as values
   * @return the original source taxon id
   */
  public static <T extends NameUsageBase> DSID<String> copyUsage(final SqlSession batchSession, final SqlSession session, final T t, final DSID<String> targetParent, int user,
                                                              boolean createVerbatim,
                                                              Set<EntityType> include,
                                                              Function<Reference, String> lookupReference,
                                                              Function<String, String> lookupByIdReference) {
    final DSID<String> orig = new DSIDValue<>(t);
    copyName(batchSession, session, createVerbatim, t, targetParent.getDatasetKey(), user, lookupReference);
    
    setKeys(t, targetParent.getDatasetKey());
    t.applyUser(user, true);
    t.setOrigin(Origin.SOURCE);
    t.setParentId(targetParent.getId());
    
    // update reference links
    t.setAccordingToId(lookupByIdReference.apply(t.getAccordingToId()));
    t.setReferenceIds(
        t.getReferenceIds().stream()
            .map(lookupByIdReference)
            .collect(Collectors.toList())
    );

    if (t instanceof Taxon) {
      batchSession.getMapper(TaxonMapper.class).create( (Taxon) t);
    } else {
      batchSession.getMapper(SynonymMapper.class).create( (Synonym) t);
    }
    
    // copy related entities
    for (EntityType type : include) {
      if (t.isTaxon() && extMapper.containsKey(type)) {
        final TaxonExtensionMapper<SectorScopedEntity<Integer>> mapper = (TaxonExtensionMapper<SectorScopedEntity<Integer>>) batchSession.getMapper(extMapper.get(type));
        mapper.listByTaxon(orig).forEach(e -> {
          e.setId(null);
          e.setSectorKey(t.getSectorKey());
          e.setDatasetKey(targetParent.getDatasetKey());
          e.applyUser(user);
          if (e instanceof VerbatimEntity) {
            // nullify verbatim keys until we create new verbatim records to keep issues
            ((VerbatimEntity) e).setVerbatimKey(null);
          }
          // check if the entity refers to a reference which we need to lookup / copy
          if (Referenced.class.isAssignableFrom(e.getClass())) {
            Referenced eRef = (Referenced) e;
            String ridCopy = lookupByIdReference.apply(eRef.getReferenceId());
            eRef.setReferenceId(ridCopy);
          }
          if (EntityType.VERNACULAR == type) {
            updateVernacularName((VernacularName)e, IssueContainer.VOID);
          }
          mapper.create(e, t.getId());
        });

      } else if (EntityType.TREATMENT == type) {
        // TODO copy
      } else if (EntityType.TYPE_MATERIAL == type) {
        // TODO copy
      }
    }
    return orig;
  }
  
  /**
   * Copies the given nam instance, modifying the original and assigning a new id
   */
  static void copyName(final SqlSession batchSession, final SqlSession session, boolean createVerbatim, final NameUsageBase u, final int targetDatasetKey, int user,
                       Function<Reference, String> lookupReference) {
    Name n = u.getName();
    n.applyUser(user, true);
    n.setOrigin(Origin.SOURCE);
    if (n.getPublishedInId() != null) {
      ReferenceMapper rm = batchSession.getMapper(ReferenceMapper.class);
      Reference ref = rm.get(new DSIDValue(n.getDatasetKey(), n.getPublishedInId()));
      n.setPublishedInId(lookupReference.apply(ref));
    }
    Integer vKey = null;
    if (createVerbatim && n.getVerbatimKey() != null) {
      VerbatimRecordMapper vm = session.getMapper(VerbatimRecordMapper.class);
      VerbatimRecord vSrc = vm.get(DSID.vkey(n));
      VerbatimRecord v = new VerbatimRecord(vSrc);
      v.setDatasetKey(targetDatasetKey);
      v.setType(ColdpTerm.Name);
      v.put(DwcTerm.datasetID, u.getDatasetKey().toString());
      v.put(ColdpTerm.ID, vSrc.getFirstRaw(ColdpTerm.ID, AcefTerm.ID, AcefTerm.AcceptedTaxonID, DwcTerm.taxonID, DwcaTerm.ID));
      v.put(ColdpTerm.scientificName, vSrc.getFirstRaw(ColdpTerm.scientificName, DwcTerm.scientificName));
      v.put(ColdpTerm.authorship, vSrc.getFirstRaw(ColdpTerm.authorship, AcefTerm.InfraSpeciesAuthorString, AcefTerm.AuthorString, DwcTerm.scientificNameAuthorship));
      v.put(ColdpTerm.rank, vSrc.getFirstRaw(ColdpTerm.rank, AcefTerm.InfraSpeciesMarker, DwcTerm.taxonRank, DwcTerm.verbatimTaxonRank));
      vm.create(v);
      vKey=v.getId();
    }
    setKeys(n, targetDatasetKey, u.getSectorKey());
    n.setVerbatimKey(vKey);
    batchSession.getMapper(NameMapper.class).create(n);
  }
  
  private static NameUsageBase setKeys(NameUsageBase t, int datasetKey) {
    t.setDatasetKey(datasetKey);
    return newKey(t);
  }
  
  private static Name setKeys(Name n, int datasetKey, int sectorKey) {
    n.setDatasetKey(datasetKey);
    n.setSectorKey(sectorKey);
    newKey(n);
    //TODO: should we update homotypic name based on the original ids if they are also in the sector???
    n.setHomotypicNameId(n.getId());
    return n;
  }
  
  private static Reference setKeys(Reference r, int datasetKey, int sectorKey) {
    r.setDatasetKey(datasetKey);
    r.setSectorKey(sectorKey);
    return newKey(r);
  }
  
  private static <T extends VerbatimEntity & DSID> T newKey(T e) {
    e.setVerbatimKey(null);
    e.setId(UUID.randomUUID().toString());
    return e;
  }
  
  private static void updateVernacularName(VernacularName vn, IssueContainer rec) {
    if (StringUtils.isBlank(vn.getLatin())) {
      vn.setLatin(latinName(vn.getName()));
      rec.addIssue(Issue.VERNACULAR_NAME_TRANSLITERATED);
    }
  }
  
  static String latinName(String name) {
    return transLatin.transform(name);
  }
  
}
