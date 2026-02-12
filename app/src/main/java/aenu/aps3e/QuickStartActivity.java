// SPDX-License-Identifier: WTFPL
package aenu.aps3e;

import android.app.ComponentCaller;
import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.ParcelFileDescriptor;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.compose.runtime.Composer;
import androidx.compose.ui.platform.ComposeView;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import aenu.hardware.ProcessorInfo;
import kotlin.contracts.Returns;
import kotlin.Unit;
import kotlin.jvm.functions.Function2;

public class QuickStartActivity extends AppCompatActivity {

    static final String ACTION_REENTRY="aenu.intent.action.REENTRY_QUISK_START";
    static final int DELAY_ON_CREATE=0xaeae0000;

    //ProgressTask progress_task;
    Emulator.Config config;
     Dialog delay_dialog=null;

    private final MutableLiveData<Integer> refresh_tick = new MutableLiveData<>(0);
    final Handler delay_on_create=new Handler(new Handler.Callback() {
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
        if(!ACTION_REENTRY.equals(getIntent().getAction())&&Application.get_default_config_file().exists()){
            goto_main_activity();
            return;
        }

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(R.string.welcome);
        }
        setContentView(R.layout.activity_quick_start);
        MainActivity.mk_dirs();
        try{config=Emulator.Config.open_config_from_string(load_default_config_str(QuickStartActivity.this));}catch (Exception e){}

        ComposeView compose_view = findViewById(R.id.compose_view);
        QuickStartCallbacks callbacks = new QuickStartCallbacks() {
            @Override
            public void onInstallFirmware() {
                MainActivity.request_file_select(QuickStartActivity.this, MainActivity.REQUEST_INSTALL_FIRMWARE);
            }

            @Override
            public void onSelectIsoDir() {
                MainActivity.request_iso_dir_select(QuickStartActivity.this);
            }

            @Override
            public void onSelectCustomFont() {
                MainActivity.request_file_select(QuickStartActivity.this, EmulatorSettings.REQUEST_CODE_SELECT_CUSTOM_FONT);
            }

            @Override
            public void onSelectCustomDriver() {
                MainActivity.request_file_select(QuickStartActivity.this, EmulatorSettings.REQUEST_CODE_SELECT_CUSTOM_DRIVER);
            }

            @Override
            public void onFinish() {
                goto_main_activity();
            }

            @Override
            public void onQuit() {
                finish();
            }

            @Override
            public void onRefresh() {
                bump_refresh();
            }
        };

        compose_view.setContent(new Function2<Composer, Integer, Unit>() {
            @Override
            public Unit invoke(Composer composer, Integer changed) {
                QuickStartScreenKt.QuickStartScreen(QuickStartActivity.this, config, refresh_tick, callbacks, composer, 0);
                return Unit.INSTANCE;
            }
        });
    }
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if(!Application.should_delay_load()){
            on_create();
            return;
        }

        delay_dialog=ProgressTask.create_progress_dialog( this,getString(R.string.loading));
        delay_dialog.show();
        new Thread(){
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
    }

