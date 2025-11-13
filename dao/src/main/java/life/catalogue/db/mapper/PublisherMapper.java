package life.catalogue.db.mapper;

import life.catalogue.api.model.*;
import life.catalogue.api.search.DatasetSearchRequest;
import life.catalogue.api.vocab.DatasetOrigin;
import life.catalogue.api.vocab.TaxGroup;
import life.catalogue.db.CRUD;

import java.time.LocalDateTime;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import javax.annotation.Nullable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import it.unimi.dsi.fastutil.ints.IntSet;

/**
 * The dataset mappers create method expects the key to be provided.
 * Unless you know exactly what you are doing please use the DatasetDAO to create, modify or delete datasets.
 */
public interface PublisherMapper extends CRUD<UUID, Publisher> {

  List<Publisher> search(@Param("q") String q, @Param("page") Page page);

}
