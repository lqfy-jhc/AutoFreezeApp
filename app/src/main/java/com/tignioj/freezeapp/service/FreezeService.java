package com.tignioj.freezeapp.service;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.ComponentName;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.LongDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Observer;


import com.tignioj.freezeapp.MainActivity;
import com.tignioj.freezeapp.R;
import com.tignioj.freezeapp.backend.entitys.AppsCategory;
import com.tignioj.freezeapp.backend.entitys.FreezeApp;
import com.tignioj.freezeapp.backend.entitys.FreezeTasker;
import com.tignioj.freezeapp.backend.viewmodel.HomeViewModel;
import com.tignioj.freezeapp.config.MyConfig;
import com.tignioj.freezeapp.receiver.PackageReceiver;
import com.tignioj.freezeapp.receiver.ScreenReceiver;
import com.tignioj.freezeapp.uientity.ProgramLocker;
import com.tignioj.freezeapp.utils.DeviceMethod;
import com.tignioj.freezeapp.utils.MyDateUtils;

import java.util.Date;
import java.util.HashMap;
import java.util.List;


public class FreezeService extends Service {
    private static final String CHANNEL_ID = "channel_id1";

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    private boolean isServiceEnd;
    private static Handler hideMySelfHandler;

    public static Handler getHideMySelfHandler() {
        return hideMySelfHandler;
    }

    public static final int ENABLE_SELF = 0x100;
    public static final int HIDE_SELF = 0x101;


    List<FreezeTasker> freezeTaskers;
    private HomeViewModel homeViewModel;
    PackageReceiver packageReceiver;
    ScreenReceiver screenReceiver;
    ProgramLocker programLocker;
    boolean isActuallyEnable;

    public class ServiceThread extends Thread {
        HashMap<Long, Boolean> screenSchedulingMap;

        ServiceThread() {
            //初始化数据
            screenSchedulingMap = new HashMap<>();
            isActuallyEnable = DeviceMethod.getInstance(getApplicationContext()).isSelfEnable();
        }

        @Override
        public void run() {
            while (!isServiceEnd) {
                if (freezeTaskers != null) {
                    if (freezeTaskers.size() > 0) {
                        loopTasks();

                        loopHideIcon();
                    }
                }
                //五秒刷新一次
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }

        }


        private void loopHideIcon() {
            if (programLocker != null) {
                Log.d(MyConfig.LOG_TAG_FREEZE_SERVICE, programLocker.toString());


                if (!programLocker.isEnable()) {
                    return;
                }

                if (!MyDateUtils.betweenStartTimeAndEndTime(
                        MyDateUtils.parse(programLocker.getStartTime()),
                        MyDateUtils.parse(programLocker.getEndTime()))) {
                    Log.d(MyConfig.LOG_TAG_FREEZE_SERVICE, "actually Enable:" + isActuallyEnable);
                    if (isActuallyEnable && programLocker.isHideIcon()) {
                        if (hideMySelfHandler != null) {
                            hideMySelfHandler.sendEmptyMessage(HIDE_SELF);
                        }

                    }
                } else {
                    Log.d(MyConfig.LOG_TAG_FREEZE_SERVICE, "liberate:" + "actually Enable:" + isActuallyEnable);
                    if (!isActuallyEnable || !programLocker.isHideIcon()) {
                        if (hideMySelfHandler != null) {
                            hideMySelfHandler.sendEmptyMessage(ENABLE_SELF);
                        }
                    }
                }
            }
        }

        private void loopTasks() {
            Log.d(MyConfig.MY_TAG, new Date().toString() + ":循环查看任务, " + (freezeTaskers == null ? null : freezeTaskers.size()));
            for (FreezeTasker freezeTasker : freezeTaskers) {
                //只处理这时间段的App
                if (MyDateUtils.betweenStartTimeAndEndTime(freezeTasker.getStartTime(), freezeTasker.getEndTime())) {
                    processLockScreen(freezeTasker);

                    processFreezeApp(freezeTasker);
                } else {
                    processUnLockScreen(freezeTasker);
                }
            }
        }

