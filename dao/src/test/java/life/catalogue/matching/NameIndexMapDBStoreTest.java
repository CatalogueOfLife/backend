package life.catalogue.matching;

import life.catalogue.matching.nidx.NameIndexMapDBStore;
import life.catalogue.matching.nidx.NameIndexStore;

import java.io.File;
import java.io.IOException;

import org.mapdb.DBMaker;

public class NameIndexMapDBStoreTest extends NameIndexStoreTest {

  @Override
  NameIndexStore create() throws IOException {
    var dbf = new File(dir.file, "colNidxStore.dat");
    var maker = DBMaker.fileDB(dbf).fileMmapEnableIfSupported();
    return new NameIndexMapDBStore(maker, dbf, 1024);
  }

}