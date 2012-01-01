/**
 * DSLR camera controller
 * 
 * @author Brannen Sorem
 * @version 0.03
 */

package com.scorgan.studiolink.v3;

import android.app.Activity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View.OnClickListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.view.View;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.io.IOException;
import java.io.OutputStream;
import java.io.InputStream;
import java.util.UUID;

import com.scorgan.studiolink.v3.R;

public class studiolink extends Activity implements OnClickListener {
    /** Called when the activity is first created. */
	
	
	static final int UPDATE_INTERVAL = 200;
	
	private BluetoothAdapter btAdapter = null;
	private BluetoothSocket  btSocket  = null;
	private BluetoothDevice  btDevice  = null;
	private OutputStream     ostream   = null;
	private InputStream		 istream   = null;
	
	private static boolean isConnected = false;
	private static String address = "";
	private byte camera = 0x00;
	
	private static byte CANON = 0x02;
	private static byte NIKON = 0x01;
	
	
	private int intervalShots;
	private int intervalDelay;
	private int timerDelay;
	
	// HANDLER objects
	private final int STRING = 1;
	private final int BUTTON = 2;
	private final int TEXT = 3;
	private final int COLOR = 4;
	
	private int colorChanged = Color.rgb(0xCC, 0x91, 0x08);  	// yellow
	private int colorWaiting = Color.rgb(0xBF, 0x00, 0x03);     // red
	private int colorConfirmed = Color.rgb(0x00, 0xBF, 0x0D);	// green
	
	private static boolean cancelActive = false;
	private static boolean intervalDelaySet = false;
	private static boolean intervalShotsSet = false;
	private static boolean timerSet = false;
	
	//private String textInfo = null;
	protected Button requestStateButton;
	protected Button shutterButton;
	protected Button connectButton;
	protected Button timerDelayButton;
	protected Button intervalDelayButton;
	protected Button intervalShotsButton;
	
	protected ToggleButton timerToggleButton;
	protected ToggleButton intervalToggleButton;
	
	protected TextView timerText;
	protected TextView intervalText;
	protected TextView intervalDelayText;
	protected TextView btStatus;
	
	protected ListView debugList;
	protected ArrayAdapter<String> debugArray;
	
	ResponseThread thread;
	
	// generic UUID for serial
	private static final UUID uuid = new UUID(0x0000110100001000L,0x800000805F9B34FBL);
	private static final int REQUEST_ENABLE_BT = 2;
	// Debug LOG info
	private String TAG = "STUDIOLINK";
	private static boolean D = true;
	
	SharedPreferences app_prefs;
	SharedPreferences.Editor pref_editor;
	
	// ===================================================================================================
	// ===================================================================================================
	// ===================================================================================================
	// ===================================================================================================
	// ===================================================================================================
	
	@Override
	public boolean onCreateOptionsMenu(Menu menu){
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);

