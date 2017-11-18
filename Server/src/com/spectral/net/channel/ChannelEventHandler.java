package com.spectral.net.channel;

import com.google.common.base.Objects;
import com.spectral.game.World;
import com.spectral.game.entity.impl.player.Player;
import com.spectral.net.NetworkConstants;
import com.spectral.net.PlayerSession;
import com.spectral.net.SessionState;
import com.spectral.net.login.LoginDetailsMessage;
import com.spectral.net.packet.Packet;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.timeout.IdleState;
import io.netty.handler.timeout.IdleStateEvent;
import io.netty.util.ReferenceCountUtil;

/**
 * An implementation of netty's {@link SimpleChannelInboundHandler} to handle
 * all of netty's incoming events..
 * 
 * @author Professor Oak
 */
@Sharable
public final class ChannelEventHandler extends SimpleChannelInboundHandler<Object> {

	@Override
	protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
		try {

			PlayerSession session = ctx.channel().attr(NetworkConstants.SESSION_KEY).get();

			if (session == null) {
				throw new IllegalStateException("session == null");
			}

			if(msg instanceof LoginDetailsMessage) {
				session.finalizeLogin((LoginDetailsMessage)msg);
			} else if(msg instanceof Packet) {
				session.queuePacket((Packet)msg);
			}

		} catch(Exception e) {
			e.printStackTrace();
		} finally {
			ReferenceCountUtil.release(msg);
		}
	}

	@Override
	public void channelInactive(ChannelHandlerContext ctx) throws Exception {

		PlayerSession session = ctx.channel().attr(NetworkConstants.SESSION_KEY).get();

		if (session == null) {
			throw new IllegalStateException("session == null");
		}

		Player player = session.getPlayer();

		if(player == null) {
			return;
		}

		//Queue the player for logout
		if(player.getSession().getState() == SessionState.LOGGED_IN 
				|| player.getSession().getState() == SessionState.REQUESTED_LOG_OUT) {
			if(!World.getRemovePlayerQueue().contains(player)) {

				//Close all open interfaces..
				player.getPacketSender().sendInterfaceRemoval();

				//After 60 seconds, it will force a logout.
				player.getForcedLogoutTimer().start(60);

				//Add player to logout queue.
				World.getRemovePlayerQueue().add(player);
			}
		}
	}

	@Override
	public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
		if (evt instanceof IdleStateEvent) {
			IdleStateEvent event = (IdleStateEvent) evt;
			if (event.state() == IdleState.READER_IDLE) {
				ctx.channel().close();
			}
		}
	}

	@Override
	public void exceptionCaught(ChannelHandlerContext ctx, Throwable e) {
		if (!NetworkConstants.IGNORED_NETWORK_EXCEPTIONS.stream()
				.anyMatch($it -> Objects.equal($it, e.getMessage()))) {
			e.printStackTrace();
		}

		ctx.channel().close();
	}
}
