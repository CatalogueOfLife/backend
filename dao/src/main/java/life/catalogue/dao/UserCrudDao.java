package life.catalogue.dao;

import life.catalogue.api.model.Page;
import life.catalogue.api.model.ResultPage;
import life.catalogue.api.model.User;
import life.catalogue.db.mapper.UserMapper;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;

import jakarta.validation.Validator;

import javax.annotation.Nullable;

import java.util.List;

public class UserCrudDao extends EntityDao<Integer, User, UserMapper> {

  public UserCrudDao(SqlSessionFactory factory, Validator validator) {
    super(true, factory, User.class, UserMapper.class, validator);
  }

  public ResultPage<User> search(@Nullable final String q, @Nullable final User.Role role, @Nullable Page page) {
    page = page == null ? new Page() : page;
    try (SqlSession session = factory.openSession()){
      UserMapper um = session.getMapper(mapperClass);
      List<User> result = um.search(q, role, defaultPage(page));
      return new ResultPage<>(page, result, () -> um.searchCount(q, role));
    }
  }

  private static Page defaultPage(Page page){
    return page == null ? new Page(0, 10) : page;
  }

}
