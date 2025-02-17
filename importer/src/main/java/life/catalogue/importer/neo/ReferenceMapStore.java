package life.catalogue.importer.neo;

import life.catalogue.api.model.DOI;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.VerbatimEntity;
import life.catalogue.api.vocab.Issue;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.text.StringUtils;
import life.catalogue.dao.ReferenceStore;

import java.util.Map;
import java.util.function.BiConsumer;

import org.mapdb.DB;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;

public class ReferenceMapStore extends MapStore<Reference> implements ReferenceStore {
    private static final Logger LOG = LoggerFactory.getLogger(ReferenceMapStore.class);

    // Citation -> refID
    private final Map<String, String> refIndexCitation;

    public ReferenceMapStore(DB db, Pool<Kryo> pool, BiConsumer<VerbatimEntity, Issue> addIssue) {
        super(Reference.class, "r", db, pool, addIssue);
        refIndexCitation = db.hashMap("refIndexCitation")
                .keySerializer(Serializer.STRING)
                .valueSerializer(Serializer.STRING)
                .createOrOpen();
    }

    /**
     * Persists a Reference instance, creating a missing id de novo
     */
    @Override
    public boolean create(Reference r) {
        // build default citation from csl
        if (r.getCitation() == null && r.getCsl() != null) {
            r.setCitation(CslUtil.buildCitation(r.getCsl()));
        }
        // normalize DOI based identifiers
        if (r.getId() != null) {
          r.setId(normaliseIdentifier(r.getId()));
        }
        if (!super.create(r)) {
            return false;
        }
        // update lookup index for title
        updateCitationIndex(r);
        return true;
    }

    public static String normaliseIdentifier(String identifier) {
      if (identifier != null) {
        return DOI.parse(identifier).map(DOI::getDoiName).orElse(identifier);
      }
      return null;
    }

    private String removeFromCitationIndex(Reference r){
        if (r.getCitation() != null) {
            String normedCit = StringUtils.digitOrAsciiLetters(r.getCitation());
            if (normedCit != null) {
                return refIndexCitation.remove(normedCit);
            }
        }
        return null;
    }

    private void updateCitationIndex(Reference r){
        if (r.getCitation() != null) {
            String normedCit = StringUtils.digitOrAsciiLetters(r.getCitation());
            if (normedCit != null) {
                refIndexCitation.put(normedCit, r.getId());
            }
        }
    }

    @Override
    public void update(Reference obj) {
        if (contains(obj.getId())) {
            String citationOld = get(obj.getId()).getCitation();
            if (citationOld != null) {
                refIndexCitation.remove(citationOld);
            }
        }
        super.update(obj);
        updateCitationIndex(obj);
    }

    @Override
    public Reference delete(String key) {
        Reference r = super.delete(key);
        if (r != null) {
            removeFromCitationIndex(r);
        }
        return r;
    }

    public Reference refByCitation(String citation) {
        String normedCit = StringUtils.digitOrAsciiLetters(citation);
        if (normedCit != null && refIndexCitation.containsKey(normedCit)) {
            return objects.get(refIndexCitation.get(normedCit));
        }
        return null;
    }
}
