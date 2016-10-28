package widebox.jelf.NettyWebSocket.service;

import widebox.jelf.NettyWebSocket.dto.Response;
import widebox.jelf.NettyWebSocket.entity.Client;

public class MessageService {

	public static Response sendMessage(Client client, String message) {
		Response res = new Response();
		res.getData().put("userId", client.getUserId());
		res.getData().put("message", message);
		res.getData().put("ts", System.currentTimeMillis());// 返回毫秒数
		return res;
	}
}
