package cn.gexu.esp8266_ctl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import android.os.Handler;
import android.util.Log;

public class TCPConn extends Thread {
	private static final String TAG = TCPConn.class.getSimpleName();
	private static String host = "140.143.161.37";
	private static int port = 7891;

	private static final int Max_Protocol_Buffer_Len = 5120;
	private byte[] protocolBuffer = new byte[Max_Protocol_Buffer_Len];
	private int protocolBufferDataLen = 0;

	private Handler responseHandle;

	private Socket sock;

	public TCPConn(Handler handle) {
		super();
		this.responseHandle = handle;
		start();
	}

	private static byte[] packMsg(byte[] msg) {
		byte[] data = new byte[msg.length + 3];
		data[0] = 0x36;
		data[1] = 0x50;
		data[2] = (byte) msg.length;
		System.arraycopy(msg, 0, data, 3, msg.length);
		return data;
	}

	private void closeSocket() {
		protocolBufferDataLen = 0;
		try {
			sock.close();
		} catch (IOException e) {
		}
	}

	private void receiveData(byte[] buf, int len) {
		if (protocolBufferDataLen + len > Max_Protocol_Buffer_Len) {
			Log.e(TAG, "数据异常，超过协议处理缓冲区长度.");
			closeSocket();
			return;
		}
		System.arraycopy(buf, 0, protocolBuffer, protocolBufferDataLen, len);
		protocolBufferDataLen += len;
		if (protocolBufferDataLen < 3) {
			// 数据不足
			return;
		}
		if (protocolBuffer[0] != 0x36 || protocolBuffer[1] != 0x50) {
			// magic head error
			closeSocket();
			return;
		}
		int dataLen = protocolBuffer[2];
		if (dataLen + 3 > protocolBufferDataLen) {
			// 数据不足
			return;
		}
		int procotoDataLen = 3 + dataLen;
		byte[] msgData = new byte[dataLen];
		System.arraycopy(protocolBuffer, 3, msgData, 0, dataLen);
		protocolBufferDataLen -= procotoDataLen;
		if (protocolBufferDataLen > 0) {
			System.arraycopy(protocolBuffer, procotoDataLen, protocolBuffer, 0,
					protocolBufferDataLen);
		}
		responseHandle.sendMessage(responseHandle.obtainMessage(
				MainActivity.WHAT_TCP_Response, msgData));
	}

	public void run() {
		Log.i(TAG, "开始连接服务器");
		byte[] buf = new byte[1024];
		boolean conn = false;
		while (!Thread.interrupted()) {
			try {
				sock = new Socket(host, port);
				Log.i(TAG, "连接到:" + host + ":" + port);
				conn = true;
				responseHandle
						.sendEmptyMessage(MainActivity.WHAT_TCP_OnConnect);
				InputStream is = sock.getInputStream();
				while (true) {
					int n = is.read(buf);
					if (n < 0) {
						break;
					}
					receiveData(buf, n);
				}
			} catch (Exception e) {
				responseHandle.sendMessage(responseHandle.obtainMessage(
						MainActivity.WHAT_Default_Err, e.getMessage()));
				Log.e(TAG, "接受数据异常", e);
			}
			if (conn) {
				responseHandle
						.sendEmptyMessage(MainActivity.WHAT_TCP_OnDisconnect);
			}
			conn = false;
			Log.i(TAG, "连接断开或异常,稍后重新连接.");
			try {
				Thread.sleep(2000);
			} catch (InterruptedException e) {
				break;
			}
		}
		Log.i(TAG, "socket读线程退出");
	}

	public void sendData(byte[] msgData) {
		try {
			if (sock != null) {
				OutputStream os = sock.getOutputStream();
				os.write(packMsg(msgData));
				os.flush();
			} else {
				responseHandle.sendMessage(responseHandle.obtainMessage(
						MainActivity.WHAT_Default_Err, "还没有socket连接"));
			}
		} catch (Exception e) {
		}
	}
}
