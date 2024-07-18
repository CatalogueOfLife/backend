package life.catalogue.db.mapper;

import life.catalogue.api.model.ParserConfig;
import life.catalogue.api.search.QuerySearchRequest;
import life.catalogue.db.Searchable;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import java.util.List;

public interface ParserConfigMapper extends Searchable<ParserConfig, QuerySearchRequest> {

  ParserConfig get(@Param("id") String id);

  void create(ParserConfig obj);

  int delete(@Param("id") String id);

  Cursor<ParserConfig> process();
  List<ParserConfig> list();

}
