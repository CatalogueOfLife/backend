package life.catalogue.dao;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ibm.icu.text.Transliterator;
import life.catalogue.db.mapper.*;
import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;
import life.catalogue.api.model.*;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Origin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CatCopy {
  private static final Logger LOG = LoggerFactory.getLogger(CatCopy.class);
  
  private static final Transliterator transLatin = Transliterator.getInstance("Any-Latin; de-ascii; Latin-ASCII");
  
  private static final Map<EntityType, Class<? extends TaxonExtensionMapper<? extends DatasetScopedEntity<Integer>>>> extMapper = new HashMap<>();
  static {
    extMapper.put(EntityType.DISTRIBUTION, DistributionMapper.class);
    extMapper.put(EntityType.VERNACULAR, VernacularNameMapper.class);
    extMapper.put(EntityType.DESCRIPTION, DescriptionMapper.class);
    extMapper.put(EntityType.MEDIA, MediaMapper.class);
  }
  
  
  /**
   * Copies the given source taxon into the dataset and under the parent of targetParent.
   * The taxon and name source instance will be modified to represent the newly generated taxon and finally persisted.
   * The original id is retained and finally returned.
   * An optional set of associated entity types can be indicated to be copied too.
   *
   * The sectorKey found on the main taxon will also be applied to associated name, reference and other copied entities.
   *
   * @return the original source taxon id
   */
  public static <T extends NameUsageBase> DSID<String> copyUsage(final SqlSession session, final T t, final DSID<String> targetParent, int user,
                                                              Set<EntityType> include,
                                                              Function<Reference, String> lookupReference,
                                                              Function<String, String> lookupByIdReference) {
    final DSID<String> orig = new DSIDValue<>(t);
    copyName(session, t, targetParent.getDatasetKey(), user, lookupReference);
    
    setKeys(t, targetParent.getDatasetKey());
    t.applyUser(user, true);
    t.setOrigin(Origin.SOURCE);
    t.setParentId(targetParent.getId());
    
    // update reference links
    t.setReferenceIds(
        t.getReferenceIds().stream()
            .map(lookupByIdReference)
            .collect(Collectors.toList())
    );
    
    if (t instanceof Taxon) {
      session.getMapper(TaxonMapper.class).create( (Taxon) t);
    } else {
      session.getMapper(SynonymMapper.class).create( (Synonym) t);
    }
    
    // copy related entities
    for (EntityType type : include) {
      if (t.isTaxon() && extMapper.containsKey(type)) {
        final TaxonExtensionMapper<DatasetScopedEntity<Integer>> mapper = (TaxonExtensionMapper<DatasetScopedEntity<Integer>>) session.getMapper(extMapper.get(type));
        mapper.listByTaxon(orig).forEach(e -> {
          e.setId(null);
          e.setDatasetKey(targetParent.getDatasetKey());
          e.applyUser(user);
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
        
      } else if (EntityType.NAME_RELATION == type) {
        // TODO copy name rels
      }
    }
    return orig;
  }
  
  /**
   * Copies the given nam instance, modifying the original and assigning a new id
   */
  static void copyName(final SqlSession session, final NameUsageBase u, final int targetDatasetKey, int user,
                       Function<Reference, String> lookupReference) {
    Name n = u.getName();
    n.applyUser(user, true);
    n.setOrigin(Origin.SOURCE);
    if (n.getPublishedInId() != null) {
      ReferenceMapper rm = session.getMapper(ReferenceMapper.class);
      Reference ref = rm.get(new DSIDValue(n.getDatasetKey(), n.getPublishedInId()));
      n.setPublishedInId(lookupReference.apply(ref));
    }
    setKeys(n, targetDatasetKey, u.getSectorKey());
    session.getMapper(NameMapper.class).create(n);
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
