package widebox.jelf.NettyWebSocket.service;

import org.apache.commons.codec.binary.Base64;
import org.json.JSONException;
import org.json.JSONObject;

import widebox.jelf.NettyWebSocket.entity.Client;

public class RequestService {

	/**
	 * 根据客户端的请求生成 Client
	 *
	 * @param request
	 *            例如 {userId:1;rid:21;token:'43606811c7305ccc6abb2be116579bfd'}
	 * @return
	 */
	public static Client clientRegister(String request) {
		String res = new String(Base64.decodeBase64(request));
		JSONObject json = new JSONObject(res);
		Client client = new Client();
		if (!json.has("rid")) {
			return client;
		}

		try {
			client.setRoomId(json.getInt("rid"));
		} catch (JSONException e) {
			e.printStackTrace();
			return client;
		}

		if (!json.has("userId") || !json.has("token")) {
			return client;
		}

		Long userId;
		String token;

		try {
			userId = json.getLong("userId");
			token = json.getString("token");
		} catch (JSONException e) {
			e.printStackTrace();
			return client;
		}

		if (!checkToken(userId, token)) {
			return client;
		}

		client.setUserId(userId);

		return client;
	}

	/**
	 * 从 redis 里根据 id 获取 token 与之对比
	 *
	 * @param id
	 * @param token
	 * @return
	 */
	private static boolean checkToken(Long userId, String token) {
		// TODO 从数据库中查找token是否对应
		return true;
	}
}
