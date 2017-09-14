package org.col.db.mapper;

import org.apache.ibatis.annotations.*;
import org.col.api.Name;

/**
 *
 */
public interface NameMapper {
    Name get(@Param("key") int key);

    void insert(Name name);

    void update(Name name);

    void delete(@Param("key") int key);
}

