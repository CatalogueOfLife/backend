package life.catalogue.db.type;

import life.catalogue.db.type2.AbstractArrayTypeHandler;

import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.List;

import org.apache.ibatis.type.JdbcType;

import it.unimi.dsi.fastutil.objects.ObjectArrayList;

/**
 * String array handler for the sparsely used author lists of an authorship.
 * Unlike {@link StringArrayTypeHandler} it stores an empty list as NULL, which costs a bit in the null bitmap
 * where an empty array takes about 16 bytes on every row: https://github.com/CatalogueOfLife/backend/issues/1664
 *
 * NULL and empty arrays are both read as null, so MyBatis does not call the setter and the authorship keeps
 * the list it was constructed with, null since name-parser-api 6.
 */
public class AuthorListTypeHandler extends AbstractArrayTypeHandler<List<String>> {

  public AuthorListTypeHandler() {
    super("text", null);
  }

  @Override
  public void setParameter(PreparedStatement ps, int i, List<String> parameter, JdbcType jdbcType) throws SQLException {
    super.setParameter(ps, i, parameter == null || parameter.isEmpty() ? null : parameter, jdbcType);
  }

  @Override
  public Object[] toArray(List<String> obj) throws SQLException {
    return obj.toArray();
  }

  @Override
  public List<String> toObj(Array pgArray) throws SQLException {
    if (pgArray == null) return null;
    String[] authors = (String[]) pgArray.getArray();
    return authors.length == 0 ? null : ObjectArrayList.wrap(authors);
  }
}
