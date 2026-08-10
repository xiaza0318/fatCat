package com.tencent.wxcloudrun.dao;

import com.tencent.wxcloudrun.model.User;
import org.apache.ibatis.annotations.Mapper;

/**
 * 用户表 Mapper
 */
@Mapper
public interface UserMapper {

    User findByAccount(String account);

    User findByUid(String uid);

    User findByToken(String token);

    int insert(User user);

    int updateToken(User user);

    int updateNickName(User user);
}
