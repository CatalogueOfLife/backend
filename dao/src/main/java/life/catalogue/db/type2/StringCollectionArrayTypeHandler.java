/*
 * Copyright 2013 Global Biodiversity Information Facility (GBIF)
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copyTaxon of the License at
 * http://www.apache.org/licenses/LICENSE-2.0
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package life.catalogue.db.type2;

import java.sql.*;
import java.util.Collection;
import java.util.Collections;

import com.google.common.collect.Lists;
import org.apache.ibatis.type.BaseTypeHandler;
import org.apache.ibatis.type.JdbcType;

/**
 * String array handler that avoids nulls and uses empty arrays instead.
 */
public class StringCollectionArrayTypeHandler extends AbstractArrayTypeHandler<Collection<String>> {

  public StringCollectionArrayTypeHandler() {
    super("text", Collections.emptyList());
  }


  @Override
  public Object[] toArray(Collection<String> obj) throws SQLException {
    return obj.toArray();
  }

  @Override
  public Collection<String> toObj(Array pgArray) throws SQLException {
    if (pgArray == null) return Lists.newArrayList();

    String[] strings = (String[]) pgArray.getArray();
    return Lists.newArrayList(strings);
  }
}
