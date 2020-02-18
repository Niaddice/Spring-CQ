package xin.lz1998.cq.robot;

import com.alibaba.fastjson.JSONObject;
import com.alibaba.fastjson.TypeReference;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.config.ConfigurableBeanFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.context.annotation.ScopedProxyMode;
import org.springframework.stereotype.Service;
import org.springframework.web.socket.WebSocketSession;
import xin.lz1998.cq.entity.CQGroupAnonymous;
import xin.lz1998.cq.entity.CQStatus;
import xin.lz1998.cq.retdata.*;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public class CoolQ {

    @Getter
    @Setter
    private long selfId;

    @Getter
    @Setter
    private WebSocketSession botSession;

    private EventHandler eventHandler;

    private int apiEcho = 0;//用于标记是哪次发送api，接受时作为key放入apiResponseMap

    private Map<String, ApiSender> apiCallbackMap = new HashMap<>();//用于存放api调用，收到响应时put，处理完成remove

    private RobotConfigInterface robotConfig;

    public CoolQ(long selfId, WebSocketSession botSession, EventHandler eventHandler,RobotConfigInterface robotConfig) {
        this.selfId = selfId;
        this.botSession = botSession;
        this.eventHandler = eventHandler;
        this.robotConfig=robotConfig;
    }

    public void onReceiveApiMessage(JSONObject message) {
        log.debug(selfId + " RECV API   {}", message);
        String echo = message.get("echo").toString();
        ApiSender apiSender = apiCallbackMap.get(echo);
        apiSender.onReceiveJson(message);
        apiCallbackMap.remove(echo);
    }

    private JSONObject sendApiMessage(ApiEnum action, JSONObject params) {
        JSONObject apiJSON = constructApiJSON(action, params);
        String echo = apiJSON.getString("echo");
        ApiSender apiSender = new ApiSender(botSession);
        apiCallbackMap.put(echo, apiSender);
        log.debug("{} SEND API   {} {}", selfId, action.getDesc(), params);
        JSONObject retJson;
        try {
            retJson = apiSender.sendApiJson(apiJSON,robotConfig.getApiTimeout());
        } catch (Exception e) {
            e.printStackTrace();
            retJson = new JSONObject();
            retJson.put("status", "failed");
            retJson.put("retcode", -1);
        }
        return retJson;
    }

    public void onReceiveEventMessage(JSONObject message) {

        log.debug(selfId + " RECV Event {}", message);
        // TODO 这里改为线程池，或者在WebSocketHandler/EventHandler里面加线程池
        new Thread(() -> eventHandler.handle(CoolQ.this, message)).start();
    }


    private JSONObject constructApiJSON(ApiEnum action, JSONObject params) {
        JSONObject apiJSON = new JSONObject();
        apiJSON.put("action", action.getUrl());
        if (params != null)
            apiJSON.put("params", params);
        apiJSON.put("echo", apiEcho++);

        return apiJSON;
    }

    /**
     * 发送私聊消息
     *
     * @param user_id     对方 QQ 号
     * @param message     要发送的内容
     * @param auto_escape 消息内容是否作为纯文本发送（即不解析 CQ 码），只在 message 字段是字符串时有效
     * @return
     */
    public ApiData<MessageData> sendPrivateMsg(long user_id, String message, boolean auto_escape) {
        ApiEnum action = ApiEnum.SEND_PRIVATE_MSG;

        JSONObject params = new JSONObject();
        params.put("user_id", user_id);
        params.put("message", message);
        params.put("auto_escape", auto_escape);

        ApiData<MessageData> result = sendApiMessage(action, params).toJavaObject(new TypeReference<ApiData<MessageData>>() {
        });
        return result;
    }

    /**
     * 发送群消息
     *
     * @param group_id    群号
     * @param message     要发送的内容
     * @param auto_escape 消息内容是否作为纯文本发送（即不解析 CQ 码），只在 message 字段是字符串时有效
     * @return
     */
    public ApiData<MessageData> sendGroupMsg(long group_id, String message, boolean auto_escape) {

        ApiEnum action = ApiEnum.SEND_GROUP_MSG;

        JSONObject params = new JSONObject();
        params.put("group_id", group_id);
        params.put("message", message);
        params.put("auto_escape", auto_escape);


        ApiData<MessageData> result = sendApiMessage(action, params).toJavaObject(new TypeReference<ApiData<MessageData>>() {
        });
        return result;
    }

    /**
     * 发送讨论组消息
     *
     * @param discuss_id  讨论组 ID（正常情况下看不到，需要从讨论组消息上报的数据中获得）
     * @param message     要发送的内容
     * @param auto_escape 消息内容是否作为纯文本发送（即不解析 CQ 码），只在 message 字段是字符串时有效
     * @return
     */
    public ApiData<MessageData> sendDiscussMsg(long discuss_id, String message, boolean auto_escape) {
        ApiEnum action = ApiEnum.SEND_DISCUSS_MSG;

        JSONObject params = new JSONObject();
        params.put("discuss_id", discuss_id);
        params.put("message", message);
        params.put("auto_escape", auto_escape);

        ApiData<MessageData> result = sendApiMessage(action, params).toJavaObject(new TypeReference<ApiData<MessageData>>() {
        });
        return result;
    }

    /**
     * 撤回消息
     *
     * @param message_id 消息 ID
     * @return
     */
    public ApiRawData deleteMsg(int message_id) {
        ApiEnum action = ApiEnum.DELETE_MSG;

        JSONObject params = new JSONObject();
        params.put("message_id", message_id);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 发送好友赞
     *
     * @param user_id 对方 QQ 号
     * @param times   赞的次数，每个好友每天最多 10 次
     * @return
     */
    public ApiRawData sendLike(long user_id, Integer times) {
        ApiEnum action = ApiEnum.SEND_LIKE;

        JSONObject params = new JSONObject();
        params.put("user_id", user_id);
        params.put("times", times);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 群组踢人
     *
     * @param group_id           群号
     * @param user_id            要踢的 QQ 号
     * @param reject_add_request 拒绝此人的加群请求
     * @return
     */
    public ApiRawData setGroupKick(long group_id, long user_id, boolean reject_add_request) {
        ApiEnum action = ApiEnum.SET_GROUP_KICK;

        JSONObject params = new JSONObject();
        params.put("group_id", group_id);
        params.put("user_id", user_id);
        params.put("reject_add_request", reject_add_request);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 群组单人禁言
     *
     * @param group_id 群号
     * @param user_id  要禁言的 QQ 号
     * @param duration 禁言时长，单位秒，0 表示取消禁言
     * @return
     */
    public ApiRawData setGroupBan(long group_id, long user_id, long duration) {
        ApiEnum action = ApiEnum.SET_GROUP_BAN;

        JSONObject params = new JSONObject();
        params.put("group_id", group_id);
        params.put("user_id", user_id);
        params.put("duration", duration);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 群组匿名用户禁言
     *
     * @param group_id         群号
     * @param cqGroupAnonymous 要禁言的匿名用户对象（群消息上报的 anonymous 字段）
     * @param duration         禁言时长，单位秒，无法取消匿名用户禁言
     * @return
     */
    public ApiRawData setGroupAnonymousBan(long group_id, CQGroupAnonymous cqGroupAnonymous, boolean duration) {
        ApiEnum action = ApiEnum.SET_GROUP_ANONYMOUS_BAN;

        JSONObject params = new JSONObject();
        params.put("group_id", group_id);
        params.put("anonymous", cqGroupAnonymous);
        params.put("duration", duration);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 群组匿名用户禁言
     *
     * @param group_id 群号
     * @param flag     要禁言的匿名用户的 flag（需从群消息上报的数据中获得）
     * @param duration 禁言时长，单位秒，无法取消匿名用户禁言
     * @return
     */
    public ApiRawData setGroupAnonymousBan(long group_id, String flag, boolean duration) {
        ApiEnum action = ApiEnum.SET_GROUP_ANONYMOUS_BAN;

        JSONObject params = new JSONObject();
        params.put("group_id", group_id);
        params.put("flag", flag);
        params.put("duration", duration);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 群组全员禁言
     *
     * @param group_id 群号
     * @param enable   是否禁言
     * @return
     */
    public ApiRawData setGroupWholeBan(long group_id, boolean enable) {
        ApiEnum action = ApiEnum.SET_GROUP_WHOLE_BAN;
        JSONObject params = new JSONObject();
        params.put("group_id", group_id);
        params.put("enable", enable);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 群组设置管理员
     *
     * @param group_id 群号
     * @param user_id  要设置管理员的 QQ 号
     * @param enable   true 为设置，false 为取消
     * @return
     */
    public ApiRawData setGroupAdmin(long group_id, long user_id, boolean enable) {
        ApiEnum action = ApiEnum.SET_GROUP_ADMIN;

        JSONObject params = new JSONObject();
        params.put("group_id", group_id);
        params.put("user_id", user_id);
        params.put("enable", enable);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 群组匿名
     *
     * @param group_id 群号
     * @param enable   是否允许匿名聊天
     * @return
     */
    public ApiRawData setGroupAnonymous(long group_id, boolean enable) {
        ApiEnum action = ApiEnum.SET_GROUP_ANONYMOUS;

        JSONObject params = new JSONObject();
        params.put("group_id", group_id);
        params.put("enable", enable);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 设置群名片（群备注）
     *
     * @param group_id 群号
     * @param user_id  要设置的 QQ 号
     * @param card     群名片内容，不填或空字符串表示删除群名片
     * @return
     */
    public ApiRawData setGroupCard(long group_id, long user_id, String card) {
        ApiEnum action = ApiEnum.SET_GROUP_CARD;

        JSONObject params = new JSONObject();
        params.put("group_id", group_id);
        params.put("user_id", user_id);
        params.put("card", card);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * @param group_id   群号
     * @param is_dismiss 是否解散，如果登录号是群主，则仅在此项为 true 时能够解散
     * @return
     */
    public ApiRawData setGroupLeave(long group_id, boolean is_dismiss) {
        ApiEnum action = ApiEnum.SET_GROUP_LEAVE;

        JSONObject params = new JSONObject();
        params.put("group_id", group_id);
        params.put("is_dismiss", is_dismiss);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 设置群组专属头衔
     *
     * @param group_id      群号
     * @param user_id       要设置的 QQ 号
     * @param special_title 专属头衔，不填或空字符串表示删除专属头衔
     * @param duration      专属头衔有效期，单位秒，-1 表示永久，不过此项似乎没有效果，可能是只有某些特殊的时间长度有效，有待测试
     * @return
     */
    public ApiRawData setGroupSpecialTitle(long group_id, long user_id, String special_title, boolean duration) {
        ApiEnum action = ApiEnum.SET_GROUP_SPECIAL_TITLE;

        JSONObject params = new JSONObject();
        params.put("group_id", group_id);
        params.put("user_id", user_id);
        params.put("special_title", special_title);
        params.put("duration", duration);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 退出讨论组
     *
     * @param discuss_id 讨论组 ID（正常情况下看不到，需要从讨论组消息上报的数据中获得）
     * @return
     */
    public ApiRawData setDiscussLeave(long discuss_id) {
        ApiEnum action = ApiEnum.SET_DISCUSS_LEAVE;

        JSONObject params = new JSONObject();
        params.put("discuss_id", discuss_id);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 处理加好友请求
     *
     * @param flag    加好友请求的 flag（需从上报的数据中获得）
     * @param approve 是否同意请求
     * @param remark  添加后的好友备注（仅在同意时有效）
     * @return
     */
    public ApiRawData setFriendAddRequest(String flag, boolean approve, String remark) {
        ApiEnum action = ApiEnum.SET_FRIEND_ADD_REQUEST;

        JSONObject params = new JSONObject();
        params.put("flag", flag);
        params.put("approve", approve);
        params.put("remark", remark);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 处理加群请求／邀请
     *
     * @param flag     加群请求的 flag（需从上报的数据中获得）
     * @param sub_type add 或 invite，请求类型（需要和上报消息中的 sub_type 字段相符）
     * @param approve  是否同意请求／邀请
     * @param reason   拒绝理由（仅在拒绝时有效）
     * @return
     */
    public ApiRawData setGroupAddRequest(String flag, String sub_type, boolean approve, String reason) {
        ApiEnum action = ApiEnum.SET_GROUP_ADD_REQUEST;

        JSONObject params = new JSONObject();
        params.put("flag", flag);
        params.put("sub_type", sub_type);
        params.put("approve", approve);
        params.put("reason", reason);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 获取登录号信息
     *
     * @return
     */
    public ApiData<LoginInfoData> getLoginInfo() {
        ApiEnum action = ApiEnum.GET_LOGIN_INFO;

        ApiData<LoginInfoData> result = sendApiMessage(action, null).toJavaObject(new TypeReference<ApiData<LoginInfoData>>() {
        });
        return result;
    }

    /**
     * 获取陌生人信息
     *
     * @param user_id  QQ 号
     * @param no_cache 是否不使用缓存（使用缓存可能更新不及时，但响应更快）
     * @return
     */
    public ApiData<StrangerInfoData> getStrangerInfo(long user_id, boolean no_cache) {

        ApiEnum action = ApiEnum.GET_STRANGER_INFO;

        JSONObject params = new JSONObject();
        params.put("user_id", user_id);
        params.put("no_cache", no_cache);

        ApiData<StrangerInfoData> result = sendApiMessage(action, null).toJavaObject(new TypeReference<ApiData<StrangerInfoData>>() {
        });
        return result;
    }

    /**
     * 获取好友列表
     *
     * @return
     */
    public ApiListData<FriendData> getFriendList() {
        ApiEnum action = ApiEnum.GET_FRIEND_LIST;
        ApiListData<FriendData> result = sendApiMessage(action, null).toJavaObject(new TypeReference<ApiListData<FriendData>>() {
        });
        return result;
    }

    /**
     * 获取群列表
     *
     * @return
     */
    public ApiListData<GroupData> getGroupList() {
        ApiEnum action = ApiEnum.GET_GROUP_LIST;

        ApiListData<GroupData> result = sendApiMessage(action, null).toJavaObject(new TypeReference<ApiListData<GroupData>>() {
        });
        return result;
    }

    /**
     * 获取群信息
     *
     * @param group_id 群号
     * @param no_cache 是否不使用缓存（使用缓存可能更新不及时，但响应更快）
     * @return
     */
    public ApiData<GroupInfoData> getGroupInfo(long group_id, boolean no_cache) {
        ApiEnum action = ApiEnum.GET_GROUP_INFO;
        JSONObject params = new JSONObject();
        params.put("group_id", group_id);
        params.put("no_cache", no_cache);
        ApiData<GroupInfoData> result = sendApiMessage(action, params).toJavaObject(new TypeReference<ApiData<GroupInfoData>>() {
        });
        return result;
    }

    /**
     * 获取群成员信息
     *
     * @param group_id 群号
     * @param user_id  QQ 号
     * @param no_cache 是否不使用缓存（使用缓存可能更新不及时，但响应更快）
     * @return
     */
    public ApiData<GroupMemberInfoData> getGroupMemberInfo(long group_id, long user_id, boolean no_cache) {
        ApiEnum action = ApiEnum.GET_GROUP_MEMBER_INFO;

        JSONObject params = new JSONObject();
        params.put("group_id", group_id);
        params.put("user_id", user_id);
        params.put("no_cache", no_cache);

        ApiData<GroupMemberInfoData> result = sendApiMessage(action, params).toJavaObject(new TypeReference<ApiData<GroupMemberInfoData>>() {
        });
        return result;
    }

    /**
     * 获取群成员列表
     * <p>
     * 响应内容为 JSON 数组，每个元素的内容和上面的 /get_group_member_info 接口相同，但对于同一个群组的同一个成员，获取列表时和获取单独的成员信息时，某些字段可能有所不同，例如 area、title 等字段在获取列表时无法获得，具体应以单独的成员信息为准。
     *
     * @param group_id 群号
     * @return
     */
    public ApiListData<GroupMemberInfoData> getGroupMemberList(long group_id) {
        ApiEnum action = ApiEnum.GET_GROUP_MEMBER_LIST;
        JSONObject params = new JSONObject();

        params.put("group_id", group_id);
        ApiListData<GroupMemberInfoData> result = sendApiMessage(action, params).toJavaObject(new TypeReference<ApiListData<GroupMemberInfoData>>() {
        });

        return result;
    }


    /**
     * 获取 Cookies
     *
     * @param domain 需要获取 cookies 的域名
     * @return
     */
    public ApiData<CookiesData> getCookies(String domain) {
        ApiEnum action = ApiEnum.GET_COOKIES;

        JSONObject params = new JSONObject();
        params.put("domain", domain);

        ApiData<CookiesData> result = sendApiMessage(action, params).toJavaObject(new TypeReference<ApiData<CookiesData>>() {
        });
        return result;
    }

    /**
     * 获取 CSRF Token
     *
     * @return
     */
    public ApiData<CsrfTokenData> getCsrfToken() {
        ApiEnum action = ApiEnum.GET_CSRF_TOKEN;

        ApiData<CsrfTokenData> result = sendApiMessage(action, null).toJavaObject(new TypeReference<ApiData<CsrfTokenData>>() {
        });
        return result;
    }

    /**
     * 获取 QQ 相关接口凭证
     * 即上面两个接口的合并
     *
     * @param domain 需要获取 cookies 的域名
     * @return
     */
    public ApiData<CredentialsData> getCredentials(String domain) {
        ApiEnum action = ApiEnum.GET_CREDENTIALS;

        JSONObject params = new JSONObject();
        params.put("domain", domain);

        ApiData<CredentialsData> result = sendApiMessage(action, params).toJavaObject(new TypeReference<ApiData<CredentialsData>>() {
        });
        return result;
    }

    /**
     * 获取语音
     *
     * @param file       收到的语音文件名（CQ 码的 file 参数），如 0B38145AA44505000B38145AA4450500.silk
     * @param out_format 要转换到的格式，目前支持 mp3、amr、wma、m4a、spx、ogg、wav、flac
     * @param full_path  是否返回文件的绝对路径（Windows 环境下建议使用，Docker 中不建议）
     * @return
     */
    public ApiData<FileData> getRecord(String file, String out_format, boolean full_path) {
        ApiEnum action = ApiEnum.GET_RECORD;

        JSONObject params = new JSONObject();
        params.put("file", file);
        params.put("out_format", out_format);
        params.put("full_path", full_path);

        ApiData<FileData> result = sendApiMessage(action, params).toJavaObject(new TypeReference<ApiData<FileData>>() {
        });
        return result;
    }

    /**
     * 获取图片
     *
     * @param file 收到的图片文件名（CQ 码的 file 参数），如 6B4DE3DFD1BD271E3297859D41C530F5.jpg
     * @return
     */
    public ApiData<FileData> getImage(String file) {
        ApiEnum action = ApiEnum.GET_IMAGE;

        JSONObject params = new JSONObject();
        params.put("file", file);

        ApiData<FileData> result = sendApiMessage(action, params).toJavaObject(new TypeReference<ApiData<FileData>>() {
        });
        return result;
    }

    /**
     * 检查是否可以发送图片
     *
     * @return
     */
    public ApiData<BooleanData> canSendImage() {
        ApiEnum action = ApiEnum.CAN_SEND_IMAGE;

        ApiData<BooleanData> result = sendApiMessage(action, null).toJavaObject(new TypeReference<ApiData<BooleanData>>() {
        });
        return result;
    }

    /**
     * 检查是否可以发送语音
     *
     * @return
     */
    public ApiData<BooleanData> canSendRecord() {
        ApiEnum action = ApiEnum.CAN_SEND_RECORD;

        ApiData<BooleanData> result = sendApiMessage(action, null).toJavaObject(new TypeReference<ApiData<BooleanData>>() {
        });
        return result;
    }

    /**
     * 获取插件运行状态
     *
     * @return
     */
    public ApiData<CQStatus> getStatus() {
        ApiEnum action = ApiEnum.GET_STATUS;

        ApiData<CQStatus> result = sendApiMessage(action, null).toJavaObject(new TypeReference<ApiData<CQStatus>>() {
        });
        return result;
    }

    /**
     * 获取 酷Q 及 HTTP API 插件的版本信息
     * 参数
     *
     * @return
     */
    public ApiData<VersionInfoData> getVersionInfo() {
        ApiEnum action = ApiEnum.GET_VERSION_INFO;

        ApiData<VersionInfoData> result = sendApiMessage(action, null).toJavaObject(new TypeReference<ApiData<VersionInfoData>>() {
        });
        return result;
    }

    /**
     * 重启 HTTP API 插件
     *
     * @param delay 要延迟的毫秒数，如果默认情况下无法重启，可以尝试设置延迟为 2000 左右
     * @return
     */
    public ApiRawData setRestartPlugin(int delay) {
        ApiEnum action = ApiEnum.SET_RESTART_PLUGIN;

        JSONObject params = new JSONObject();
        params.put("delay", delay);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 清理数据目录
     *
     * @param data_dir 收到清理的目录名，支持 image、record、show、bface
     * @return
     */
    public ApiRawData cleanDataDir(String data_dir) {
        ApiEnum action = ApiEnum.CLEAN_DATA_DIR;

        JSONObject params = new JSONObject();
        params.put("data_dir", data_dir);

        ApiRawData result = sendApiMessage(action, params).toJavaObject(ApiRawData.class);
        return result;
    }

    /**
     * 清理插件日志
     *
     * @return
     */
    public ApiRawData cleanPluginLog() {
        ApiEnum action = ApiEnum.CLEAN_PLUGIN_LOG;

        ApiRawData result = sendApiMessage(action, null).toJavaObject(ApiRawData.class);
        return result;
    }
}
