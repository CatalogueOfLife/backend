package life.catalogue.importer.neo;

import com.esotericsoftware.kryo.pool.KryoPool;
import life.catalogue.api.model.Reference;
import life.catalogue.api.model.VerbatimEntity;
import life.catalogue.api.vocab.Issue;
import life.catalogue.common.csl.CslUtil;
import life.catalogue.common.text.StringUtils;
import org.mapdb.DB;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.function.BiConsumer;

public class ReferenceStore extends MapStore<Reference> {
    private static final Logger LOG = LoggerFactory.getLogger(ReferenceStore.class);

    // Citation -> refID
    private final Map<String, String> refIndexCitation;

    public ReferenceStore(DB db, KryoPool pool, BiConsumer<VerbatimEntity, Issue> addIssue) {
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
        if (!super.create(r)) {
            return false;
        }
        // update lookup index for title
        String normedCit = StringUtils.digitOrAsciiLetters(r.getCitation());
        if (normedCit != null) {
            refIndexCitation.put(normedCit, r.getId());
        }
        return true;
    }

    public Reference refByCitation(String citation) {
        String normedCit = StringUtils.digitOrAsciiLetters(citation);
        if (normedCit != null && refIndexCitation.containsKey(normedCit)) {
            return objects.get(refIndexCitation.get(normedCit));
        }
        return null;
    }
}
