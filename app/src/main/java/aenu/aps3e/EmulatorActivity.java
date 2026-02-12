// SPDX-License-Identifier: WTFPL

package aenu.aps3e;

import android.app.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.*;
import android.view.*;
import android.content.*;
import android.widget.*;
import android.preference.*;
import android.util.*;
import org.vita3k.emulator.overlay.InputOverlay.ControlId;
import android.content.res.*;

import androidx.annotation.NonNull;
import androidx.drawerlayout.widget.DrawerLayout;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.Process;
import java.util.ArrayList;
import java.util.List;

import aenu.hardware.ProcessorInfo;

//import org.libsdl.app.*;

public class EmulatorActivity extends Activity implements View.OnGenericMotionListener,SurfaceHolder.Callback
{
    //{ System.loadLibrary("e"); }

	public static final String EXTRA_META_INFO="meta_info";
	public static final String EXTRA_ISO_URI="iso_uri";
	public static final String EXTRA_GAME_DIR="game_dir";

	private static final String PREF_LAST_SESSION_ACTIVE = "last_session_active";
	private static final String PREF_LAST_SESSION_END = "last_session_end_time";
	private static final String PREF_LAST_SESSION_START = "last_session_start_time";
	private static final String PREF_LAST_GAME_SERIAL = "last_game_serial";

    static final int DELAY_ON_CREATE=0xaeae0001;
    private SparseIntArray keysMap = new SparseIntArray();
    private GameFrameView gv;
    private DrawerLayout drawerLayout;

	private Vibrator vibrator=null;
	private VibrationEffect vibrationEffect=null;
	boolean started=false;

