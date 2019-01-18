package cn.gexu.esp8266_ctl;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.Socket;

import android.os.Handler;
import android.os.Message;
import android.util.Log;

public class TCPConn extends Thread {
	private static final String TAG = TCPConn.class.getSimpleName();
	private static String host = "";
	private static int port = 0;

	private static final int Max_Protocol_Buffer_Len = 5120;
	private byte[] protocolBuffer = new byte[Max_Protocol_Buffer_Len];
	private int protocolBufferDataLen = 0;

	private Handler responseHandle;
	private int succMsgId;
	private int defaultErrMsgId;

	private Socket sock;

	public TCPConn(Handler handle, int succMsgId, int errMsgId) {
		super();
		this.responseHandle = handle;
		this.succMsgId = succMsgId;
		this.defaultErrMsgId = errMsgId;
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
		responseHandle.sendMessage(responseHandle.obtainMessage(succMsgId,
				msgData));
	}

	public void run() {
		byte[] buf = new byte[1024];
		while (!Thread.interrupted()) {
			try {
				sock = new Socket(host, port);
				Log.i(TAG, "连接到:" + host + ":" + port);
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
						defaultErrMsgId, e.getMessage()));
				Log.e(TAG, "接受数据异常", e);
			}
			Log.i(TAG, "连接断开或异常,稍后重新连接.");
		}
	}

	public void sendData(byte[] msgData) {
		try {
			if (sock != null) {
				OutputStream os = sock.getOutputStream();
				os.write(packMsg(msgData));
				os.flush();
			} else {
				responseHandle.sendMessage(responseHandle.obtainMessage(
						this.defaultErrMsgId, "还没有socket连接"));
			}
		} catch (Exception e) {
		}
	}
}
