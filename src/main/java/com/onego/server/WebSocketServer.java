package com.onego.server;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import javax.websocket.*;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;
import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author yiwt
 */
@ServerEndpoint(value = "/chat/{senderId}")
@Component
@Slf4j
public class WebSocketServer {
    /**
     * 记录当前在线连接数
     */
    public static final Map<Integer, Session> sessionMap = new ConcurrentHashMap<>();

    /**
     * 当连接建立成功后调用
     */
    @OnOpen
    public void onOpen(Session session,
                       @PathParam("senderId") Integer senderId) {
        sessionMap.put(senderId, session);
        log.info("{}加入聊天", senderId);
    }

    /**
     * 连接关闭时调用
     */
    @OnClose
    public void onClose(@PathParam("senderId") Integer senderId) {
        sessionMap.remove(senderId);
        log.info("{}离开", senderId);
    }

    /**
     * 收到客户端消息后调用的方法
     * 后台收到客户端发送过来的消息
     * onMessage 是一个消息的中转站
     * 接受 浏览器端 socket.send 发送过来的 json数据
     * @param message 客户端发送过来的消息
     */
    @OnMessage
    public void onMessage(Session session, String message,
                       @PathParam("senderId") Integer senderId) {
        log.info("服务端收到用户username={}的消息:{}", senderId, message);
        JSONObject obj = JSONUtil.parseObj(message);
        // {"to": "admin", "text": "聊天文本"}
        Integer toUser = obj.getInt("to");
        String text = obj.getStr("text");
        // 根据 toUser来获取 session，再通过session发送消息文本
        Session toSession = sessionMap.get(toUser);
        if (toSession != null) {
            JSONObject jsonObject = new JSONObject();
            jsonObject.set("from", senderId);
            jsonObject.set("text", text);
            this.sendMessage(jsonObject.toString(), toSession);
        } else {
            log.info("发送失败，未找到用户{}的session", toUser);
        }
    }

    @OnError
    public void onError(Session session, Throwable error) {
        log.error("发生错误");
        error.printStackTrace();
    }

    private void sendMessage(String message, Session toSession) {
        try {
            toSession.getBasicRemote().sendText(message);
        } catch (IOException e) {
            log.error("服务端发送消息给客户端失败", e);
        }
    }
}
