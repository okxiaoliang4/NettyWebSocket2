package widebox.jelf.NettyWebSocket.entity;

public class Client {
	private Long userId;
	private Integer roomId;
	private String token;

	public Client() {
		userId = 0L;
		roomId = 0;
		token = null;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public Integer getRoomId() {
		return roomId;
	}

	public void setRoomId(Integer roomId) {
		this.roomId = roomId;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

}
