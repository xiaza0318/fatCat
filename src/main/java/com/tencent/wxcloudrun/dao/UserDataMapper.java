package com.tencent.wxcloudrun.dao;

import com.tencent.wxcloudrun.model.UserData;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 用户游戏数据 Mapper（key-value 存档）
 */
@Mapper
public interface UserDataMapper {

    /** 按 uid + regionId 获取该分区所有数据 */
    List<UserData> findByUidAndRegion(String uid, String regionId);

    /** 按 key 获取单条数据 */
    UserData findByKey(String uid, String regionId, String dataKey);

    /** 插入或更新单条数据 */
    int upsert(UserData userData);

    /** 批量插入或更新 */
    int batchUpsert(List<UserData> list);

    /** 删除单条数据 */
    int deleteByKey(String uid, String regionId, String dataKey);

    /** 批量删除 */
    int batchDelete(String uid, String regionId, List<String> keys);

    /** 清空分区所有数据 */
    int clearByUidAndRegion(String uid, String regionId);
}
