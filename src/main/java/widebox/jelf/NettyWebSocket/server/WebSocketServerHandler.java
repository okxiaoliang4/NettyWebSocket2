package widebox.jelf.NettyWebSocket.server;

import static io.netty.handler.codec.http.HttpHeaderNames.HOST;
import static io.netty.handler.codec.http.HttpMethod.GET;
import static io.netty.handler.codec.http.HttpResponseStatus.BAD_REQUEST;
import static io.netty.handler.codec.http.HttpResponseStatus.FORBIDDEN;
import static io.netty.handler.codec.http.HttpResponseStatus.NOT_FOUND;
import static io.netty.handler.codec.http.HttpVersion.HTTP_1_1;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderUtil;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.websocketx.CloseWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PingWebSocketFrame;
import io.netty.handler.codec.http.websocketx.PongWebSocketFrame;
import io.netty.handler.codec.http.websocketx.TextWebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketFrame;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshaker;
import io.netty.handler.codec.http.websocketx.WebSocketServerHandshakerFactory;
import io.netty.util.CharsetUtil;
import io.netty.util.concurrent.GlobalEventExecutor;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.json.JSONObject;

import widebox.jelf.NettyWebSocket.dto.Response;
import widebox.jelf.NettyWebSocket.entity.Client;
import widebox.jelf.NettyWebSocket.service.MessageService;
import widebox.jelf.NettyWebSocket.service.RequestService;

public class WebSocketServerHandler extends SimpleChannelInboundHandler<Object> {

	// websocket 服务的 uri
	private static final String WEBSOCKET_PATH = "/websocket";
	// 登录用户表 如果用户不存在于该表,收到的消息不广播,也不入库
	private static Map<String, Client> loginClientMap = new HashMap<String, Client>();
	// 一个 ChannelGroup 代表一个直播频道
	private static Map<Integer, ChannelGroup> channelGroupMap = new HashMap<Integer, ChannelGroup>();

	// 本次请求的 code
	private static final String HTTP_REQUEST_STRING = "request";

	private Client client = null;

	private WebSocketServerHandshaker handshaker;

	/**
	 * 处理请求类型
	 */
	@Override
	public void messageReceived(ChannelHandlerContext ctx, Object msg) {
		if (msg instanceof FullHttpRequest) {
			handleHttpRequest(ctx, (FullHttpRequest) msg);
		} else if (msg instanceof WebSocketFrame) {
			handleWebSocketFrame(ctx, (WebSocketFrame) msg);
		}
	}

	@Override
	public void channelReadComplete(ChannelHandlerContext ctx) {
		ctx.flush();
	}

