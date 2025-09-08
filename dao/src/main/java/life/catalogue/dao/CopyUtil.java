package life.catalogue.dao;

import life.catalogue.api.model.*;
import life.catalogue.api.vocab.EntityType;
import life.catalogue.api.vocab.Issue;
import life.catalogue.api.vocab.Origin;
import life.catalogue.common.id.ShortUUID;
import life.catalogue.db.mapper.*;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import javax.annotation.Nullable;

import org.apache.commons.lang3.StringUtils;
import org.apache.ibatis.session.SqlSession;

import com.ibm.icu.text.Transliterator;

public class CopyUtil {
  
  // Public so that the ES QMatcher class can us it and be guranteed it transliterates the Q exactly alike.
  public static final Transliterator transLatin = Transliterator.getInstance("Any-Latin; de-ascii; Latin-ASCII");
  public static final Supplier<String> ID_GENERATOR = () -> ShortUUID.random().toString();
  private static final Map<EntityType, Class<? extends TaxonExtensionMapper<? extends SectorScopedEntity<Integer>>>> extMapper = new HashMap<>();
  static {
    extMapper.put(EntityType.TAXON_PROPERTY, TaxonPropertyMapper.class);
    extMapper.put(EntityType.DISTRIBUTION, DistributionMapper.class);
    extMapper.put(EntityType.VERNACULAR, VernacularNameMapper.class);
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
  public static <T extends NameUsageBase> DSID<String> copyUsage(final SqlSession batchSession, final T t,
                                                                 @Nullable final DSID<String> targetParent,
                                                                 int user,
                                                                 Set<EntityType> include,
                                                                 Supplier<String> nameIdSupplier,
                                                                 Supplier<String> typeMaterialIdSupplier,
                                                                 Function<SimpleNameWithNidx, String> usageIdSupplier,
                                                                 Function<Integer, Integer> nidx2canonical,
                                                                 Function<Reference, String> lookupReference,
                                                                 Function<String, String> lookupByIdReference) {
    final DSID<String> origT = new DSIDValue<>(t);
    final DSID<String> origN = new DSIDValue<>(t.getName());
    copyName(batchSession, t, targetParent.getDatasetKey(), user, lookupReference, nameIdSupplier);
    
    setKeys(t, targetParent.getDatasetKey(), usageIdSupplier, nidx2canonical);
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
        final TaxonExtensionMapper<ExtensionEntity> mapper = (TaxonExtensionMapper<ExtensionEntity>) batchSession.getMapper(extMapper.get(type));
        mapper.listByTaxon(origT).forEach(e -> {
          e.setId(null);
          e.setDatasetKey(targetParent.getDatasetKey());
          e.setSectorKey(t.getSectorKey());
          e.setVerbatimSourceKey(t.getVerbatimSourceKey()); // keep the same verbatim source record as the main usage
          // nullify verbatim keys until we create new verbatim records to keep issues
          e.setVerbatimKey(null);
          e.applyUser(user);
          // check if the entity refers to a reference which we need to lookup / copy
          String ridCopy = lookupByIdReference.apply(e.getReferenceId());
          e.setReferenceId(ridCopy);
          if (EntityType.VERNACULAR == type) {
            transliterateVernacularName((VernacularName)e, IssueContainer.VOID);
          }
          mapper.create(e, t.getId());
        });

      } else if (EntityType.TREATMENT == type) {
        // TODO copy treatments
      } else if (EntityType.TYPE_MATERIAL == type) {
        final var mapper = batchSession.getMapper(TypeMaterialMapper.class);
        mapper.listByName(origN).forEach(tm -> {
          newKey(tm, typeMaterialIdSupplier); // id=UUID & verbatimKey=null
          tm.setNameId(t.getName().getId());
          tm.setDatasetKey(targetParent.getDatasetKey());
          tm.setSectorKey(t.getSectorKey());
          tm.setVerbatimSourceKey(t.getVerbatimSourceKey()); // keep the same verbatim source record as the main usage
          tm.applyUser(user);
          String ridCopy = lookupByIdReference.apply(tm.getReferenceId());
          tm.setReferenceId(ridCopy);
          mapper.create(tm);
        });
      }
    }
    return origT;
  }

  /**
   * Usage copy method that uses a UUID generator for all new ids.
   */
  public static <T extends NameUsageBase> DSID<String> copyUsage(final SqlSession batchSession, final T t,
                                                                 @Nullable final DSID<String> targetParent,
                                                                 int user,
                                                                 Set<EntityType> include,
                                                                 Function<Reference, String> lookupReference,
                                                                 Function<String, String> lookupByIdReference) {
    return copyUsage(batchSession, t, targetParent, user, include, ID_GENERATOR, ID_GENERATOR, n -> ID_GENERATOR.get(), i -> null, lookupReference, lookupByIdReference);
  }


    /**
     * Copies the given nam instance, modifying the original and assigning a new id
     */
  static void copyName(final SqlSession batchSession, final NameUsageBase u, final int targetDatasetKey, int user,
                       Function<Reference, String> lookupReference, Supplier<String> idSupplier) {
    Name n = u.getName();
    n.applyUser(user, true);
    n.setOrigin(Origin.SOURCE);
    if (n.getPublishedInId() != null) {
      ReferenceMapper rm = batchSession.getMapper(ReferenceMapper.class);
      Reference ref = rm.get(new DSIDValue<>(n.getDatasetKey(), n.getPublishedInId()));
      n.setPublishedInId(lookupReference.apply(ref));
    }
    setKeys(n, targetDatasetKey, u.getSectorKey(), idSupplier);
    batchSession.getMapper(NameMapper.class).create(n);
  }
  
  private static NameUsageBase setKeys(NameUsageBase t, int datasetKey, Function<SimpleNameWithNidx, String> idSupplier, Function<Integer, Integer> nidx2canonical) {
    t.setDatasetKey(datasetKey);
    t.setVerbatimKey(null);
    SimpleNameWithNidx sn = t.toSimpleNameWithNidx(nidx2canonical);
    t.setId(idSupplier.apply(sn));
    return t;
  }
  
  private static Name setKeys(Name n, int datasetKey, Integer sectorKey, Supplier<String> idSupplier) {
    n.setDatasetKey(datasetKey);
    n.setSectorKey(sectorKey);
    newKey(n, idSupplier);
    return n;
  }
  
  private static Reference setKeys(Reference r, int datasetKey, Integer sectorKey, Supplier<String> idSupplier) {
    r.setDatasetKey(datasetKey);
    r.setSectorKey(sectorKey);
    return newKey(r, idSupplier);
  }
  
  private static <T extends VerbatimEntity & DSID<String>> T newKey(T e, Supplier<String> idSupplier) {
    e.setVerbatimKey(null);
    e.setId(idSupplier.get());
    return e;
  }
  
  public static void transliterateVernacularName(VernacularName vn, IssueContainer issues) {
    if (StringUtils.isBlank(vn.getLatin())) {
      vn.setLatin(latinName(vn.getName()));
      issues.add(Issue.VERNACULAR_NAME_TRANSLITERATED);
    }
  }
  
  static String latinName(String name) {
    return transLatin.transform(name);
  }
  
}
