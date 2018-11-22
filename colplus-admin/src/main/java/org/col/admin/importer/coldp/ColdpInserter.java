package org.col.admin.importer.coldp;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;

import com.google.common.base.Splitter;
import org.col.admin.importer.NeoInserter;
import org.col.admin.importer.NormalizationFailedException;
import org.col.admin.importer.neo.NeoDb;
import org.col.admin.importer.neo.NodeBatchProcessor;
import org.col.admin.importer.reference.ReferenceFactory;
import org.col.api.datapackage.ColTerm;
import org.col.api.model.Dataset;
import org.col.api.model.VerbatimRecord;
import org.col.api.vocab.DataFormat;
import org.gbif.dwc.terms.AcefTerm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 *
 */
public class ColdpInserter extends NeoInserter {

  private static final Logger LOG = LoggerFactory.getLogger(ColdpInserter.class);
  private static final Splitter COMMA_SPLITTER = Splitter.on(',').trimResults().omitEmptyStrings();

  private ColdpReader reader;
  private ColdpInterpreter inter;

  public ColdpInserter(NeoDb store, Path folder, ReferenceFactory refFactory) {
    super(folder, store, refFactory);
  }

  private void initReader() {
    if (reader == null) {
      try {
        reader = ColdpReader.from(folder);
      } catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  }

  /**
   * Inserts COL data from a source folder into the normalizer store. Before inserting it does a
   * quick check to see if all required files are existing.
   */
  @Override
  public void batchInsert() throws NormalizationFailedException {
    try {
      initReader();
      inter = new ColdpInterpreter(store.getDataset(), meta, refFactory, store);

      // This inserts the plain references from the Reference file with no links to names, taxa or distributions.
      // Links are added afterwards in other methods when a ACEF:ReferenceID field is processed by lookup to the neo store.
      insertEntities(reader, ColTerm.Reference,
          inter::interpretReference,
          store::create
      );

      // name & relations
      insertEntities(reader, ColTerm.Name,
          inter::interpretName,
          store.names()::create
      );
      insertNameRelations(reader, ColTerm.NameRel,
          inter::interpretNameRelations,
          ColTerm.nameID,
          ColTerm.relatedNameID
      );

      // taxa
      insertEntities(reader, ColTerm.Taxon,
          inter::interpretTaxon,
          store.usages()::create
      );
      
      // synonyms
      insertEntities(reader, ColTerm.Synonym,
          inter::interpretSynonym,
          store.usages()::create
      );
  
      // supplementary
      insertTaxonEntities(reader, ColTerm.Description,
          inter::interpretDescription,
          ColTerm.taxonID,
          (t, d) -> t.descriptions.add(d)
      );
      insertTaxonEntities(reader, ColTerm.Distribution,
          inter::interpretDistribution,
          ColTerm.taxonID,
          (t, d) -> t.distributions.add(d)
      );
      insertTaxonEntities(reader, ColTerm.Media,
          inter::interpretMedia,
          ColTerm.taxonID,
          (t, d) -> t.media.add(d)
      );
      insertTaxonEntities(reader, ColTerm.VernacularName,
          inter::interpretVernacular,
          ColTerm.taxonID,
          (t, d) -> t.vernacularNames.add(d)
      );
      
    } catch (RuntimeException e) {
      throw new NormalizationFailedException("Failed to read ColDP files", e);
    }
  }

  @Override
  protected NodeBatchProcessor relationProcessor() {
    return new ColdpRelationInserter(store, inter);
  }

  /**
   * Reads the dataset metadata and puts it into the store
   */
  @Override
  protected Optional<Dataset> readMetadata() {
    Dataset d = null;
    initReader();
    Optional<VerbatimRecord> metadata = reader.readFirstRow(AcefTerm.SourceDatabase);
    if (metadata.isPresent()) {
      VerbatimRecord dr = metadata.get();
      d = new Dataset();
      d.setTitle(dr.get(AcefTerm.DatabaseFullName));
      d.setVersion(dr.get(AcefTerm.DatabaseVersion));
      d.setDescription(dr.get(AcefTerm.Abstract));
      d.setAuthorsAndEditors(dr.get(AcefTerm.AuthorsEditors, COMMA_SPLITTER));
      d.setDescription(dr.get(AcefTerm.Abstract));
      d.setHomepage(dr.getURI(AcefTerm.HomeURL));
      d.setDataFormat(DataFormat.ACEF);
    }
    return Optional.ofNullable(d);
  }

}
