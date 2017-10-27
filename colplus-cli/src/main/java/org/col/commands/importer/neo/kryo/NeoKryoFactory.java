package org.col.commands.importer.neo.kryo;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.pool.KryoFactory;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.TextNode;
import it.unimi.dsi.fastutil.ints.IntArrayList;
import org.col.api.*;
import org.col.api.vocab.*;
import org.col.commands.importer.neo.model.NeoTaxon;
import org.col.commands.importer.neo.model.RankedName;
import org.gbif.dwc.terms.*;
import org.neo4j.kernel.impl.core.NodeProxy;

import java.net.URI;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;


/**
 * Creates a kryo factory usable for thread safe kryo pools that can deal with clb api classes.
 * We use Kryo for extremely fast byte serialization of temporary objects.
 * It is used to serialize various information in kvp stores during checklist indexing and nub builds.
 */
public class NeoKryoFactory implements KryoFactory {

  @Override
  public Kryo create() {
    Kryo kryo = new Kryo();
    kryo.setRegistrationRequired(true);

    // col core
    kryo.register(Authorship.class);
    kryo.register(Classification.class);
    kryo.register(Dataset.class);
    kryo.register(DatasetMetrics.class);
    kryo.register(Name.class);
    kryo.register(NameAct.class);
    kryo.register(Reference.class);
    kryo.register(Serial.class);
    kryo.register(Taxon.class);
    kryo.register(VerbatimRecord.class);
    kryo.register(VerbatimRecordTerms.class);
    kryo.register(TermRecord.class);
    kryo.register(Page.class);
    // jackson json node (e.g. csl property)
    kryo.register(ObjectNode.class);
    kryo.register(TextNode.class);
    kryo.register(JsonNodeFactory.class);

    // normalizer specific models
    kryo.register(NeoTaxon.class);
    kryo.register(NeoTaxon.Synonym.class);
    kryo.register(RankedName.class);

    // fastutil
    kryo.register(IntArrayList.class);

    // java & commons
    kryo.register(LocalDateTime.class);
    kryo.register(LocalDate.class);
    kryo.register(HashMap.class);
    kryo.register(LinkedHashMap.class);
    kryo.register(HashSet.class);
    kryo.register(ArrayList.class);
    kryo.register(LinkedList.class);
    kryo.register(UUID.class, new UUIDSerializer());
    kryo.register(URI.class, new URISerializer());
    kryo.register(int[].class);
    ImmutableListSerializer.registerSerializers(kryo);

    // enums
    kryo.register(Country.class);
    kryo.register(DataFormat.class);
    kryo.register(EnumMap.class, new EnumMapSerializer());
    kryo.register(EnumSet.class, new EnumSetSerializer());
    kryo.register(Issue.class);
    kryo.register(Kingdom.class);
    kryo.register(Language.class);
    kryo.register(Lifezone.class);
    kryo.register(NamePart.class);
    kryo.register(NameType.class);
    kryo.register(NomActType.class);
    kryo.register(NomCode.class);
    kryo.register(NomStatus.class);
    kryo.register(Origin.class);
    kryo.register(Rank.class);
    kryo.register(TaxonomicStatus.class);
    kryo.register(TypeStatus.class);

    // term enums
    kryo.register(AcTerm.class);
    kryo.register(DcElement.class);
    kryo.register(DcTerm.class);
    kryo.register(DwcTerm.class);
    kryo.register(EolReferenceTerm.class);
    kryo.register(GbifInternalTerm.class);
    kryo.register(GbifTerm.class);
    kryo.register(IucnTerm.class);
    kryo.register(XmpRightsTerm.class);
    kryo.register(XmpTerm.class);
    kryo.register(UnknownTerm.class, new TermSerializer());

    // ignore normalizer node proxies and set them to null upon read:
    kryo.register(NodeProxy.class, new NullSerializer());

    return kryo;
  }
}
