package com.tencent.wxcloudrun.protocol;

/**
 * 游戏命令码枚举 —— 对应 feijiu2 前端 GNetCmd 定义
 * 
 * 通信模式说明：
 *   HTTP：登录、注册、存档读写、充值订单
 *   WebSocket：心跳、粘包、实时推送（排行榜、活动）
 */
public enum GameCommand {

    // ==================== 粘包/心跳 ====================
    StickPack(100000, "粘包分片"),
    Heartbeat(101000, "心跳 + 时间同步"),

    // ==================== 用户登录/注册 (HTTP) ====================
    UserLogin(101001, "用户登录"),
    UserRegister(101002, "用户注册"),
    AntiAddiction(101003, "防沉迷认证"),
    SensitiveWordsCheck(101004, "敏感词检测"),
    UniqueLogin(101005, "游客/Facebook/Google 统一登录"),

    // ==================== 游戏数据存取 (HTTP) ====================
    SaveUserRecord(102003, "单 key 存档"),
    GetRegionData(102004, "获取分区全量数据"),
    GetRegionList(102005, "获取分区列表"),
    SaveUserRecordAll(102007, "全量存档"),
    UploadAvatar(102008, "上传头像"),
    GetUserDataByKey(102009, "按 key 读取数据"),
    SaveUserRecordMulti(102010, "批量多 key 存档"),
    GetCodeDataByKey(102011, "兑换码数据查询"),

    // ==================== 微信/手机登录 (HTTP) ====================
    ReqWxSession(103001, "微信 code 换 session"),
    ReqSendAuthCode(103002, "发送短信验证码"),
    ReqPhoneLogin(103003, "手机号登录"),

    // ==================== 社交/邮件 (WebSocket 推送) ====================
    BindFaceBook(104001, "绑定 Facebook"),
    BindGoogle(104002, "绑定 Google"),
    ToClientNewMail(104003, "服务端推送邮件"),
    GetActiveCode(104004, "激活码兑换"),

    // ==================== 背包日志/分享 ====================
    SetPlayerPkgLog(105001, "背包操作日志"),
    ReceiveGodWealth(105002, "财神分享领取"),

    // ==================== 充值 (HTTP) ====================
    CreateChargeOrder(106001, "创建充值订单"),
    UpdChargeOrderStatus(106002, "更新订单状态"),

    // ==================== 排行榜/社交 (WebSocket) ====================
    GetRankInfo(201001, "排行榜查询"),
    GetInviteCode(201002, "获取邀请码"),
    GetInviteCodeReward(201003, "输入邀请码领取奖励"),
    GetInviteLevelReward(201004, "邀请等级奖励"),
    PushInviteProcess(201005, "推送邀请进度"),
    IsRankOpen(201006, "排行榜是否开放"),
    GetSomeonePlayerInfo(201007, "获取其他玩家信息"),

    // ==================== 活动 (WebSocket) ====================
    GetMidAutumnRank(301001, "中秋活动排行榜"),
    SetMidAutumnRank(301002, "设置中秋排行榜"),
    GetWorldBossRank(301003, "世界 Boss 排行查看"),
    SetWorldBossRank(301004, "世界 Boss 排行设置"),
    PushWorldBossSettle(301005, "世界 Boss 结算推送"),
    SetWorldBossPlayerStatus(301006, "设置世界 Boss 挑战状态"),
    GetWorldBossPlayerStatus(301007, "读取世界 Boss 挑战状态"),

    // ==================== 帝国战争 (WebSocket) ====================
    GetEmpireSpotData(302001, "帝国战争地块数据"),
    ActionEmpireSpot(302002, "帝国战争地块操作"),

    // 未知命令
    UNKNOWN(-1, "未知命令");

    private final int code;
    private final String description;

    GameCommand(int code, String description) {
        this.code = code;
        this.description = description;
    }

    public int getCode() {
        return code;
    }

    public String getDescription() {
        return description;
    }

    /**
     * 根据命令码查找枚举，找不到返回 UNKNOWN
     */
    public static GameCommand fromCode(int code) {
        for (GameCommand cmd : values()) {
            if (cmd.code == code) {
                return cmd;
            }
        }
        return UNKNOWN;
    }
}
