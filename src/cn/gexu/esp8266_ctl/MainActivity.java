package cn.gexu.esp8266_ctl;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.text.SimpleDateFormat;
import java.util.Calendar;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.util.Log;
import android.util.SparseArray;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends Activity implements View.OnClickListener,
		Handler.Callback {
	private static final String LOG_TAG = "ESP8266_CTL";
	private SparseArray<byte[]> btnKeys = new SparseArray<byte[]>();

	private TextView iotList;
	private TextView show;

	// 非UI线程的handler处理机制
	private HandlerThread txThread = new HandlerThread("netThread");
	private Handler handler = new Handler(txThread.getLooper(), this);
	private TCPConn conn = new TCPConn(handler, WHAT_TCP_Response,
			WHAT_Default_Err);

	private static final int WHAT_LoadIotList = 1;
	private static final int WHAT_TCP_Request = 2;
	private static final int WHAT_TCP_Response = 3;
	private static final int WHAT_Default_Err = -1;

	private static Handler uiHandler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			Object[] objs = (Object[]) msg.obj;
			TextView view = (TextView) objs[0];
			String text = (String) objs[1];
			view.setText(text);
		}
	};

	public MainActivity() {
		btnKeys.put(R.id.btn_hall_A, new byte[] {});
		btnKeys.put(R.id.btn_hall_B, new byte[] {});
		btnKeys.put(R.id.btn_hall_C, new byte[] {});
		btnKeys.put(R.id.btn_hall_D, new byte[] {});
		btnKeys.put(R.id.btn_big_room_ON, new byte[] {});
		btnKeys.put(R.id.btn_big_room_OFF, new byte[] {});
		btnKeys.put(R.id.btn_small_room_ON, new byte[] {});
		btnKeys.put(R.id.btn_small_room_OFF, new byte[] {});
		btnKeys.put(R.id.btn_charge_ON, new byte[] {});
		btnKeys.put(R.id.btn_charge_OFF, new byte[] {});
		btnKeys.put(R.id.btn_rest_room_A, new byte[] {});
		btnKeys.put(R.id.btn_rest_room_B, new byte[] {});
		btnKeys.put(R.id.btn_rest_room_C, new byte[] {});
		btnKeys.put(R.id.btn_rest_room_D, new byte[] {});
	}

	@Override
	public boolean handleMessage(Message msg) {
		switch (msg.what) {
		case WHAT_LoadIotList:
			conn.sendData(new byte[] { 10 });
			break;
		case WHAT_TCP_Request:
			conn.sendData((byte[]) msg.obj);
			break;
		case WHAT_TCP_Response:
			byte[] buf = (byte[]) msg.obj;
			processResponse(buf);
			break;
		case WHAT_Default_Err:
			Exception e = (Exception) msg.obj;
			showTextView(show, e.getMessage());
			break;
		default:
			return false;
		}
		return true;
	}

	private void showTextView(TextView view, String text) {
		Message m = uiHandler.obtainMessage();
		m.obj = new Object[] { show, text };
		uiHandler.sendMessage(m);
	}

	@SuppressLint("SimpleDateFormat")
	private void showIotCount(byte[] buf, int offset, int len) {
		int iotCount = buf[offset];
		if ((len - 1) % 4 != 0) {
			// 数据长度不对
			showTextView(show, "iotList数据不正常");
			return;
		}
		try {
			ByteArrayInputStream bais = new ByteArrayInputStream(buf,
					offset + 1, len - 1);
			DataInputStream dis = new DataInputStream(bais);
			StringBuilder textBuf = new StringBuilder(100);
			textBuf.append("设备列表:");
			SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm");
			Calendar cal = Calendar.getInstance();
			for (int i = 0; i < iotCount; i++) {
				int ip0 = dis.read();
				int ip1 = dis.read();
				int ip2 = dis.read();
				int ip3 = dis.read();
				int port = dis.readShort();
				int ts = dis.readInt();
				cal.setTimeInMillis(ts * 1000);
				textBuf.append(ip0).append('.').append(ip1).append('.')
						.append(ip2).append('.').append(ip3).append(':')
						.append(port).append('@')
						.append(sdf.format(cal.getTime()));
			}
			showTextView(iotList, textBuf.toString());
		} catch (Exception e) {
			showTextView(iotList, "错误:" + e.getMessage());
			Log.i(LOG_TAG, "iotList出错", e);
		}
	}

	private void processCtlResponse(byte[] buf, int offset, int len) {
		if (len < 1) {
			showTextView(show, "错误:服务器响应数据没有可读内容");
			return;
		}
		int rst = buf[offset];
		showTextView(show, "数据转发结果:" + (rst == 0 ? "成功" : "失败"));
	}

	/**
	 * 处理服务器响应的内容
	 * 
	 * @param buf
	 * @param dataLen
	 */
	private void processResponse(byte[] buf) {
		int msgType = buf[0];
		int dataLen = buf.length;
		switch (msgType) {
		case 100:// hub回复ctl iot列表信息
			showIotCount(buf, 1, dataLen - 1);
			break;
		case 101:// hub回复ctl转发控制数据的结果
			processCtlResponse(buf, 1, dataLen - 1);
			break;
		default:
			showTextView(show, "不支持的响应消息类型:" + msgType);
		}
	}

	@Override
	public void onClick(View v) {
		byte[] ctl = btnKeys.get(v.getId());
		byte[] msgData = new byte[ctl.length + 1];
		msgData[0] = 11;
		System.arraycopy(ctl, 0, msgData, 1, ctl.length);
		Message msg = handler.obtainMessage(WHAT_TCP_Request, msgData);
		handler.sendMessage(msg);
	}

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		findViewById(R.id.btn_hall_A).setOnClickListener(this);
		findViewById(R.id.btn_hall_B).setOnClickListener(this);
		findViewById(R.id.btn_hall_C).setOnClickListener(this);
		findViewById(R.id.btn_hall_D).setOnClickListener(this);
		findViewById(R.id.btn_big_room_ON).setOnClickListener(this);
		findViewById(R.id.btn_big_room_OFF).setOnClickListener(this);
		findViewById(R.id.btn_small_room_ON).setOnClickListener(this);
		findViewById(R.id.btn_small_room_OFF).setOnClickListener(this);
		findViewById(R.id.btn_charge_ON).setOnClickListener(this);
		findViewById(R.id.btn_charge_OFF).setOnClickListener(this);
		findViewById(R.id.btn_rest_room_A).setOnClickListener(this);
		findViewById(R.id.btn_rest_room_B).setOnClickListener(this);
		findViewById(R.id.btn_rest_room_C).setOnClickListener(this);
		findViewById(R.id.btn_rest_room_D).setOnClickListener(this);

		iotList = (TextView) findViewById(R.id.iot_list);
		show = (TextView) findViewById(R.id.main_show);
		handler.sendEmptyMessage(WHAT_LoadIotList);
	}
}
