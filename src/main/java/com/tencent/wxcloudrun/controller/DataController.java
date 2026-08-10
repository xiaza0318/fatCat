package com.tencent.wxcloudrun.controller;

import com.tencent.wxcloudrun.config.ApiResponse;
import com.tencent.wxcloudrun.model.User;
import com.tencent.wxcloudrun.service.AuthService;
import com.tencent.wxcloudrun.service.DataService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * 游戏数据存取 HTTP 接口
 * 
 * 响应格式兼容 feijiu2 前端：{ "cmd": NNN, "status": "success"/"fail", ... }
 */
@RestController
@RequestMapping("/api/data")
public class DataController {

    private static final Logger logger = LoggerFactory.getLogger(DataController.class);

    @Autowired
    private DataService dataService;

    @Autowired
    private AuthService authService;

    /**
     * 获取分区全量数据（登录后调用）
     * POST /api/data/get-region
     * Body: { "token": "xxx", "regionId": "1" }
     */
    @PostMapping("/get-region")
    public ApiResponse getRegionData(@RequestBody Map<String, String> body) {
        User user = authService.validateToken(body.get("token"));
        if (user == null) {
            return ApiResponse.error("token 无效");
        }

        String regionId = body.getOrDefault("regionId", "1");
        String jsonData = dataService.getRegionDataJson(user.getUid(), regionId);

        Map<String, Object> data = new HashMap<>();
        data.put("uid", user.getUid());
        data.put("regionId", regionId);
        data.put("jsonData", jsonData);
        data.put("token", user.getToken());
        data.put("nickName", user.getNickName());
        return ApiResponse.ok(data);
    }

    /**
     * 按 key 获取单条数据
     * POST /api/data/get-by-key
     * Body: { "token": "xxx", "regionId": "1", "key": "JSF_playerModel" }
     */
    @PostMapping("/get-by-key")
    public ApiResponse getByKey(@RequestBody Map<String, String> body) {
        User user = authService.validateToken(body.get("token"));
        if (user == null) {
            return ApiResponse.error("token 无效");
        }

        String regionId = body.getOrDefault("regionId", "1");
        String key = body.get("key");
        if (key == null || key.isEmpty()) {
            return ApiResponse.error("key 不能为空");
        }

        String val = dataService.getByKey(user.getUid(), regionId, key);

        Map<String, Object> data = new HashMap<>();
        data.put("key", key);
        data.put("val", val != null ? val : "");
        return ApiResponse.ok(data);
    }

    /**
     * 保存单条数据
     * POST /api/data/save
     * Body: { "token": "xxx", "regionId": "1", "key": "xxx", "val": "xxx", "dml": "add" }
     * dml: "add upd" 插入或更新, "del" 删除
     */
    @PostMapping("/save")
    public ApiResponse saveRecord(@RequestBody Map<String, String> body) {
        User user = authService.validateToken(body.get("token"));
        if (user == null) {
            return ApiResponse.error("token 无效");
        }

        String regionId = body.getOrDefault("regionId", "1");
        String key = body.get("key");
        String val = body.getOrDefault("val", "");
        String dml = body.getOrDefault("dml", "add");

        if (key == null || key.isEmpty()) {
            return ApiResponse.error("key 不能为空");
        }

        dataService.saveRecord(user.getUid(), regionId, key, val, dml);
        return ApiResponse.ok();
    }

    /**
     * 批量保存
     * POST /api/data/save-multi
     * Body: { "token": "xxx", "regionId": "1", "multiData": "{\"key1\":\"val1\",...}", "ops": "upd" }
     * ops: "upd" 更新, "del" 删除
     */
    @PostMapping("/save-multi")
    public ApiResponse saveMulti(@RequestBody Map<String, String> body) {
        User user = authService.validateToken(body.get("token"));
        if (user == null) {
            return ApiResponse.error("token 无效");
        }

        String regionId = body.getOrDefault("regionId", "1");
        String multiData = body.get("multiData");
        String ops = body.getOrDefault("ops", "upd");

        if (multiData == null || multiData.isEmpty()) {
            return ApiResponse.error("multiData 不能为空");
        }

        try {
            dataService.saveMultiRecord(user.getUid(), regionId, multiData, ops);
            return ApiResponse.ok();
        } catch (Exception e) {
            logger.error("批量存档失败", e);
            return ApiResponse.error("存档失败: " + e.getMessage());
        }
    }

    /**
     * 全量存档
     * POST /api/data/save-all
     * Body: { "token": "xxx", "regionId": "1", "allData": "{\"key1\":val1,...}" }
     */
    @PostMapping("/save-all")
    public ApiResponse saveAll(@RequestBody Map<String, String> body) {
        User user = authService.validateToken(body.get("token"));
        if (user == null) {
            return ApiResponse.error("token 无效");
        }

        String regionId = body.getOrDefault("regionId", "1");
        String allData = body.getOrDefault("allData", "{}");

        try {
            dataService.saveAll(user.getUid(), regionId, allData);
            return ApiResponse.ok();
        } catch (Exception e) {
            logger.error("全量存档失败", e);
            return ApiResponse.error("存档失败: " + e.getMessage());
        }
    }
}