    @Override
    protected void onDestroy() {
        if(config!=null) {
            config.close_config();
            config = null;
        }

        super.onDestroy();

        if(delay_dialog!=null){
            delay_dialog.dismiss();
            delay_dialog=null;
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null) return;

        Uri uri = data.getData();

        if(uri==null) return;

        if(requestCode == MainActivity.REQUEST_SELECT_ISO_DIR){
            MainActivity.save_pref_iso_dir(this,uri);
            return;
        }

        String file_name = Utils.getFileNameFromUri(uri);

        switch (requestCode) {
            case MainActivity.REQUEST_INSTALL_FIRMWARE:
                (/*progress_task = */new ProgressTask(this)
                        .set_progress_message(getString(R.string.installing_firmware))
                        .set_failed_task(new ProgressTask.UI_Task() {
                            @Override
                            public void run() {
                                Toast.makeText(QuickStartActivity.this, getString(R.string.msg_failed), Toast.LENGTH_LONG).show();
                            }
                        })
                        .set_done_task(new ProgressTask.UI_Task() {
                                           @Override
                                           public void run() {
                                               try {
                                                   MainActivity.firmware_installed_file().createNewFile();
                                               } catch (IOException e) {
                                               }
                                               bump_refresh();
                                           }
                                       }
                        )).call(new ProgressTask.Task() {

                    @Override
                    public void run(ProgressTask task) {
                        int pfd;
                        try {
                            ParcelFileDescriptor pfd_ = getContentResolver().openFileDescriptor(uri, "r");
                            pfd = pfd_.detachFd();
                            pfd_.close();

                        } catch (Exception e) {
                            task.task_handler.sendEmptyMessage(ProgressTask.TASK_FAILED);
                            return;
                        }
                        MainActivity.firmware_installed_file().delete();
                        int result = Emulator.get.install_firmware(pfd) ? ProgressTask.TASK_DONE : ProgressTask.TASK_FAILED;
                        task.task_handler.sendEmptyMessage(result);
                    }
                });
                return;
            case EmulatorSettings.REQUEST_CODE_SELECT_CUSTOM_FONT:
                if (file_name.endsWith(".ttf") || file_name.endsWith(".ttc") || file_name.endsWith(".otf")) {
                    EmulatorSettings.install_custom_font(this,uri,(font_path)->{
                        config.save_config_entry(EmulatorSettings.Miscellaneous$Custom_Font_File_Path,font_path);
                        bump_refresh();
                    });
                }
                return;

            case EmulatorSettings.REQUEST_CODE_SELECT_CUSTOM_DRIVER:
                if (file_name.endsWith(".zip")) {
                    EmulatorSettings.install_custom_driver_from_zip(this,uri,(path)->{
                        config.save_config_entry(EmulatorSettings.Video$Vulkan$Custom_Driver_Library_Path,path);
                        bump_refresh();
                    });
                } else if (file_name.endsWith(".so")) {
                    EmulatorSettings.install_custom_driver_from_lib(this,uri,(path)->{
                        config.save_config_entry(EmulatorSettings.Video$Vulkan$Custom_Driver_Library_Path,path);
                        bump_refresh();
                    });
                }
                return;
        }
}

    public LiveData<Integer> getRefreshTick() {
        return refresh_tick;
    }

    public Emulator.Config getConfig() {
        return config;
    }

    void apply_config_fixes() {
        if (config == null) return;
        if(!Boolean.valueOf(config.load_config_entry(EmulatorSettings.Video$Vulkan$Use_Custom_Driver))){

            String gpu_driver_name=Emulator.get.get_vulkan_physical_dev_list()[0];
            if(gpu_driver_name.contains("Adreno (TM) 7")||gpu_driver_name.contains("Adreno (TM) 8")){
                config.save_config_entry(EmulatorSettings.Video$Use_BGRA_Format,"false");
                config.save_config_entry(EmulatorSettings.Video$Force_Convert_Texture,"true");
            }

            if(gpu_driver_name.contains("Adreno (TM) 7")){
                config.save_config_entry(EmulatorSettings.Video$Texture_Upload_Mode,"CPU");
            }
        }

        boolean fix_llvm_cpu_cfg=false;
        String fix_llvm_cpu_val=null;
        {
            String llvm_cpu = Emulator.get.get_native_llvm_cpu_list()[0];

            if (llvm_cpu.equals("cortex-a510")
                    || llvm_cpu.equals("cortex-a710")
                    || llvm_cpu.equals("cortex-x2")
                    || llvm_cpu.equals("cortex-a715")
                    || llvm_cpu.equals("cortex-x3")
                    || llvm_cpu.equals("cortex-a520")
                    || llvm_cpu.equals("cortex-a720")
                    || llvm_cpu.equals("cortex-a725")
                    || llvm_cpu.equals("cortex-x4")
                    || llvm_cpu.equals("cortex-a925")
            ) {
                fix_llvm_cpu_cfg = true;
                fix_llvm_cpu_val = "cortex-x1";
            }
        }

        if(fix_llvm_cpu_cfg)
            config.save_config_entry(EmulatorSettings.Core$Use_LLVM_CPU,fix_llvm_cpu_val);
    }

    private void bump_refresh() {
        Integer current = refresh_tick.getValue();
        if (current == null) current = 0;
        refresh_tick.postValue(current + 1);
    }



    void init_layout_list(){
    }

    static String load_default_config_str(Context ctx){
        return new String(Utils.load_assets_file(
                ctx,"config/config.yml"));
    }

    void goto_main_activity(){
        if(config!=null){
            String config_str=config.close_config();
            try {
                FileOutputStream default_cfg_strm = new FileOutputStream(Application.get_default_config_file());
                default_cfg_strm.write(config_str.getBytes());
                default_cfg_strm.close();
                config=null;
            }catch (Exception e){
                Toast.makeText(this,e.getMessage(),Toast.LENGTH_LONG).show();
                if(Application.get_default_config_file().exists())
                    Application.get_default_config_file().delete();
                return;
            }
        }
        startActivity(new Intent(this,MainActivity.class));
        finish();
    }
}
