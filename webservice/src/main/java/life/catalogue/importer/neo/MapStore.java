package life.catalogue.importer.neo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import life.catalogue.api.model.DatasetScopedEntity;
import life.catalogue.api.model.VerbatimEntity;
import life.catalogue.api.vocab.Issue;
import life.catalogue.common.kryo.map.MapDbObjectSerializer;
import life.catalogue.importer.IdGenerator;
import org.jetbrains.annotations.NotNull;
import org.mapdb.DB;
import org.mapdb.HTreeMap;
import org.mapdb.Serializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;
import java.util.function.BiConsumer;

public class MapStore<T extends DatasetScopedEntity<String> & VerbatimEntity> implements Iterable<T>, AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(MapStore.class);
    private static final String TMP_PREFIX = "\u0007\u0126";
    protected final Class<T> entityClass;
    protected final IdGenerator idGen;
    // ID -> entity
    protected final Map<String, T> objects;
    // tmp ids to newly generated id so that external tools know about this change
    private final Map<String, String> tmp2ids = new HashMap<>();
    protected final BiConsumer<VerbatimEntity, Issue> addIssue;
    private final String prefPrefix;
    private final DB db;
    private final Pool<Kryo> pool;

    public MapStore(Class<T> entityClass, String prefPrefix, DB db, Pool<Kryo> pool, BiConsumer<VerbatimEntity, Issue> addIssue) {
        this.db = db;
        this.pool = pool;
        this.prefPrefix = prefPrefix;
        this.entityClass = entityClass;
        this.addIssue = addIssue;
        idGen = new IdGenerator(TMP_PREFIX);
        objects = newMap();
    }

    private HTreeMap<String, T> newMap(){
        return db.hashMap(entityClass.getSimpleName())
                .keySerializer(Serializer.STRING)
                .valueSerializer(new MapDbObjectSerializer(entityClass, pool, 128))
                .createOrOpen();
    }

    public Collection<T> values() {
        return objects.values();
    }

    public boolean create(T obj) {
        // create missing id
        if (obj.getId() == null) {
            obj.setId(idGen.next());
        }
        if (objects.containsKey(obj.getId())) {
            LOG.warn("Duplicate {} ID {}", entityClass.getSimpleName(), obj.getId());
            T prev = objects.get(obj.getId());
            addIssue.accept(prev, Issue.ID_NOT_UNIQUE);
            addIssue.accept(obj, Issue.ID_NOT_UNIQUE);
            return false;
        }
        objects.put(obj.getId(), obj);
        return true;
    }

    public void update(T obj) {
        objects.put(obj.getId(), obj);
    }

    public T delete(String key) {
        return objects.remove(key);
    }

    public T get(String key) {
        return objects.getOrDefault(key, null);
    }

    public boolean contains(String key) {
        return objects.containsKey(key);
    }

    public Set<String> keys() {
        return objects.keySet();
    }

    /**
     * Switches the tmp ids created during inserts into real ones
     * generated using the preferred prefix - making sure we never clash with any source ids.
     *
     * It keeps the old-new ids in memory for later resolution via tmpIdUpdate(String).
     * These are not persistent and will be gone when the store is closed !!!
     */
    public void updateTmpIds() {
        // generate a good prefix
        idGen.setPrefix(prefPrefix, objects.values().stream().map(DatasetScopedEntity::getId));

        int counter = 0;
        for (String key : objects.keySet()) {
            if (key.startsWith(TMP_PREFIX)) {
                T obj = delete(key);
                String id = idGen.next();
                tmp2ids.put(obj.getId(), id);
                obj.setId(id);
                create(obj);
                counter++;
            }
        }
        LOG.info("Updated {} tmp {} keys out of {} in total with new prefix >>{}<<", counter, entityClass.getSimpleName(), size(), idGen.getPrefix());
    }

    /**
     * @return the final reference id if it was a temporary one, the given id otherwise
     */
    public String tmpIdUpdate(String id) {
        return tmp2ids.getOrDefault(id, id);
    }

    @NotNull
    @Override
    public Iterator<T> iterator() {
        return objects.values().iterator();
    }

    public int size() {
        return objects.size();
    }

    @Override
    public void close() throws Exception {
        ((HTreeMap)objects).close();
    }
}