        private void processUnLockScreen(FreezeTasker freezeTasker) {
            //如果不是该时间段的，查看是不是之前有冻结的记录
            //如果有, 说明之前灭屏了，则把广播的设为false
            Boolean aBoolean = screenSchedulingMap.get(freezeTasker.getId());
            if (aBoolean != null && aBoolean) {
                Log.d(MyConfig.MY_TAG, "out of data: unlock screen");
                screenSchedulingMap.remove(freezeTasker.getId());
                ScreenReceiver.isLockScreen = false;
            }
        }


        private void processFreezeApp(final FreezeTasker freezeTasker) {
            //如果规定冻结App
            List<FreezeApp> freezeAppsByCategory = homeViewModel.getAppsByCategory(freezeTasker.getCategoryId());

            if (freezeTasker.isFrozen()) {
                Log.d(MyConfig.MY_TAG, "lock apps");
                freezeApps(freezeAppsByCategory, homeViewModel);
            } else {
                Log.d(MyConfig.MY_TAG, "unlock apps");
                unfreezeApps(freezeAppsByCategory, homeViewModel);
            }
        }

        private void processLockScreen(FreezeTasker freezeTasker) {
            //如果规定锁屏
            if (freezeTasker.isLockScreen()) {
                //如果屏幕还没锁
                if (!ScreenReceiver.isLockScreen) {
                    Log.d(MyConfig.MY_TAG, "lock screen");
                    screenSchedulingMap.put(freezeTasker.getId(), true);
                    ScreenReceiver.isLockScreen = true;
                    ScreenReceiver.lockNow(getApplicationContext());
                }
            } else {
                //如果规定不锁屏
                Log.d(MyConfig.MY_TAG, "unlock screen");
                screenSchedulingMap.put(freezeTasker.getId(), false);
            }
        }


        private void unfreezeApps(List<FreezeApp> appsByCategory, HomeViewModel homeViewModel) {
            for (FreezeApp freezeApp : appsByCategory) {
                if (freezeApp.isFrozen()) {
                    DeviceMethod.getInstance(getApplicationContext()).
                            freeze(freezeApp.getPackageName(), false);
                    Log.d("myTag", "解冻：" + freezeApp.getAppName());
                    freezeApp.setFrozen(false);
                    homeViewModel.updateFreezeApp(freezeApp);
                }
            }
        }

        /**
         * 冻结指定App集合
         *
         * @param appsByCategory 待冻结的App
         */
        private void freezeApps(List<FreezeApp> appsByCategory, HomeViewModel homeViewModel) {

            for (FreezeApp freezeApp : appsByCategory) {
                if (!freezeApp.isFrozen()) {
                    DeviceMethod.getInstance(getApplicationContext()).
                            freeze(freezeApp.getPackageName(), true);
                    Log.d("myTag", "冻结：" + freezeApp.getAppName());
                    freezeApp.setFrozen(true);
                    homeViewModel.updateFreezeApp(freezeApp);
                }
            }
        }
    }

