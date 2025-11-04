package life.catalogue.importer.neo;

import com.univocity.parsers.csv.CsvWriter;

import life.catalogue.importer.IdGenerator;
import life.catalogue.importer.neo.model.*;

import org.mapdb.DB;
import org.neo4j.graphdb.Node;
import org.neo4j.graphdb.Transaction;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.util.Pool;
import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.stream.Collectors;

public class NeoUsageStore extends NeoCRUDStore<NeoUsage> {

  private final CsvWriter csvTaxWriter;
  private final CsvWriter csvSynWriter;
  private final CsvWriter csvSynRelWriter;
  private final CsvWriter csvNameRelWriter;

  public NeoUsageStore(DB mapDb, String mapDbName, Pool<Kryo> pool, IdGenerator idGen, NeoDb neoDb) throws IOException {
    super(mapDb, mapDbName, NeoUsage.class, pool, neoDb, idGen);
    csvTaxWriter = neoDb.newCsvWriter(taxFileName(), NeoProperties.ID);
    csvSynWriter = neoDb.newCsvWriter(synFileName(), NeoProperties.ID);
    csvSynRelWriter = neoDb.newCsvWriter(synRelFileName(), NeoProperties.ID);
    csvNameRelWriter = neoDb.newCsvWriter(nameRelFileName(), NeoProperties.ID);
  }
  
  @Override
  public Node create(NeoUsage obj, Transaction tx) {
    Preconditions.checkNotNull(obj.nameNode, "Usage requires an existing name node");
    Node nu = super.create(obj, tx);
    if (nu != null) {
      nu.createRelationshipTo(obj.nameNode, RelType.HAS_NAME);
    }
    return nu;
  }

  public String taxFileName() {
    return csvFileName("-tax");
  }
  public String synFileName() {
    return csvFileName("-syn");
  }
  public String synRelFileName() {
    return csvFileName("-syn-rel");
  }
  public String nameRelFileName() {
    return csvFileName("-name-rel");
  }

  @Override
  void writeCsvNode(NeoUsage obj) {
    final PropLabel props = obj.propLabel();
    if (obj.isTaxon()) {
      csvTaxWriter.writeRow(props);
    } else  {
      csvSynWriter.writeRow(props);
    }
  }

  @Override
  protected void closeWriters() {
    super.closeWriters();
    csvTaxWriter.close();
    csvSynWriter.close();
    csvSynRelWriter.close();
    csvNameRelWriter.close();
  }

}
