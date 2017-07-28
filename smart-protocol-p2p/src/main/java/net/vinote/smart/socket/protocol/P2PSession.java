package net.vinote.smart.socket.protocol;

import net.vinote.smart.socket.protocol.p2p.message.BaseMessage;
import net.vinote.smart.socket.protocol.p2p.message.MessageType;
import net.vinote.smart.socket.protocol.p2p.Session;
import net.vinote.smart.socket.transport.IoSession;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.security.InvalidParameterException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * @author Seer
 */
public class P2PSession implements Session<BaseMessage> {
    private static Logger logger = LogManager.getLogger(P2PSession.class);
    public static final String SESSION_KEY="session";
    private String remoteIp;
    private String localAddress;
    private IoSession<BaseMessage> ioSession;
    /**
     * 会话创建时间
     */
    private final long creatTime;
    /**
     * 最长闲置时间
     */
    private int maxInactiveInterval;
    /**
     * 当前会话唯一标识
     */
    private int sessionId = 0;
    /**
     * 失效标识
     */
    private boolean invalidated = false;
    private Map<Integer, BaseMessage> synchRespMap;

    private Map<String, Object> attributeMap = new ConcurrentHashMap<String, Object>();

    public P2PSession(IoSession<BaseMessage> ioSession) {
        sessionId = ioSession.getSessionID();
        this.ioSession = ioSession;
        maxInactiveInterval = ioSession.getTimeout();
        synchRespMap = new ConcurrentHashMap<Integer, BaseMessage>();
        creatTime = System.currentTimeMillis();
    }

    @SuppressWarnings("unchecked")
    public <T> T getAttribute(String name) {
        return (T) attributeMap.get(name);
    }

    public long getCreationTime() {
        return creatTime;
    }


    public int getMaxInactiveInterval() {
        return maxInactiveInterval;
    }

    public String getRemoteIp() {
        return remoteIp;
    }

    public void invalidate(boolean immediate) {

        invalidated = true;
        for (BaseMessage unit : synchRespMap.values()) {
            synchronized (unit) {
                unit.notifyAll();
            }
        }
        attributeMap.clear();
        ioSession.close(immediate);
    }

    public void invalidate() {
        invalidate(true);
    }

    public void removeAttribute(String name) {
        // TODO Auto-generated method stub

    }

    public void setAttribute(String name, Object value) {
        attributeMap.put(name, value);
    }

    public void setMaxInactiveInterval(int interval) {
        maxInactiveInterval = interval;
    }

    @Override
    public String toString() {
        return "OMCSession [remoteIp=" + remoteIp + ", ioSession=" + ioSession + ", creatTime=" + creatTime
                + ", maxInactiveInterval=" + maxInactiveInterval + ", sessionId="
                + sessionId + ", invalidated=" + invalidated + "]";
    }

    public boolean isInvalid() {
        return invalidated;
    }

    private void assertTransactionSession() throws IOException {
        if (ioSession == null || !ioSession.isValid()) {
            throw new IOException("Socket Channel is invalid now");
        }
    }


    public String getLocalAddress() {
        return localAddress;
    }

    public boolean notifySyncMessage(BaseMessage baseMsg) {
        BaseMessage respMsg = (BaseMessage) baseMsg;
        if (isRequestMessage(respMsg.getMessageType())) {
            return false;
        }
        int sequenceId = respMsg.getHead().getSequenceID();
        BaseMessage reqMsg = synchRespMap.get(sequenceId);
        if (reqMsg != null) {
            synchronized (reqMsg) {
                if (synchRespMap.containsKey(sequenceId)) {
                    synchRespMap.put(sequenceId, respMsg);
                    reqMsg.notifyAll();
                    return true;
                }
            }
        }
        return false;

    }


    public void sendWithoutResponse(BaseMessage requestMsg) throws Exception {
        assertTransactionSession();
        ByteBuffer buffer = requestMsg.encode();
        ioSession.write(buffer);
    }

    public BaseMessage sendWithResponse(BaseMessage requestMsg, long timeout) throws Exception {
        BaseMessage reqMsg = (BaseMessage) requestMsg;
        assertTransactionSession();

        if (!isRequestMessage(reqMsg.getMessageType())) {
            throw new InvalidParameterException("current message is not a requestMessage, messageType is 0x"
                    + Integer.toHexString(reqMsg.getMessageType()));
        }
        ByteBuffer buffer = reqMsg.encode();// 必须执行encode才可产生sequenceId
        int sequenceId = reqMsg.getHead().getSequenceID();
        synchRespMap.put(sequenceId, reqMsg);
        ioSession.write(buffer);
        if (synchRespMap.get(sequenceId) == reqMsg) {
            synchronized (reqMsg) {
                if (synchRespMap.get(sequenceId) == reqMsg) {
                    try {
                        reqMsg.wait(timeout);
                    } catch (InterruptedException e) {
                        logger.warn("", e);
                    }
                }
            }
        }
        BaseMessage resp = null;
        synchronized (reqMsg) {
            resp = synchRespMap.remove(sequenceId);
        }
        if (resp == null || resp == reqMsg) {
            throw new SocketTimeoutException("Message " + sequenceId + " is timeout!");
        }
        return resp;

    }

    public BaseMessage sendWithResponse(BaseMessage requestMsg) throws Exception {
        return sendWithResponse(requestMsg, ioSession.getTimeout());
    }

    /**
     * 是否为请求消息类型
     *
     * @param msgType
     * @return
     */
    private boolean isRequestMessage(int msgType) {
        return (MessageType.RESPONSE_MESSAGE & msgType) == MessageType.REQUEST_MESSAGE;
    }
}
