package widebox.jelf.NettyWebSocket.test;

import com.alibaba.fastjson.util.Base64;

public class Test {

	public static void main(String[] args) {
		String jsonString = "e2lkOjE7cmlkOjI2O3Rva2VuOiI0MzYwNjgxMWM3MzA1Y2NjNmFiYjJiZTExNjU3OWJmZCJ9";
		new String(Base64.decodeFast(jsonString));
		System.out.println(new String(Base64.decodeFast(jsonString)));
	}
}