		return true;
	}
	
	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
			case R.id.setCanonT2i:
				if (item.isChecked()) item.setChecked(false);
				else {
					item.setChecked(true);
					camera = CANON;
					debugArray.add(this.getString(R.string.camera) + "Canon T2i");
					pref_editor.putInt("camera", CANON);
					pref_editor.commit();
				}
				return true;
			case R.id.setNikonD90:
				if (item.isChecked()) item.setChecked(false);
				else {
					item.setChecked(true);
					camera = NIKON;
					debugArray.add(this.getString(R.string.camera) + "Nikon D90");
					pref_editor.putInt("camera", NIKON);
					pref_editor.commit();
				}
				return true;
			case R.id.setBranBT:
				if (item.isChecked()) item.setChecked(false);
				else {
					item.setChecked(true);
					address = this.getString(R.string.branBT);
					debugArray.add(this.getString(R.string.address) + address);
					pref_editor.putString("address", this.getString(R.string.branBT));
					pref_editor.commit();
				}
				return true;
			case R.id.setScottBT:
				if (item.isChecked()) item.setChecked(false);
				else {
					item.setChecked(true);
					address = this.getString(R.string.scottBT);
					debugArray.add(this.getString(R.string.address) + address);
					pref_editor.putString("address", this.getString(R.string.scottBT));
					pref_editor.commit();
				}
				return true;
			default:
				return super.onOptionsItemSelected(item);
		}
	}
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
    	if (D) Log.d(TAG, "onCreate");
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        requestStateButton = (Button) findViewById(R.id.reqstate);
        shutterButton = (Button) findViewById(R.id.shutterButton);
        connectButton = (Button) findViewById(R.id.connBT);
        timerDelayButton = (Button) findViewById(R.id.sendTimer);
        intervalShotsButton = (Button) findViewById(R.id.sendNumber);
        intervalDelayButton = (Button) findViewById(R.id.sendInterval);
        timerToggleButton = (ToggleButton) findViewById(R.id.togTimer);
        intervalToggleButton = (ToggleButton) findViewById(R.id.togInterval);
        timerText = (TextView) findViewById(R.id.timerInput);
        intervalText = (TextView) findViewById(R.id.numberInput);
        intervalDelayText = (TextView) findViewById(R.id.intervalInput);
        
        timerText.addTextChangedListener(new TextWatcher(){
			@Override
			public void afterTextChanged(Editable arg0) {}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				timerText.setTextColor(colorChanged);
			}
        });
        
        intervalText.addTextChangedListener(new TextWatcher(){
			@Override
			public void afterTextChanged(Editable arg0) {}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				intervalText.setTextColor(colorChanged);
			}
        });
        
        intervalDelayText.addTextChangedListener(new TextWatcher(){
			@Override
			public void afterTextChanged(Editable arg0) {}
			@Override
			public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

			@Override
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				intervalDelayText.setTextColor(colorChanged);
			}
        });
        
        debugList = (ListView) findViewById(R.id.debugList);
        debugArray = new ArrayAdapter<String>(this, R.layout.message);
        debugList.setAdapter(debugArray);
        
        btStatus = (TextView) findViewById(R.id.btStatusText);
        btAdapter = BluetoothAdapter.getDefaultAdapter();

        disableButtons();
        
        btStatus.setOnClickListener(this);
        
        requestStateButton.setOnClickListener(this);
        connectButton.setOnClickListener(this);
        shutterButton.setOnClickListener(this);
        timerDelayButton.setOnClickListener(this);
        intervalShotsButton.setOnClickListener(this);
        intervalDelayButton.setOnClickListener(this);
        
        timerToggleButton.setOnClickListener(this);
        intervalToggleButton.setOnClickListener(this);
        
        shutterButton.setText(R.string.fireShutterButton);
        
        app_prefs = PreferenceManager.getDefaultSharedPreferences(this);
        pref_editor = app_prefs.edit();
   
        BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        
        
        Integer camera_pref = app_prefs.getInt("camera", 0);
        String address_pref = app_prefs.getString("address", "");
        
        if 		(camera_pref == CANON)  camera = CANON;
    	else if (camera_pref == NIKON)  camera = NIKON;
        
        if (address_pref != null)
        	address = address_pref;
        
    }
    
	// ===================================================================================================
	// ===================================================================================================
	// ===================================================================================================
    
    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        return true;
    }
    
    public void disableButtons(){
    	// Disable buttons until connected
        shutterButton.setEnabled(false);
        timerDelayButton.setEnabled(false);
        intervalShotsButton.setEnabled(false);
        intervalDelayButton.setEnabled(false);
        timerToggleButton.setEnabled(false);
        intervalToggleButton.setEnabled(false);
        requestStateButton.setEnabled(false);
        timerText.setEnabled(false);
        intervalText.setEnabled(false);
        intervalDelayText.setEnabled(false);
    }
    
    public void enableButtons(){
    	shutterButton.setEnabled(true);
        timerDelayButton.setEnabled(true);
        intervalShotsButton.setEnabled(true);
        intervalDelayButton.setEnabled(true);
        timerToggleButton.setEnabled(true);
        intervalToggleButton.setEnabled(true);
        requestStateButton.setEnabled(true);
        timerText.setEnabled(true);
        intervalText.setEnabled(true);
        intervalDelayText.setEnabled(true);
    }

	// ===================================================================================================
	// ===================================================================================================
	// ===================================================================================================
	    
    public boolean connect(){
    	if (!isConnected) {
			try {
				Byte b = camera;
				if (D) Log.d(TAG, b.toString());
				if (camera == 0x00){  // camera not set?
					Context context = getApplicationContext();
					CharSequence text = "Please choose a camera from the Select Camera menu.";
					int duration = Toast.LENGTH_SHORT;

					Toast toast = Toast.makeText(context, text, duration);
					toast.show();
					return false;
				}
				else if (address == ""){
					Context context = getApplicationContext();
					CharSequence text = "Please choose an address from the Set Address menu.";
					int duration = Toast.LENGTH_SHORT;

					Toast toast = Toast.makeText(context, text, duration);
					toast.show();
					return false;
				}
				else { // bluetooth address and camera have been set
					if (D) Log.d(TAG, "connecting");
				
					btDevice = btAdapter.getRemoteDevice(address);
					
					// Connect to bluetooth
		        	btSocket = btDevice.createRfcommSocketToServiceRecord(uuid);
		        	btSocket.connect();
		        	
		        	// get streams
		        	ostream = btSocket.getOutputStream();
		        	istream = btSocket.getInputStream();
		        			       
		        	// update connection status and GUI
		        	isConnected = true;
		        	connectButton.setText(R.string.disconnectButton);
		        	
		        	enableButtons();
		        	
		        	debugArray.add(this.getString(R.string.connected));
		        	btStatus.setText(R.string.connected);
		        	
		        	//cameraListener();
		        	thread = new ResponseThread();
		        	thread.start();
				}
	        }
			catch(Exception e) { if (D) Log.e(TAG, e.toString()); }
		}
		else {
			disconnect();
		}
    	return isConnected;
    }

	// ===================================================================================================
	// ===================================================================================================
    
    final Handler handler = new Handler() {
    	public void handleMessage(Message msg){
    		if (D) Log.d(TAG, "Writing message");
    		switch(msg.arg1){
	    		case STRING:
	    			String str = (String) msg.obj;
	    			debugArray.add(str);
	    			break;
	    		case COLOR:
	    			TextView txt = (TextView) msg.obj;
	    			txt.setTextColor(msg.arg2);
	    			break;
	    		case BUTTON:
	    			Button btn = (Button) msg.obj;
	    			btn.setText(msg.arg2);
	    			break;
	    		case TEXT:
	    			TextView tx = (TextView) msg.obj;
	    			Integer arg1 = msg.arg2;
	    			tx.setText(arg1.toString());
	    			break;
    		}
    	}
    };
    
    private class ResponseThread extends Thread {
    	
    	public void run(){
    		while(true){
    			if (isConnected){
					Message msg = new Message();
					String response = cameraListener();
					msg.obj = response;
					msg.arg1 = STRING;
					handler.sendMessage(msg);
    			}
    		}
    	}
    }

	// ===================================================================================================
	// ===================================================================================================
    public String cameraListener(){

    	if (D) Log.d(TAG, "listening");
    	while (true){
			byte[] buff = new byte[5];
			try{
    			if (istream.available() > 0){
    				istream.read(buff, 0, 5);

    				Byte head = buff[0];
    				Byte group = buff[1];
    				Byte message = buff[2];
    				Byte data_top = buff[3];
    				Byte data_bottom = buff[4];
    				
    				Integer data = (Integer) (((Integer) (buff[3] << 8) & 0x0000FF00) + (Integer) (buff[4] & 0x000000FF));
    				
    				// Shutter Fired
    				if (head == 		0x00 &&
    					group == 		0x01 && 
    					message == 		0x01 &&
    					data_top == 	0x00 &&
    					data_bottom == 	0x02){
    					return (studiolink.this.getString(R.string.firedShutter));
   	     	 		}
    				// General Error
    				else if (head == 		0xFF &&
        					group == 		0x01 && 
        					message == 		0x01 &&
        					data_top == 	0x00 &&
        					data_bottom == 	0x00){
    					return (studiolink.this.getString(R.string.generalError));
       	     	 	}
    				// Start
    				else if (head == 		0x00 &&
        					group == 		0xFF && 
        					message == 		0xFF &&
        					data_top == 	0x00 &&
        					data_bottom == 	0x00){
    					return (studiolink.this.getString(R.string.started));
       	     	 	}
    				// Timer Success
    				else if (head == 		0x00 &&
        					group == 		0x01 && 
        					message == 		0x02 &&
        					data_top == 	0x00 &&
        					data_bottom == 	0x02){
    					cancelActive = false;
    					Message msg = new Message();
    					msg.arg2 = R.string.fireShutterTimer;
    					msg.arg1 = BUTTON;
    					msg.obj = shutterButton;
    					handler.sendMessage(msg);
    					return (studiolink.this.getString(R.string.firedTimer));
       	     	 	}
    				// Interval Started
    				else if (head == 		0x00 &&
        					group == 		0x01 && 
        					message == 		0x03 &&
        					data_top == 	0x00 &&
        					data_bottom == 	0x02){
    					return (studiolink.this.getString(R.string.intervalStarted));
       	     	 	}
    				// Interval Success
    				else if (head ==		0x00 &&
    						group ==		0x03 &&
    						message == 		0x06){
    					cancelActive = false;
    					if (D)	Log.d(TAG, "Interval succeeded");
    					Message msg = new Message();
    					msg.arg2 = R.string.fireShutterInterval;
    					msg.arg1 = BUTTON;
    					msg.obj = shutterButton;
    					handler.sendMessage(msg);
    					return (studiolink.this.getString(R.string.firedInterval));
    				}
    				// Interval Status
    				else if (head == 		0x00 &&
        					group == 		0x01 && 
        					message == 		0x04){
    					return (studiolink.this.getString(R.string.intervalStatus) + data);
       	     	 	}
    				// Stop Interval
    				else if (head == 		0x00 &&
        					group == 		0x01 && 
        					message == 		0x05 &&
        					data_top == 	0x00 &&
        					data_bottom == 	0x02){
    					return (studiolink.this.getString(R.string.firedInterval));
       	     	 	}
    				// Camera disconnected
    				else if (head == 		0x00 &&
        					group == 		0xEE && 
        					message == 		0x02 &&
        					data_top == 	0x00 &&
        					data_bottom == 	0x02){
        				disconnect();
        				return (studiolink.this.getString(R.string.cameraDisconnected));
       	     	 	}
    				// Confirm Timer Time
    				else if (head == 		0x00 &&
        					group == 		0x02 && 
        					message == 		0x02){
    					timerDelay = data;
    					timerSet = true;
    					Message msg = new Message();
    					Message msg2 = new Message();
    					// send value to text field
    					msg.arg2 = timerDelay;
    					msg.arg1 = TEXT;
    					msg.obj = timerText;
    					handler.sendMessage(msg);
    					// send color to text field
    					msg2.arg2 = colorConfirmed;
    					msg2.arg1 = COLOR;
    					msg2.obj = timerText;
    					handler.sendMessage(msg2);
    					return (studiolink.this.getString(R.string.receiveTimer) + " " + timerDelay);
       	     	 	}
    				// Confirm Interval Delay
    				else if (head == 		0x00 &&
        					group == 		0x03 && 
        					message == 		0x02){
    					intervalDelay = data;
    					intervalDelaySet = true;
    					Message msg = new Message();
    					Message msg2 = new Message();
    					// send value to text field
    					msg.arg2 = intervalDelay;
    					msg.arg1 = TEXT;
    					msg.obj = intervalDelayText;
    					handler.sendMessage(msg);
    					// send color to text field
    					msg2.arg2 = colorConfirmed;
    					msg2.arg1 = COLOR;
    					msg2.obj = intervalDelayText;
    					handler.sendMessage(msg2);
    					return (studiolink.this.getString(R.string.receiveIntervalDelay) + " " + intervalDelay);
       	     	 	}
    				// Confirm Interval Shots
    				else if (head == 		0x00 &&
        					group == 		0x03 && 
        					message == 		0x04){
    					intervalShots = data;
    					intervalShotsSet = true;
    					Message msg = new Message();
    					Message msg2 = new Message();
    					// send value to text field
    					msg.arg2 = intervalShots;
    					msg.arg1 = TEXT;
    					msg.obj = intervalText;
    					handler.sendMessage(msg);
    					// send color to text field
    					msg2.arg2 = colorConfirmed;
    					msg2.arg1 = COLOR;
    					msg2.obj = intervalText;
    					handler.sendMessage(msg2);
 
    					return (studiolink.this.getString(R.string.receiveIntervalShots) + " " + data);
       	     	 	}
    				// current interval shot
    				else if (head ==		0x00 &&
    						group == 		0x03 &&
    						message == 		0x03){
    					return (studiolink.this.getString(R.string.receiveIntervalShot) + " " + data);
    				}
    				// last interval shot
    				else if (head ==		0x00 &&
    						group == 		0x03 &&
    						message == 		0x05){
    					return (studiolink.this.getString(R.string.receiveIntervalShot) + " " + data);
    				}
    				// cancel operation successful
    				else if (head == 		0x00 &&
    						group == 		0x04 &&
    						message ==	 	0x03 &&
    						data_top == 	0x00 &&
    						data_bottom ==  0x01){
    					return studiolink.this.getString(R.string.canceled);
    				}
    				// unknown return
    				else {
    					return studiolink.this.getString(R.string.generalError);
    				}
    			}
    			try {
    				Thread.sleep(300);  // delay for time to read/parse/accept USB packet
    			}
    			catch(Exception e) { if (D) Log.d(TAG, e.toString()); }
			}
			catch(Exception e) {  }
    	}
    }
    
	// ===================================================================================================
	// ===================================================================================================
    public void disconnect(){
    	try {
    		if (D) Log.d(TAG, "Closing Streams");
			istream.close();
			ostream.close();
			btSocket.close();
			
			isConnected = false;
			
			connectButton.setText(R.string.connectButton);
			debugArray.add(this.getString(R.string.disconnected));
			btStatus.setText(R.string.disconnected);
			
			disableButtons();
		}
    	catch(Exception e) { if (D) Log.e(TAG, e.toString()); }
    }

    @Override
    public void onStart() {
        super.onStart();
        if(D) Log.d(TAG, "onStart");
    }
    
    @Override
    public void onResume() {
        super.onResume();
        if(D) Log.d(TAG, "onResume");
        connect();
    }
    
    @Override
    public void onPause() {
    	super.onPause();
    	disconnect();
    }
    
    @Override
    public void onStop() {
    	super.onStop();
    	try {
			disconnect();
		}
    	catch(Exception e) { if (D) Log.e(TAG, e.toString()); }
    }

    
	
	// ===================================================================================================
	// ===================================================================================================
    @Override
	public void onClick(View v) {
		if (v == connectButton){
			if (D) { Log.d(TAG, "connectButton"); }
			// Change connect button
			connectButton.setText(this.getString(R.string.connecting));
			btStatus.setText(R.string.connecting);
			
			boolean stat = connect();
			
			if (!stat) {
				connectButton.setText(this.getString(R.string.connectButton));
				btStatus.setText(R.string.connectButton);
			}
		}
		else if (v == shutterButton){
			if (D) Log.d(TAG, "shutterButton");
			try {
				if (cancelActive){
					byte[] output = { camera, 0x00, 0x00, 0x00, 0x00 }; // blank command to cancel interval/timer
					ostream.write(output);
					//debugArray.add(this.getString(R.string.canceled));
					if (timerToggleButton.isChecked()) shutterButton.setText(R.string.fireShutterTimer);
					else if (intervalToggleButton.isChecked()) shutterButton.setText(R.string.fireShutterInterval);
					else shutterButton.setText(R.string.fireShutterButton);
					cancelActive = false;
				}
				else {
					if (timerToggleButton.isChecked()){
						
						if (!timerSet) {
							Context context = getApplicationContext();
							CharSequence text = "Timer not set";
							int duration = Toast.LENGTH_SHORT;

							Toast toast = Toast.makeText(context, text, duration);
							toast.show();
						}
						else {
							byte[] output = { camera, 0x01, 0x02, 0x00, 0x00 };
							ostream.write(output);
							debugArray.add(this.getString(R.string.fireTimer));
							shutterButton.setText(R.string.cancelFire);
							cancelActive = true;
						}
					}
					else if (intervalToggleButton.isChecked()){
						if (!intervalDelaySet){
							Context context = getApplicationContext();
							CharSequence text = "Interval delay not set";
							int duration = Toast.LENGTH_SHORT;

							Toast toast = Toast.makeText(context, text, duration);
							toast.show();
						}
						else if (!intervalShotsSet){
							Context context = getApplicationContext();
							CharSequence text = "Interval shots not set";
							int duration = Toast.LENGTH_SHORT;

							Toast toast = Toast.makeText(context, text, duration);
							toast.show();
						}
						else {
							byte [] output = { camera, 0x01, 0x03, 0x00, 0x00 };
							ostream.write(output);
							debugArray.add(this.getString(R.string.fireInterval));
							shutterButton.setText(R.string.cancelFire);
							cancelActive = true;
						}
					}
					else {
						byte[] output = { camera, 0x01, 0x01, 0x00, 0x00 };
						ostream.write(output);
						debugArray.add(this.getString(R.string.fireShutter));
						shutterButton.setText(R.string.fireShutterButton);
					}
				}
			}
			catch(Exception e) { if (D) Log.e(TAG, e.toString()); }
		}
		else if (v == timerDelayButton){
			if (D) Log.d(TAG, "timerDelay");
			try {
				// Send value
				Integer amount = Integer.parseInt(timerText.getText().toString());
				int amnt = amount;
				if (amnt > 65535) amnt = 65535;
				byte[] output = { camera, 0x02, 0x01, (byte) (amnt >>> 8), (byte) (amnt) };
				ostream.write(output);
				debugArray.add(this.getString(R.string.sendTimer) + amnt);
				timerText.setTextColor(colorWaiting);
			}
			catch(Exception e) { if (D) Log.e(TAG, e.toString()); }
		}
		else if (v == intervalShotsButton){
			if (D) Log.d(TAG, "intervalShots");
			try {
				// Send value
				Integer amount = Integer.parseInt(intervalText.getText().toString());
				int amnt = amount;
				if (amnt > 65535) amnt = 65535;
				byte[] output = { camera, 0x03, 0x03, (byte) (amnt >>> 8), (byte) (amnt) };
				ostream.write(output);
				debugArray.add(this.getString(R.string.sendIntervalShots) + amnt);
				intervalText.setTextColor(colorWaiting);	
			}
			catch(Exception e) { if (D) Log.e(TAG, e.toString()); }
		}
		else if (v == intervalDelayButton){
			if (D) Log.d(TAG, "intervalDelay");
			try {
				// Send value
				Integer amount = Integer.parseInt(intervalDelayText.getText().toString());
				int amnt = amount;
				if (amnt > 65535) amnt = 65535;
				byte[] output = { camera, 0x03, 0x01, (byte) (amnt >>> 8), (byte) (amnt) };
				ostream.write(output);
				debugArray.add(this.getString(R.string.sendIntervalDelay) + amnt);
				intervalDelayText.setTextColor(colorWaiting);
			}
			catch(Exception e) { if (D) Log.e(TAG, e.toString()); }
		}
		else if (v == timerToggleButton){
			if (D) Log.d(TAG, "timerToggle");
			try {
				if (timerToggleButton.isChecked()) {
					intervalToggleButton.setEnabled(false);
					shutterButton.setText(R.string.fireShutterTimer);
				}
				else {
					intervalToggleButton.setEnabled(true);
					shutterButton.setText(R.string.fireShutterButton);
				}
			}
			catch(Exception e) { if (D) Log.e(TAG, e.toString()); }
		}
		else if (v == intervalToggleButton){
			if (D) Log.d(TAG, "intervalToggle");
			try {
				if (intervalToggleButton.isChecked()) {
					timerToggleButton.setEnabled(false);
					shutterButton.setText(R.string.fireShutterInterval);
				}
				else {
					timerToggleButton.setEnabled(true);
					shutterButton.setText(R.string.fireShutterButton);
				}
			}
			catch(Exception e) { if (D) Log.e(TAG, e.toString()); }
		}
		else if (v == requestStateButton){
			if (D) Log.d(TAG, "requestState");
			try {
				if (D) Log.d(TAG, "Request state");
				byte[] output = { camera, 0x04, 0x01, 0x00, 0x00 };
				ostream.write(output);
				debugArray.add(this.getString(R.string.requestState));
			}
			catch(Exception e) { if (D) Log.e(TAG, e.toString()); }
		}
		else if (v == btStatus){
			if (D) Log.d(TAG, "btStatus");
			debugArray.clear();
		}
	}
	
	
	@Override
	public void onSaveInstanceState(Bundle savedInstanceState) {
		super.onSaveInstanceState(savedInstanceState);
	}
	
	@Override
	public void onRestoreInstanceState(Bundle savedInstanceState){
		super.onRestoreInstanceState(savedInstanceState);
	}


}