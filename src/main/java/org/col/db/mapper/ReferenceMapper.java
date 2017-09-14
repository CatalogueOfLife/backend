package org.col.db.mapper;

import org.apache.ibatis.annotations.*;
import org.col.api.Reference;

/**
 *
 */
public interface ReferenceMapper {
    String COLS = "title, author, year, link, identifier";
    String PROPS = "#{title}, #{author}, #{year}, #{link}, #{identifier}";

    @Select("SELECT * FROM reference WHERE key = #{key}")
    Reference get(int key);

    @Insert("INSERT INTO reference ("+COLS+") VALUES ("+PROPS+")")
    @Options(useGeneratedKeys=true, keyProperty="key")
    void insert(Reference reference);

    @Update("UPDATE reference SET ("+COLS+") = ("+PROPS+") WHERE key = #{key}")
    void update(Reference reference);

    @Delete("DELETE FROM reference WHERE key = #{key}")
    void delete(int key);
}

