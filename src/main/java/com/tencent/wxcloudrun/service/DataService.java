package com.tencent.wxcloudrun.service;

import com.tencent.wxcloudrun.dao.UserDataMapper;
import com.tencent.wxcloudrun.model.UserData;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

/**
 * 游戏数据服务 —— 处理存档读写
 * 
 * 数据格式兼容 feijiu2 前端的 GameStorage key-value 体系。
 * 前端 key 格式： "JSF_<name>"，存储时使用 GameStorage.Key(name) 转换。
 */
@Service
public class DataService {

    @Autowired
    private UserDataMapper userDataMapper;

    /**
     * 获取某个分区所有数据（登录时拉取全量）
     * 返回 Map<key, value>，value 为原始 JSON 字符串
     */
    public Map<String, String> getRegionData(String uid, String regionId) {
        List<UserData> list = userDataMapper.findByUidAndRegion(uid, regionId);
        Map<String, String> result = new LinkedHashMap<>();
        for (UserData d : list) {
            result.put(d.getDataKey(), d.getDataVal());
        }
        return result;
    }

    /**
     * 获取某个分区的全量 JSON（前端 GetRegionData 需要的格式）
     * 返回 { "key1": "val1", "key2": "val2", ... } 的 JSON 字符串
     * 注意：值必须按 JSON 字符串编码（转义引号）——前端 GameStorage.setAll
     * 期望拿到的是字符串值；若原样嵌入对象，前端会存成 "[object Object]" 导致还原失败。
     */
    public String getRegionDataJson(String uid, String regionId) {
        Map<String, String> data = getRegionData(uid, regionId);
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : data.entrySet()) {
            if (!first) sb.append(",");
            sb.append("\"").append(escapeJson(e.getKey())).append("\":");
            // 值按 JSON 字符串编码（兼容前端 GameStorage.setAll 期望字符串值）
            sb.append("\"").append(escapeJson(e.getValue() != null ? e.getValue() : "")).append("\"");
            first = false;
        }
        sb.append("}");
        return sb.toString();
    }

    /**
     * 按 key 获取单条数据
     */
    public String getByKey(String uid, String regionId, String dataKey) {
        UserData d = userDataMapper.findByKey(uid, regionId, dataKey);
        return d != null ? d.getDataVal() : null;
    }

    /**
     * 保存单条数据（单 key）
     */
    public void saveRecord(String uid, String regionId, String dataKey, String dataVal, String dml) {
        if ("del".equals(dml)) {
            userDataMapper.deleteByKey(uid, regionId, dataKey);
        } else {
            UserData d = new UserData();
            d.setUid(uid);
            d.setRegionId(regionId);
            d.setDataKey(dataKey);
            d.setDataVal(dataVal);
            userDataMapper.upsert(d);
        }
    }

    /**
     * 批量保存（前端 SaveUserRecordMulti）
     * multiData 是 JSON: { "key1": "val1", "key2": "val2", ... }
     * ops: "upd" 更新, "del" 删除
     */
    @SuppressWarnings("unchecked")
    @Transactional
    public void saveMultiRecord(String uid, String regionId, String multiDataJson, String ops) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> multiData = mapper.readValue(multiDataJson, Map.class);

            if ("del".equals(ops)) {
                List<String> keys = new ArrayList<>(multiData.keySet());
                if (!keys.isEmpty()) {
                    userDataMapper.batchDelete(uid, regionId, keys);
                }
            } else {
                List<UserData> list = new ArrayList<>();
                for (Map.Entry<String, Object> e : multiData.entrySet()) {
                    UserData d = new UserData();
                    d.setUid(uid);
                    d.setRegionId(regionId);
                    d.setDataKey(e.getKey());
                    d.setDataVal(e.getValue() != null ? e.getValue().toString() : "");
                    list.add(d);
                }
                if (!list.isEmpty()) {
                    userDataMapper.batchUpsert(list);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("批量存档解析失败", e);
        }
    }

    /**
     * 全量存档（前端 SaveUserRecordAll）
     * allData 是完整 JSON 对象字符串: { "key1": val1, "key2": val2, ... }
     */
    @SuppressWarnings("unchecked")
    @Transactional
    public void saveAll(String uid, String regionId, String allDataJson) {
        try {
            com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
            Map<String, Object> allData = mapper.readValue(allDataJson, Map.class);

            if (allData.isEmpty()) {
                // 空存档：前端 UploadEmptyRecord
                return;
            }

            List<UserData> list = new ArrayList<>();
            for (Map.Entry<String, Object> e : allData.entrySet()) {
                UserData d = new UserData();
                d.setUid(uid);
                d.setRegionId(regionId);
                d.setDataKey(e.getKey());
                d.setDataVal(e.getValue() != null ? e.getValue().toString() : "");
                list.add(d);
            }
            if (!list.isEmpty()) {
                userDataMapper.batchUpsert(list);
            }
        } catch (Exception e) {
            throw new RuntimeException("全量存档解析失败", e);
        }
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