	int open_iso_fd(String iso_uri) {
        try {
			ParcelFileDescriptor pfd_ = getContentResolver().openFileDescriptor(Uri.parse(iso_uri), "r");
			int iso_fd=pfd_.detachFd();
			pfd_.close();
			return iso_fd;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
	}

    Dialog delay_dialog=null;
	final Handler delay_on_create=new Handler(new Handler.Callback(){
		@Override
		public boolean handleMessage(@NonNull Message msg) {

            if(msg.what!=DELAY_ON_CREATE) return false;
            if(delay_dialog!=null){
                delay_dialog.dismiss();
                delay_dialog=null;
            }
			on_create();
			return true;
		}
	});

	void on_create(){
		setContentView(R.layout.emulator_view);
		drawerLayout = (DrawerLayout)findViewById(R.id.drawer_layout);
		gv=(GameFrameView)findViewById(R.id.emulator_view);

		gv.setFocusable(true);
		gv.setFocusableInTouchMode(true);
		gv.requestFocus();

		gv.setOnGenericMotionListener(EmulatorActivity.this);

		gv.getHolder().addCallback(EmulatorActivity.this);

		setup_sidebar();
		load_key_map_and_vibrator();

		Emulator.MetaInfo meta_info=null;
		//get meta info
		{
			try{
				if((meta_info=(Emulator.MetaInfo) getIntent().getSerializableExtra("meta_info"))!=null){

					if(meta_info.eboot_path==null&&meta_info.iso_uri!=null)
						meta_info.iso_fd=open_iso_fd(meta_info.iso_uri);

				}else{
					String iso_uri = getIntent().getStringExtra(EXTRA_ISO_URI);
					String game_dir = getIntent().getStringExtra(EXTRA_GAME_DIR);
					if(iso_uri!=null){
						meta_info = Emulator.get.meta_info_from_iso(open_iso_fd(iso_uri), iso_uri);
						meta_info.iso_fd=open_iso_fd(iso_uri);
					}
					if(game_dir!=null){
						meta_info = Emulator.get.meta_info_from_dir(game_dir);
					}

					if(meta_info==null)
						throw new RuntimeException("Failed to get meta info");
				}
			}catch (Exception e) {
				Toast.makeText(EmulatorActivity.this, e.getMessage(), Toast.LENGTH_SHORT).show();
			}
		}

		//setenv
		{
			File custom_cfg=Application.get_custom_cfg_file(meta_info.serial);
			if(custom_cfg.exists())
				Emulator.get.set_env("APS3E_CUSTOM_CONFIG_YAML_PATH",custom_cfg.getAbsolutePath());

			boolean enable_log=getSharedPreferences("debug",MODE_PRIVATE).getBoolean("enable_log",false);
			Emulator.get.set_env("APS3E_ENABLE_LOG",Boolean.toString(enable_log));
		}

		//setup game path
		{
			if(meta_info.eboot_path!=null)
				Emulator.get.setup_game_path(meta_info.eboot_path);
			else if(meta_info.iso_uri!=null)
				Emulator.get.setup_game_path(aenu.emulator.Emulator.Path.from(meta_info.iso_uri, meta_info.iso_fd));
			else
				throw new RuntimeException("Failed to get meta info");
		}

		Emulator.get.setup_game_id(meta_info.serial);
	}
	
	void setup_sidebar() {
		Button btnMemorySearch = (Button)findViewById(R.id.btn_memory_search);
		Button btnQuit = (Button)findViewById(R.id.btn_quit);

		btnMemorySearch.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				close_sidebar();
				show_memory_search_view();
			}
		});

		btnQuit.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				show_quit_confirmation();
			}
		});
	}
	void show_memory_search_view() {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		View view = getLayoutInflater().inflate(R.layout.memory_search_view, null);
		builder.setView(view);
		
		final AlertDialog dialog = builder.create();

		Spinner searchTypeSpinner = (Spinner)view.findViewById(R.id.search_type);
		EditText searchValueEditText = (EditText)view.findViewById(R.id.search_value);
		TextView searchResultTextView = (TextView)view.findViewById(R.id.search_result);
		ListView searchListView = (ListView)view.findViewById(R.id.search_list);
		TextView selectedAddressTextView = (TextView)view.findViewById(R.id.selected_address);
		EditText writeValueEditText = (EditText)view.findViewById(R.id.write_value);
		Button btnSearch = (Button)view.findViewById(R.id.btn_search);
		Button btnWrite = (Button)view.findViewById(R.id.btn_write);

		String[] searchTypes = {"U8", "U16", "U32"};
		ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, searchTypes);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		searchTypeSpinner.setAdapter(adapter);

		final ArrayAdapter<String> listAdapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1);
		searchListView.setAdapter(listAdapter);

		btnSearch.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String searchValueStr = searchValueEditText.getText().toString();
				if (searchValueStr.isEmpty()) {
					Toast.makeText(EmulatorActivity.this, R.string.please_enter_a_search_value, Toast.LENGTH_SHORT).show();
					return;
				}
				
				try {
					long searchValue = Utils.convert_hex_str_to_long(searchValueStr);
					int searchType = searchTypeSpinner.getSelectedItemPosition() + 1;
					String exception_msg_prefix=getString(R.string.search_value_cannot_be_greater_than);
					switch (searchType) {
					case 1:
						if(searchValue > 0xFF)
							throw new Exception(exception_msg_prefix+"0xFF");
						break;

					case 2:
						if(searchValue > 0xFFFF)
							throw new Exception(exception_msg_prefix+"0xFFFF");
						break;

					case 3:
						if(searchValue > 0xFFFFFFFFL)
							throw new Exception(exception_msg_prefix+"0xFFFFFFFF");
						break;
					}

					Emulator.CheatInfo searchInfo = new Emulator.CheatInfo();
					searchInfo.type = searchType;
					searchInfo.value = searchValue;
					Emulator.CheatInfo[] searchResults = Emulator.get.search_memory(searchInfo);
					
					if (searchResults == null || searchResults.length == 0) {
						listAdapter.clear();
						searchResultTextView.setText(R.string.search_results_0_matches);
						selectedAddressTextView.setText(R.string.please_select_the_address_to_write_to_first);
						Toast.makeText(EmulatorActivity.this, R.string.no_matching_memory_address_found, Toast.LENGTH_SHORT).show();
						return;
					}

					listAdapter.clear();
					final int RESULT_COUNT_MAX = 200;
					for (int i = 0,n=Math.min(searchResults.length, RESULT_COUNT_MAX); i < n; i++) {
						listAdapter.add(String.format("0x%08X", searchResults[i].addr));
					}
					
					searchResultTextView.setText(String.format(getString(R.string.search_results_n_matches), searchResults.length));
					Toast.makeText(EmulatorActivity.this, String.format(getString(R.string.search_results_n_matches), searchResults.length), Toast.LENGTH_SHORT).show();
					
				} catch (NumberFormatException e) {
					Toast.makeText(EmulatorActivity.this, R.string.please_enter_a_valid_number, Toast.LENGTH_SHORT).show();
				} catch (Exception e) {
					Toast.makeText(EmulatorActivity.this, getString(R.string.search_failed) + e.getMessage(), Toast.LENGTH_LONG).show();
				}
			}
		});

		searchListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
			@Override
			public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
				String selectedItem = listAdapter.getItem(position);
				if (selectedItem != null) {
					selectedAddressTextView.setText(getString(R.string.address_selected) + selectedItem);
				}
			}
		});

		btnWrite.setOnClickListener(new View.OnClickListener() {
			@Override
			public void onClick(View v) {
				String selectedAddressStr =selectedAddressTextView.getText().toString();
				if (selectedAddressStr.split("0x").length!=2) {
					Toast.makeText(EmulatorActivity.this, R.string.please_select_the_memory_address_to_write_to_first, Toast.LENGTH_SHORT).show();
					return;
				}
				selectedAddressStr = selectedAddressStr.split("0x")[1];
				
				String writeValueStr = writeValueEditText.getText().toString();
				if (writeValueStr.isEmpty()) {
					Toast.makeText(EmulatorActivity.this, R.string.please_enter_the_value_to_write, Toast.LENGTH_SHORT).show();
					return;
				}
				
				try {
					long address = Long.parseLong(selectedAddressStr.toUpperCase(), 16);
					long writeValue = Utils.convert_hex_str_to_long(writeValueStr);
					int writeType = searchTypeSpinner.getSelectedItemPosition() + 1;
					String exception_msg_prefix=getString(R.string.the_value_to_be_written_cannot_be_greater_than);

					switch (writeType) {
									case 1:
										if(writeValue > 0xFF)
											throw new Exception(exception_msg_prefix+"0xFF");
										break;

									case 2:
										if(writeValue > 0xFFFF)
											throw new Exception(exception_msg_prefix+"0xFFFF");
										break;

									case 3:
										if(writeValue > 0xFFFFFFFFL)
											throw new Exception(exception_msg_prefix+"0xFFFFFFFF");
										break;
					}
					
					Emulator.CheatInfo writeInfo = new Emulator.CheatInfo();
					writeInfo.type = writeType;
					writeInfo.addr = address;
					writeInfo.value = writeValue;

					Emulator.get.set_cheat(writeInfo);
					
					Toast.makeText(EmulatorActivity.this, getString(R.string.write_successful)+": 0x" + Long.toHexString(address) + " = " + writeValue, Toast.LENGTH_SHORT).show();
					
				} catch (Exception e) {
					Toast.makeText(EmulatorActivity.this, getString(R.string.write_failed)+": " + e.getMessage(), Toast.LENGTH_LONG).show();
				}
			}
		});
		
		dialog.show();
	}

	void show_quit_confirmation() {

		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setPositiveButton(R.string.quit, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				close_sidebar();
				show_closing_dialog();
			}
		});
		builder.setNegativeButton(android.R.string.cancel, new DialogInterface.OnClickListener() {
			@Override
			public void onClick(DialogInterface dialog, int which) {
				dialog.dismiss();
			}
		});
		builder.show();
	}
	
	void close_sidebar() {
		if (drawerLayout != null && drawerLayout.isDrawerOpen(findViewById(R.id.sidebar))) {
			drawerLayout.closeDrawer(findViewById(R.id.sidebar));
		}
	}
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

		Utils.enable_fullscreen(getWindow());

		if(!Application.should_delay_load()){
			on_create();
			return;
		}

		delay_dialog=ProgressTask.create_progress_dialog( this,getString(R.string.loading));
		delay_dialog.show();
		new Thread() {
			@Override
			public void run() {
				try {
					Thread.sleep(500);
					Emulator.load_library();
					Thread.sleep(100);
					delay_on_create.sendEmptyMessage(DELAY_ON_CREATE);
				} catch (InterruptedException e) {
					throw new RuntimeException(e);
				}
			}
		}.start();
		return;
	}

	@Override
	protected void onStart() {
		super.onStart();
	}

	void show_closing_dialog(){
		Dialog closing_dialog=ProgressTask.create_progress_dialog( this,null);
		closing_dialog.show();
		Handler handler=new Handler(new Handler.Callback() {
			@Override
			public boolean handleMessage(@NonNull Message msg) {
				closing_dialog.dismiss();
				finish();
				return true;
			}
		});

		new Thread(){
			@Override
			public void run() {
				try {
					Emulator.get.quit();
					handler.sendEmptyMessage(0);
				} catch (Exception e) {
					throw new RuntimeException(e);
				}
			}
		}.start();
	}
	@Override
	public void onBackPressed()
	{
        if(delay_dialog!=null)
            return;
		
		if (drawerLayout != null && !drawerLayout.isDrawerOpen(findViewById(R.id.sidebar))) {
			drawerLayout.openDrawer(findViewById(R.id.sidebar));
		} else if (drawerLayout != null && drawerLayout.isDrawerOpen(findViewById(R.id.sidebar))) {
			drawerLayout.closeDrawer(findViewById(R.id.sidebar));
		} else {
			show_quit_confirmation();
		}
	}
	
	@Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        int gameKey = keysMap.get(keyCode, 0);
		if (gameKey == 0) return super.onKeyDown(keyCode, event);
		if (event.getRepeatCount() == 0){
			vibrator();
			Emulator.get.key_event(gameKey, true);
            return true;
		}
		return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        int gameKey = keysMap.get(keyCode, 0);
        if (gameKey != 0) {
            Emulator.get.key_event(gameKey, false);
			return true;
        }
        return super.onKeyUp(keyCode, event);
    }

	boolean handle_dpad(InputEvent event) {

		boolean pressed=false;
		if (event instanceof MotionEvent) {

            // Use the hat axis value to find the D-pad direction
            MotionEvent motionEvent = (MotionEvent) event;
            float xaxis = motionEvent.getAxisValue(MotionEvent.AXIS_HAT_X);
            float yaxis = motionEvent.getAxisValue(MotionEvent.AXIS_HAT_Y);

            // Check if the AXIS_HAT_X value is -1 or 1, and set the D-pad
            // LEFT and RIGHT direction accordingly.
            if (Float.compare(xaxis, -1.0f) == 0) {
                Emulator.get.key_event(ControlId.l, true);
				Emulator.get.key_event(ControlId.r, false);
				vibrator();
				pressed=true;
            } else if (Float.compare(xaxis, 1.0f) == 0) {
                Emulator.get.key_event(ControlId.r, true);
				Emulator.get.key_event(ControlId.l, false);

				vibrator();
				pressed=true;
            }
            // Check if the AXIS_HAT_Y value is -1 or 1, and set the D-pad
            // UP and DOWN direction accordingly.
            if (Float.compare(yaxis, -1.0f) == 0) {
                Emulator.get.key_event(ControlId.u, true);
				Emulator.get.key_event(ControlId.d, false);

				vibrator();
				pressed=true;
            } else if (Float.compare(yaxis, 1.0f) == 0) {
                Emulator.get.key_event(ControlId.d, true);
				Emulator.get.key_event(ControlId.u, false);

				vibrator();
				pressed=true;
            }
        }
		else if (event instanceof KeyEvent) {

			// Use the key code to find the D-pad direction.
            KeyEvent keyEvent = (KeyEvent) event;
            if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_LEFT) {
                Emulator.get.key_event(ControlId.l, true);
				Emulator.get.key_event(ControlId.r, false);

				vibrator();
				pressed=true;
				
            } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_RIGHT) {
                Emulator.get.key_event(ControlId.r, true);
				Emulator.get.key_event(ControlId.l, false);

				vibrator();
				pressed=true;
				
            } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_UP) {
                Emulator.get.key_event(ControlId.u, true);
				Emulator.get.key_event(ControlId.d, false);

				vibrator();
				pressed=true;

            } else if (keyEvent.getKeyCode() == KeyEvent.KEYCODE_DPAD_DOWN) {
                Emulator.get.key_event(ControlId.d, true);
				Emulator.get.key_event(ControlId.u, false);

				vibrator();
				pressed=true;
				
            }
		}

		if(pressed) return true;
		Emulator.get.key_event(ControlId.l, false);
		Emulator.get.key_event(ControlId.u, false);
		Emulator.get.key_event(ControlId.r, false);
		Emulator.get.key_event(ControlId.d, false);
		return false;
	}


    private static boolean isDpadDevice(MotionEvent event) {
        // Check that input comes from a device with directional pads.
        if ((event.getSource() & InputDevice.SOURCE_DPAD)
			!= InputDevice.SOURCE_DPAD) {
            return true;
        } else {
            return false;
        }
    }
	
	@Override
    public boolean onGenericMotion(View v, MotionEvent event) {
		
		if(isDpadDevice(event)&& handle_dpad(event)) return true;
		
		if ((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK/*&&
			event.getAction() == MotionEvent.ACTION_MOVE*/) {
			float laxisX = event.getAxisValue(MotionEvent.AXIS_X);
			float laxisY = event.getAxisValue(MotionEvent.AXIS_Y);
			float raxisX = event.getAxisValue(MotionEvent.AXIS_Z);
			float raxisY = event.getAxisValue(MotionEvent.AXIS_RZ);

			//左摇杆
			{
				if(laxisX!=0){
					if(laxisX<0){
						Emulator.get.key_event(ControlId.lsr,false);
						Emulator.get.key_event(ControlId.lsl,true,(int)(Math.abs(laxisX)*255.0));
					}
					else{
						Emulator.get.key_event(ControlId.lsl,false);
						Emulator.get.key_event(ControlId.lsr,true,(int)(Math.abs(laxisX)*255.0));
					}
				}
				else{
					Emulator.get.key_event(ControlId.lsr,false);
					Emulator.get.key_event(ControlId.lsl,false);
				}

				if(laxisY!=0){
					if(laxisY<0){
						Emulator.get.key_event(ControlId.lsd,false);
						Emulator.get.key_event(ControlId.lsu,true,(int)(Math.abs(laxisY)*255.0));
					}else{
						Emulator.get.key_event(ControlId.lsu,false);
						Emulator.get.key_event(ControlId.lsd,true,(int)(Math.abs(laxisY)*255.0));
					}
				}
				else{
					Emulator.get.key_event(ControlId.lsd,false);
					Emulator.get.key_event(ControlId.lsu,false);
				}
			}
			//右摇杆
			{
				if(raxisX!=0){
					if(raxisX<0){
						Emulator.get.key_event(ControlId.rsr,false);
						Emulator.get.key_event(ControlId.rsl,true,(int)(Math.abs(raxisX)*255.0));
					}else{
						Emulator.get.key_event(ControlId.rsl,false);
						Emulator.get.key_event(ControlId.rsr,true,(int)(Math.abs(raxisX)*255.0));
					}
				}
				else{
					Emulator.get.key_event(ControlId.rsr,false);
					Emulator.get.key_event(ControlId.rsl,false);
				}

				if(raxisY!=0){
					if(raxisY<0){
						Emulator.get.key_event(ControlId.rsd,false);
						Emulator.get.key_event(ControlId.rsu,true,(int)(Math.abs(raxisY)*255.0));
					}else{
						Emulator.get.key_event(ControlId.rsu,false);
						Emulator.get.key_event(ControlId.rsd,true,(int)(Math.abs(raxisY)*255.0));
					}
				}
				else{
					Emulator.get.key_event(ControlId.rsd,false);
					Emulator.get.key_event(ControlId.rsu,false);
				}
			}
			return true;
		}
		
		return super.onGenericMotionEvent(event);
	}
	
	void vibrator(){
		if(vibrator!=null) {
			vibrator.vibrate(vibrationEffect);
		}
	}
	
	void load_key_map_and_vibrator() {
        final SharedPreferences sPrefs = PreferenceManager.getDefaultSharedPreferences(this);
        keysMap.clear();
        for (int i = 0; i < KeyMapConfig.KEY_IDS.length; i++) {
            String key = Integer.toString(KeyMapConfig.KEY_IDS[i]);
            int keyCode = sPrefs.getInt(key, KeyMapConfig.DEFAULT_KEYMAPPERS[i]);
            keysMap.put(keyCode, KeyMapConfig.KEY_VALUES[i]);
        }
		if(sPrefs.getBoolean("enable_vibrator",false)){
			vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
			vibrationEffect = VibrationEffect.createOneShot(25, VibrationEffect.DEFAULT_AMPLITUDE);
		}
    }

	@Override
	protected void onPause()
	{
		super.onPause();

		if(started)
			if(Emulator.get.is_running())
				Emulator.get.pause();
	}

	@Override
	protected void onResume()
	{
		super.onResume();

		if(started&&gv.getHolder().getSurface().isValid()&&Emulator.get.is_paused()){
				Emulator.get.resume();
		}

	}

	@Override
	protected void onStop() {
		super.onStop();
	}

	@Override
	protected void onDestroy()
	{
		super.onDestroy();
		SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(this);
		String serial = prefs.getString(PREF_LAST_GAME_SERIAL, null);
		long startTime = prefs.getLong(PREF_LAST_SESSION_START, 0L);
		long now = System.currentTimeMillis();
		if (serial != null && startTime > 0L && now >= startTime) {
			String playtimeKey = "playtime_ms_" + serial;
			long previous = prefs.getLong(playtimeKey, 0L);
			long delta = now - startTime;
			prefs.edit()
					.putLong(playtimeKey, previous + delta)
					.apply();
		}
		prefs.edit()
				.putBoolean(PREF_LAST_SESSION_ACTIVE, false)
				.putLong(PREF_LAST_SESSION_END, now)
				.apply();
		System.exit(0);
	}

    @Override
    public void onConfigurationChanged(Configuration newConfig)
    {
        // TODO: Implement this method
        super.onConfigurationChanged(newConfig);
    }

	@Override
	public void surfaceCreated(SurfaceHolder holder) {
		//synchronized (EmulatorActivity.class)
        {

			if (!started) {
				started = true;
				try {
					Emulator.get.setup_surface(holder.getSurface());
				} 
                finally {
					try {
						Emulator.get.boot();
					} catch (Emulator.BootException e) {
						throw new RuntimeException(e);
					}
				}

			} else {
				
					Emulator.get.setup_surface(holder.getSurface());
				
				if (Emulator.get.is_paused())
					Emulator.get.resume();
			}
		}
	}

	@Override
	public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
		if (!started) return;
		if(width==0||height==0) return;
		//if (Emulator.get.is_running()) Emulator.get.pause();
		//while (!Emulator.get.is_paused());
		Emulator.get.change_surface(width,height);
		//Emulator.get.resume();
	}

	@Override
	public void surfaceDestroyed(SurfaceHolder holder) {
		//synchronized (EmulatorActivity.class){
		if(!started)
			return;
        Emulator.get.setup_surface(null);
    //}
	}
}
