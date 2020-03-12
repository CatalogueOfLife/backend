package life.catalogue.db.mapper;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.ParserConfig;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.cursor.Cursor;

import java.util.List;

public interface ParserConfigMapper {

  ParserConfig get(@Param("id") String id);

  void create(ParserConfig obj);

  int delete(@Param("id") String id);

  List<ParserConfig> search(@Param("q") String query, @Param("page") Page page);

  int count (@Param("q") String query);

  Cursor<ParserConfig> process();

}