    private void createNotificationChannel() {
        // Create the NotificationChannel, but only on API 26+ because
        // the NotificationChannel class is new and not in the support library
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = getString(R.string.channel_name_keep_running);
            String description = getString(R.string.channel_description_keep_running);
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            // Register the channel with the system; you can't change the importance
            // or other notification behaviors after this
            NotificationManager notificationManager = getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();

        //安卓O以上需要创建Channel用来发送通知
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_lock_black_24dp)
                .setContentTitle("AutoFreezeApp Keep Running")
//                .setContentText("Much longer text that cannot fit one line...")
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);
        createNotificationChannel();


        startForeground(0x1001, builder.build());

        //注册广播
        registBroadCast();
        homeViewModel = new HomeViewModel(getApplication());

        MutableLiveData<ProgramLocker> programLockerMutableLiveData = homeViewModel.getProgramLockerMutableLiveData();
        programLockerMutableLiveData.observeForever(new Observer<ProgramLocker>() {
            @Override
            public void onChanged(ProgramLocker programLocker) {
                FreezeService.this.programLocker = programLocker;
            }
        });


        hideMySelfHandler = new Handler(
                Looper.getMainLooper()) {
            private void showMe() {
                if (isActuallyEnable) {
                    return;
                }
                Toast.makeText(getApplicationContext(), "Service:冷静结束", Toast.LENGTH_SHORT).show();
                PackageManager p = getPackageManager();
                ComponentName componentName = new ComponentName(getApplication(), MainActivity.class); // activity which is first time open in manifiest file which is declare as <category android:name="android.intent.category.LAUNCHER" />
                p.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, PackageManager.DONT_KILL_APP);
                isActuallyEnable = true;
            }

            private void hideMe() {
                if (!isActuallyEnable) {
                    return;
                }
                PackageManager p = getPackageManager();
                ComponentName componentName = new ComponentName(getApplication(), MainActivity.class); // activity which is first time open in manifiest file which is declare as <category android:name="android.intent.category.LAUNCHER" />
//                ComponentName componentName = new ComponentName("com.tignioj.freezeapp", "com.tignioj.freezeapp.MainActivity");
                p.setComponentEnabledSetting(componentName, PackageManager.COMPONENT_ENABLED_STATE_DISABLED, PackageManager.DONT_KILL_APP);
                isActuallyEnable = false;
            }
            @Override
            public void handleMessage(@NonNull Message msg) {
                super.handleMessage(msg);
                switch (msg.what) {
                    case ENABLE_SELF:
                        showMe();
                        break;
                    case HIDE_SELF:
                        hideMe();
                }
            }
        };

        LiveData<List<FreezeTasker>> allFreezeTaskerLive = homeViewModel.getAllFreezeTaskerLive();

        allFreezeTaskerLive.observeForever(new Observer<List<FreezeTasker>>() {
            @Override
            public void onChanged(List<FreezeTasker> freezeTaskers) {
                if (FreezeService.this.freezeTaskers != null) {
                    Log.d("myTag", "数据更新 从" + FreezeService.this.freezeTaskers.size() + "到 " + freezeTaskers.size());
                    //如果删掉了一个任务，必须先设置为false，否则当遍历不到被删掉的任务时，屏幕保持锁定状态
                    ScreenReceiver.isLockScreen = false;
                }
                FreezeService.this.freezeTaskers = freezeTaskers;
            }
        });

        serviceThread = new ServiceThread();
        serviceThread.start();
    }



    ServiceThread serviceThread;

    /**
     * 动态注册广播
     */
    private void registBroadCast() {
        //包安装/移除状态监听
        IntentFilter intentFilterPackage = new IntentFilter();
        intentFilterPackage.addAction(Intent.ACTION_PACKAGE_REMOVED);
        intentFilterPackage.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        intentFilterPackage.addAction(Intent.ACTION_PACKAGE_ADDED);
        intentFilterPackage.addDataScheme("package");
        packageReceiver = new PackageReceiver();
        registerReceiver(packageReceiver, intentFilterPackage);


        //屏幕开启/关闭状态监听
        IntentFilter intentFilterScreen = new IntentFilter(Intent.ACTION_SCREEN_ON);
        intentFilterScreen.addAction(Intent.ACTION_SCREEN_OFF);
        intentFilterScreen.addAction(Intent.ACTION_USER_PRESENT);
        screenReceiver = new ScreenReceiver();
        registerReceiver(screenReceiver, intentFilterScreen);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        isServiceEnd = true;
        Log.d(MyConfig.MY_TAG, "服务结束！");
        unregisterReceiver(screenReceiver);
        unregisterReceiver(packageReceiver);
    }
}