	/**
	 * 第一次建立连接请求为http请求，所以执行一次此方法
	 * 
	 * @param ctx
	 * @param req
	 */
	private void handleHttpRequest(ChannelHandlerContext ctx,
			FullHttpRequest req) {
		// Handle a bad request.
		if (!req.decoderResult().isSuccess()) {
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1,
					BAD_REQUEST));
			return;
		}

		// Allow only GET methods.
		if (req.method() != GET) {
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1,
					FORBIDDEN));
			return;
		}

		if ("/favicon.ico".equals(req.uri()) || ("/".equals(req.uri()))) {
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1,
					NOT_FOUND));
			return;
		}

		QueryStringDecoder queryStringDecoder = new QueryStringDecoder(
				req.uri());

		// 获取请求参数
		Map<String, List<String>> parameters = queryStringDecoder.parameters();

		// 参数不能为缺省值
		if (parameters.size() == 0
				|| !parameters.containsKey(HTTP_REQUEST_STRING)) {
			System.err.printf(HTTP_REQUEST_STRING + "参数不可缺省");
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1,
					NOT_FOUND));
			return;
		}

		// 获取参数生成客户端对象
		client = RequestService.clientRegister(parameters.get(
				HTTP_REQUEST_STRING).get(0));
		// 房间号不能为缺省值
		if (client.getRoomId() == 0) {
			System.err.printf("房间号不可缺省");
			sendHttpResponse(ctx, req, new DefaultFullHttpResponse(HTTP_1_1,
					NOT_FOUND));
			return;
		}

		// 房间列表中如果不存在则为该频道,则新增一个频道 ChannelGroup
		if (!channelGroupMap.containsKey(client.getRoomId())) {
			channelGroupMap.put(client.getRoomId(), new DefaultChannelGroup(
					GlobalEventExecutor.INSTANCE));
		}
		// 确定有房间号,才将客户端加入到频道中
		channelGroupMap.get(client.getRoomId()).add(ctx.channel());

		// Handshake
		WebSocketServerHandshakerFactory wsFactory = new WebSocketServerHandshakerFactory(
				getWebSocketLocation(req), null, true);
		handshaker = wsFactory.newHandshaker(req);
		if (handshaker == null) {
			WebSocketServerHandshakerFactory.sendUnsupportedVersionResponse(ctx
					.channel());
		} else {
			ChannelFuture channelFuture = handshaker.handshake(ctx.channel(),
					req);

			// 握手成功之后,业务逻辑 注册
			if (channelFuture.isSuccess()) {
				if (client.getUserId() == 0) {
					System.out.println(ctx.channel() + " 游客");
					return;
				}
				// 连接建立之后存入已连接的map集合中
				loginClientMap.put(ctx.channel().id().asShortText(), client);
			}
		}
	}

	/**
	 * 消息处理逻辑
	 * 
	 * @param ctx
	 * @param frame
	 */
	private void broadcast(ChannelHandlerContext ctx, WebSocketFrame frame) {
		if (!loginClientMap.containsKey(ctx.channel().id().asShortText())) {
			Response response = new Response(1001, "没登录不能聊天哦");
			String msg = new JSONObject(response).toString();
			ctx.channel().write(new TextWebSocketFrame(msg));
			return;
		}

		String request = ((TextWebSocketFrame) frame).text();
		System.out.println(" 收到 " + ctx.channel() + request);

		Response response = MessageService.sendMessage(client, request);
		String msg = new JSONObject(response).toString();
		System.out.println(msg);
		// 判断该聊天室是否存在
		if (channelGroupMap.containsKey(client.getRoomId())) {
			channelGroupMap.get(client.getRoomId()).writeAndFlush(
					new TextWebSocketFrame(msg));
		}

	}

	/**
	 * 管理socket请求
	 * 
	 * @param ctx
	 * @param frame
	 */
	private void handleWebSocketFrame(ChannelHandlerContext ctx,
			WebSocketFrame frame) {
		// 判断是否是关闭webSocket请求
		if (frame instanceof CloseWebSocketFrame) {
			handshaker.close(ctx.channel(),
					(CloseWebSocketFrame) frame.retain());
			return;
		}
		// 判断是否是ping请求
		if (frame instanceof PingWebSocketFrame) {
			ctx.channel().write(
					new PongWebSocketFrame(frame.content().retain()));
			return;
		}
		// 如果不是webSocket请求就抛出不支持该数据异常
		if (!(frame instanceof TextWebSocketFrame)) {
			throw new UnsupportedOperationException(String.format(
					"%s frame types not supported", frame.getClass().getName()));
		}

		broadcast(ctx, frame);
	}

	/**
	 * 发送HttpResponse
	 * 
	 * @param ctx
	 * @param req
	 * @param res
	 */
	private static void sendHttpResponse(ChannelHandlerContext ctx,
			FullHttpRequest req, FullHttpResponse res) {
		if (res.status().code() != 200) {
			ByteBuf buf = Unpooled.copiedBuffer(res.status().toString(),
					CharsetUtil.UTF_8);
			res.content().writeBytes(buf);
			buf.release();
			HttpHeaderUtil.setContentLength(res, res.content().readableBytes());
		}

		ChannelFuture f = ctx.channel().writeAndFlush(res);
		if (!HttpHeaderUtil.isKeepAlive(req) || res.status().code() != 200) {
			f.addListener(ChannelFutureListener.CLOSE);
		}
	}

	/**
	 * 异常处理
	 */
	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
		cause.printStackTrace();
		ctx.close();
	}

	/**
	 * 添加用户连接
	 */
	@Override
	public void handlerAdded(ChannelHandlerContext ctx) throws Exception {
		Channel incoming = ctx.channel();
		System.out.println("收到" + incoming.remoteAddress() + " 握手请求");
	}

	/**
	 * 删除用户连接
	 */
	@Override
	public void handlerRemoved(ChannelHandlerContext ctx) throws Exception {
		String channelId = ctx.channel().id().asShortText();

		if (loginClientMap.containsKey(channelId)) {
			loginClientMap.remove(channelId);
		}

		if (client != null && channelGroupMap.containsKey(client.getRoomId())) {
			channelGroupMap.get(client.getRoomId()).remove(ctx.channel());
		}

	}

	/**
	 * 获取服务器Socket地址
	 * 
	 * @param req
	 * @return
	 */
	private static String getWebSocketLocation(FullHttpRequest req) {
		String location = req.headers().get(HOST) + WEBSOCKET_PATH;
		return "ws://" + location;
	}
}