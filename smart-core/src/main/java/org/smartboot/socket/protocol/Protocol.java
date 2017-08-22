package org.smartboot.socket.protocol;

import org.smartboot.socket.transport.AioSession;

import java.nio.ByteBuffer;

/**
 *
 * 消息传输采用的协议 注意:同一个协议解析器切勿同时用于多个socket链路,否则将出息码流错乱情况
 *
 * @author Seer
 * @version Protocol.java, v 0.1 2015年3月13日 下午3:30:57 Seer Exp.
 */
public interface Protocol<T> {
	/**
	 * 对于从Socket流中获取到的数据采用当前Protocol的实现类协议进行解析
	 *
	 * @param data
	 * @return 本次解码所成功解析的消息实例集合,不允许返回null
	 */
	public T decode(ByteBuffer data, AioSession<T> session);


	/**
	 * 将业务消息实体编码成ByteBuffer用于输出至对端。
	 * <b>切勿在encode中直接调用session.write,编码后的byteuffer需交由框架本身来输出</b>
	 * @param t
	 * @param session
	 * @return
	 */
	public ByteBuffer encode(T t,AioSession<T> session);

}