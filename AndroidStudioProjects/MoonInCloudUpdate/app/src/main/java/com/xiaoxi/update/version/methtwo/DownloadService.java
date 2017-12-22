package com.xiaoxi.update.version.methtwo;

/**
 * Created by zhouhui on 2017/12/22.
 */

import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.support.v4.app.NotificationCompat;
import android.support.v4.content.FileProvider;
import android.text.TextUtils;
import android.util.Log;
import android.widget.Toast;

import com.xiaoxi.update.BuildConfig;
import com.xiaoxi.update.R;
import com.xiaoxi.update.file.FileManager;
import com.xiaoxi.update.recevier.InstalledReceiver;
import com.xiaoxi.update.version.methtwo.coreprogress.ProgressHelper;
import com.xiaoxi.update.version.methtwo.coreprogress.ProgressUIListener;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.BufferedSink;
import okio.BufferedSource;
import okio.Okio;

/**
 * @author zhouhui
 */
public class DownloadService extends Service {
    public static final String INTENT_VERSION_NAME = "service.intent.version_name";
    public static final String INTENT_DOWNLOAD_URL = "service.intent.download_url";
    public static final String SAVE_FILE_NAME = "CMPP.apk";

    private static final int NOTIFICATION_ID = UUID.randomUUID().hashCode();

    private String mDownloadUrl;
    private String mVersionName;

    private NotificationManager mNotifyManager;
    private NotificationCompat.Builder mBuilder;

    /**
     * 安装成功的广播
     */
    private InstalledReceiver mInstalledReceiver;

    private Context mContext;


    @Override
    public void onDestroy() {
        super.onDestroy();
        // 移除通知
        mNotifyManager.cancel(NOTIFICATION_ID);
        unregisterReceiver(mInstalledReceiver);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


    @Override
    public void onCreate() {
        super.onCreate();
        mContext = this;
        initNotify();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            mVersionName = intent.getStringExtra(INTENT_VERSION_NAME);
            mDownloadUrl = intent.getStringExtra(INTENT_DOWNLOAD_URL);
            if (!TextUtils.isEmpty(mVersionName) && !TextUtils.isEmpty(mDownloadUrl)) {
                Log.i("TEST", "INTENT_VERSION_NAME---" + mVersionName);
                Log.i("TEST", "INTENT_DOWNLOAD_URL---" + mDownloadUrl);
                initNotify();
                startDownload();
            }
        }
        return super.onStartCommand(intent, flags, startId);
    }

    /**
     * 初始化通知栏
     */
    private void initNotify() {
        mNotifyManager = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        mBuilder = new NotificationCompat.Builder(this);
        mBuilder.setContentTitle(String.format("%s V%s", getString(getApplicationInfo().labelRes), mVersionName))
                //通知首次出现在通知栏，带上升动画效果的
                .setTicker("捷账宝新版本诚邀体验")
                //通常是用来表示一个后台任务
                .setOngoing(true)
                //通知产生的时间，会在通知信息里显示，一般是系统获取到的时间
                .setWhen(System.currentTimeMillis());

        //解决5.0系统通知栏白色Icon的问题
        Drawable appIcon = getAppIcon(this);
        Bitmap drawableToBitmap = null;
        if (appIcon != null) {
            drawableToBitmap = drawableToBitmap(appIcon);
        }
        if (drawableToBitmap != null) {
            mBuilder.setSmallIcon(R.mipmap.app_update_icon);
            mBuilder.setLargeIcon(drawableToBitmap);
        } else {
            mBuilder.setSmallIcon(getApplicationInfo().icon);
        }

        //设置通知栏常住(服务前台运行)
//        startForeground(NOTIFICATION_ID, mBuilder.build());

        mInstalledReceiver = new InstalledReceiver(handler);
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.intent.action.PACKAGE_ADDED");
        filter.addAction("android.intent.action.PACKAGE_REMOVED");
        filter.addDataScheme("package");
        registerReceiver(mInstalledReceiver, filter);
    }

