package com.almightybuserror.BT_Example;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.UUID;

import android.app.Activity;
import android.app.Dialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;


/*
 * Copyright (C) 2011 by Fernando Alexandre

	Permission is hereby granted, free of charge, to any person obtaining a copy
	of this software and associated documentation files (the "Software"), to deal
	in the Software without restriction, including without limitation the rights
	to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
	copies of the Software, and to permit persons to whom the Software is
	furnished to do so, subject to the following conditions:

	The above copyright notice and this permission notice shall be included in
	all copies or substantial portions of the Software.

	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
	FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
	AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
	LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
	OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
	THE SOFTWARE.
 */

/**
 * This is a Bluetooth example which can act as a client or server. 
 * Created as support material to a post in almightybuserror.com
 * 
 * As a server it will listen for any bluetooth connection and print
 * on the screen a line of text (\n delimited) received and from which device.
 * 
 * @author Fernando Alexandre
 *
 */
public class BT_Example extends Activity implements OnClickListener {

	/**
	 * Default Serial-Port UUID
	 */
	private String defaultUUID = "00001101-0000-1000-8000-00805F9B34FB";

	/**
	 * Default bluetooth adapter on the device.
	 */
	private BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

	/**
	 * String used to identify this application in the log.
	 */
	private final String TAG = BT_Example.class.getName();
	
	/**
	 * The prefix to identify devices of interest.
	 */
	private final static String PREFIX = "BT_";

	/**
	 * The Server thread.
	 */
	private AcceptThread server;

	/**
	 * Magic number used in the bluetooth enabling request.
	 */
	private final int REQ = 111;
	
	private NotificationCenter mNotificationCenter;
	
	private static final String MESSAGE_RECEIVED_INTENT = "com.almightybuserror.intent.MESSAGE_RECEIVED";

	/** Called when the activity is first created. */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		
		
		mNotificationCenter = new NotificationCenter();

		if(mBluetoothAdapter == null) {
			Log.e(TAG, "No Bluetooth Adapter available. Exiting...");
			this.finish();
		}
		
		this.registerReceiver(mNotificationCenter, new IntentFilter(MESSAGE_RECEIVED_INTENT));
		
		setHandlers();
	}

	@Override
	public void onBackPressed() {
		onPause();
	}
	
	@Override
	public void onPause() {
		server.cancel();
		
		restoreBTDeviceName();
		
		super.onPause();
	}

	@Override
	public void onClick(View v) {
		Button btn = (Button) v;

		if(btn.getId() == R.id.btn_start_server) {
			if(!mBluetoothAdapter.getName().startsWith(PREFIX))
				mBluetoothAdapter.setName(PREFIX + mBluetoothAdapter.getName());
			
			if(mBluetoothAdapter.getScanMode() != BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE)
				requestBTDiscoverable();
			
			server = new AcceptThread();
			server.start();

			btn.setEnabled(false);

			((Button) this.findViewById(R.id.btn_stop_server)).setEnabled(true);
		} else if(btn.getId() == R.id.btn_stop_server) {
			server.cancel();
			
			btn.setEnabled(false);
			((Button) this.findViewById(R.id.btn_start_server)).setEnabled(true);
			
			restoreBTDeviceName();
		}
	}

	/**
	 * Launches Discoverable Bluetooth Intent.
	 */
	public void requestBTDiscoverable() {
		Intent i = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
		i.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);

		startActivityForResult(i, REQ);

		int result = 0;

		this.onActivityResult(REQ, result, i);
		Log.i(TAG, "Bluetooth discoverability enabled");
	}

	/**
	 * Obtains the Vibrator service.
	 * @return
	 * 	Vibrator Object.
	 */
	private Vibrator getVibrator() {
		return (Vibrator) getSystemService(VIBRATOR_SERVICE);
	}
	
	/**
	 * Removes the prefix from the device name if the prefix is present.
	 */
	private void restoreBTDeviceName() {
		if(mBluetoothAdapter.getName().startsWith(PREFIX))
			mBluetoothAdapter.setName(mBluetoothAdapter.getName().substring(PREFIX.length()));
	}
	
	/**
	 * Sets the interface button handlers.
	 */
	public void setHandlers() {
		Button start_server = (Button) this.findViewById(R.id.btn_start_server);
		start_server.setOnClickListener(this);
		
		Button stop_server = (Button) this.findViewById(R.id.btn_stop_server);
		stop_server.setOnClickListener(this);
	}
	
	/**
	 * Shows a information dialog.
	 * @param message
	 * 	String resource used to define the message.
	 * @param duration
	 * 	Dialog's TTL.
	 */
	private void showInformation(String message, long duration) {
		final Dialog mDialog = new Dialog(this);

		TextView txt = new TextView(this);
		txt.setText(message);
		mDialog.setContentView(txt);
		mDialog.setTitle("Information");
		mDialog.show();

		(new Handler()).postDelayed(new Runnable() {
			public void run() {
				mDialog.dismiss();
			}}, duration); // Close dialog after delay
	}

	/**
	 * Thread that handles an incoming connection.
	 * Adapted from http://developer.android.com/guide/topics/wireless/bluetooth.html 
	 */
	class AcceptThread extends Thread {
		/**
		 * Tag that will appear in the log.
		 */
		private final String ACCEPT_TAG = AcceptThread.class.getName();

		/**
		 * The bluetooth server socket.
		 */
		private final BluetoothServerSocket mServerSocket;

		public AcceptThread() {
			BluetoothServerSocket tmp = null;
			try {
				tmp = mBluetoothAdapter.listenUsingRfcommWithServiceRecord(ACCEPT_TAG,
						UUID.fromString(defaultUUID));
			} catch (IOException e) { 
				e.printStackTrace();
			}
			mServerSocket = tmp;
		}

		public void run() {
			BluetoothSocket socket = null;
			while (true) {
				try {
					Log.i(ACCEPT_TAG, "Listening for a connection...");

					socket = mServerSocket.accept();
					Log.i(ACCEPT_TAG, "Connected to " + socket.getRemoteDevice().getName());

				} catch (IOException e) {
					break;
				}
				// If a connection was accepted
				if (socket != null) {
					// Do work to manage the connection (in a separate thread)
					try {
						// Read the incoming string.
						String buffer;
						
						DataInputStream in = new DataInputStream(socket.getInputStream());
						
						buffer = in.readUTF();
						
						Intent i = new Intent(MESSAGE_RECEIVED_INTENT);
						i.putExtra("Message", String.format("%sn From: %s", buffer, socket.getRemoteDevice().getName()));
						
						getBaseContext().sendBroadcast(i);
					} catch (IOException e) {
						Log.e(ACCEPT_TAG, "Error obtaining InputStream from socket");
						e.printStackTrace();
					}
					try {
						mServerSocket.close();
					} catch (IOException e) { }
					break;
				}
			}
		}

		/** Will cancel the listening socket, and cause the thread to finish */
		public void cancel() {
			try {
				mServerSocket.close();
			} catch (IOException e) { }
		}
	}
	
	class NotificationCenter extends BroadcastReceiver {

		@Override
		public void onReceive(Context context, Intent intent) {
			if(intent.getAction().equals(MESSAGE_RECEIVED_INTENT)) {
				showInformation(intent.getExtras().getString("Message"), 5000);
				getVibrator().vibrate(500);
			}
		}
		
	}
}