    /**
     * 开始下载
     */
    private void startDownload() {
        OkHttpClient client = new OkHttpClient();
        Request.Builder builder = new Request.Builder();
        builder.url(mDownloadUrl);
        builder.get();
        Call call = client.newCall(builder.build());

        call.enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                e.printStackTrace();
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                ResponseBody responseBody = ProgressHelper.withProgress(response.body(), new ProgressUIListener() {
                    @Override
                    public void onUIProgressStart(long totalBytes) {
                        super.onUIProgressStart(totalBytes);
                        //开始下载
                    }

                    int oldProgress = 0;

                    @Override
                    public void onUIProgressChanged(long numBytes, long totalBytes, float percent, float speed) {
                        //下载中
                        Log.e("TAG", "=============start===============");
                        Log.e("TAG", "numBytes:" + numBytes);
                        Log.e("TAG", "totalBytes:" + totalBytes);
                        Log.e("TAG", "percent:" + percent);
                        Log.e("TAG", "speed:" + speed);
                        Log.e("TAG", "============= end ===============");

                        int progress = (int) (100 * percent);
                        // 如果进度与之前进度相等，则不更新，如果更新太频繁，否则会造成界面卡顿
                        if (progress != oldProgress) {
                            updateProgress(progress);
                        }
                        oldProgress = progress;
                    }

                    @Override
                    public void onUIProgressFinish() {
                        super.onUIProgressFinish();
                        // 下载完成
                        installAPk();
                        updateProgressCompleted();
                    }
                });

                BufferedSource source = responseBody.source();

                String apkDownLoadDir = FileManager.getApkDownLoadDir(mContext.getApplicationContext());
                File apkFile = new File(apkDownLoadDir, SAVE_FILE_NAME);

                apkFile.delete();
                apkFile.getParentFile().mkdirs();
                apkFile.createNewFile();

                BufferedSink sink = Okio.buffer(Okio.sink(apkFile));
                source.readAll(sink);
                sink.flush();
                source.close();
            }
        });
    }

    /**
     * 更新通知栏的进度(下载中)
     *
     * @param progress
     */
    private void updateProgress(int progress) {
        mBuilder.setContentText(String.format("正在下载:%1$d%%", progress)).setProgress
                (100, progress, false);
        mBuilder.setContentIntent(getDefalutIntent());
        mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
    }

    /**
     * 更新通知栏的进度(下载完成)
     */
    private void updateProgressCompleted() {
        mBuilder.setContentText("下载完成,点击安装").setProgress(100, 100, false);
        mBuilder.setContentIntent(getInstallPendingIntent());
        Notification build = mBuilder.build();
        //用户单击通知后自动消失
        build.flags = Notification.FLAG_AUTO_CANCEL;
        //只有全部清除时，Notification才会清除 ，不清楚该通知(QQ的通知无法清除，就是用的这个)
        //build.flags = Notification.FLAG_NO_CLEAR;
        mNotifyManager.notify(NOTIFICATION_ID, build);
    }

    /**
     * 获取默认的通知栏事件
     *
     * @return
     */
    public PendingIntent getDefalutIntent() {
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 1, new Intent(), PendingIntent
                .FLAG_UPDATE_CURRENT);
        return pendingIntent;
    }

    /**
     * 获取安装的通知栏事件
     *
     * @return
     */
    public PendingIntent getInstallPendingIntent() {
        // 表示相应的PendingIntent已经存在，则取消前者，然后创建新的PendingIntent，这个有利于数据保持为最新的，可以用于即时通信的通信场景
        //PendingIntent.FLAG_CANCEL_CURRENT
        // 表示更新的PendingIntent
        //PendingIntent.FLAG_UPDATE_CURRENT
        PendingIntent pendingIntent = PendingIntent.getActivity(this, 1, getInstallIntent(), PendingIntent
                .FLAG_UPDATE_CURRENT);
        return pendingIntent;
    }

    /**
     * 开始安装
     */
    private void installAPk() {
        startActivity(getInstallIntent());
        //设置通知栏常住(服务退出前台运行转后台)
        //stopForeground(true);
    }

    /**
     * 启动安装界面
     *
     * @return
     */
    private Intent getInstallIntent() {
        File apkInstallDir = FileManager.getApkInstallDir(getApplicationContext());
        Log.i("TEST", "路径---" + apkInstallDir.getAbsolutePath());
        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) {
            intent.setDataAndType(Uri.fromFile(apkInstallDir), "application/vnd.android.package-archive");
        } else {
            // 声明需要的零时权限
            intent.setFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            // 第二个参数，即第一步中配置的authorities
            Uri contentUri = FileProvider.getUriForFile(this, BuildConfig.APPLICATION_ID + ".fileProvider",
                    apkInstallDir);
            intent.setDataAndType(contentUri, "application/vnd.android.package-archive");
        }
        return intent;
    }


    /**
     * 安装成功的广播
     */
    private Handler handler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                // 安装成功
                case InstalledReceiver.INSTALL_SUCCESS:
                    // 移除通知
                    mNotifyManager.cancel(NOTIFICATION_ID);
                    // 删除安装包
                    deleteApk();
                    /*** stop service *****/
                    stopSelf();
                    break;
                default:
                    break;
            }
        }
    };

    /**
     * 安装完成后删除安装包
     */
    private void deleteApk() {
        File apkInstallDir = FileManager.getApkInstallDir(getApplicationContext());
        if (apkInstallDir != null && apkInstallDir.exists()) {
            if (apkInstallDir.delete()) {
                Toast.makeText(this, "捷账宝安装包已删除", Toast.LENGTH_LONG).show();
            } else {
                Log.i("TEST", "捷账宝安装包删除失败");
            }
        } else {
            Log.i("TEST", "安装包不存在");
        }
    }


    /**
     * 合成更新的Icon
     *
     * @param drawable
     * @return
     */
    public Bitmap drawableToBitmap(Drawable drawable) {
        Bitmap bitmap = Bitmap.createBitmap(
                drawable.getIntrinsicWidth(),
                drawable.getIntrinsicHeight(),
                drawable.getOpacity() != PixelFormat.OPAQUE ? Bitmap.Config.ARGB_8888 : Bitmap.Config.RGB_565);
        Canvas canvas = new Canvas(bitmap);
        drawable.setBounds(0, 0, drawable.getIntrinsicWidth(), drawable.getIntrinsicHeight());
        drawable.draw(canvas);
        return bitmap;
    }

    /**
     * 获取App的Icon
     *
     * @param context
     * @return
     */
    public Drawable getAppIcon(Context context) {
        try {
            return context.getPackageManager().getApplicationIcon(context.getPackageName());
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return null;
    }
}