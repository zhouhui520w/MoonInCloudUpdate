# MoonInCloudUpdate
Android下载更新(兼容7.0文件，支持5.0通知栏显示Icon)

参考文献：

1.okhttp获取下载进度：https://github.com/lizhangqu/CoreProgress

2.HttpURLConnection下载文件：https://github.com/feicien/android-auto-update

3.5.0通知栏显示Icon：https://github.com/WVector/AppUpdate

用两种方式实现了更新的效果，项目结构如下：

![](https://user-gold-cdn.xitu.io/2017/12/22/1607c8b80e5f2490?w=802&h=1194&f=jpeg&s=109154)

5.0通知栏不显示icon的解决方式：


```

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
```


7.0文件读取的解决方式：


```
1.配置文件增加provider

    <!--authorities：app的包名.fileProvider （fileProvider可以随便写）,上面采用的是gradle的替换直接写成包名也可以，但是推荐这种方式，多渠道分包的时候不用关心了-->
    <!--grantUriPermissions：必须是true，表示授予 URI 临时访问权限-->
    <!--exported：必须是false-->
    <provider
         android:name="android.support.v4.content.FileProvider"
         android:authorities="${applicationId}.fileProvider"
         android:exported="false"
         android:grantUriPermissions="true">
         <meta-data
             android:name="android.support.FILE_PROVIDER_PATHS"
             android:resource="@xml/provider_paths"/>
    </provider>
        
2.在res新建xml文件件，然后新建provider_paths.xml,copy如下代码:

    <?xml version="1.0" encoding="utf-8"?>
    <paths xmlns:android="http://schemas.android.com/apk/res/android">
        <!--external-path中，-->
        <!--name：可以随意写，只是一个名字-->
        <!--path：表示文件路径，.表示所有-->
        <external-path name="external_files" path="."/>
    </paths>

3.启动安装界面的时候

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

```

methone：第一种方式是用IntentService实现的，这种方式的好处是，下载完后，通知栏自动移除，服务自动销毁


```
/**
 * @author zhouhui
 */
public class DownloadIntentService extends IntentService {
    /**
     * 默认超时时间
     */
    private static final int DEFAULT_TIME_OUT = 10 * 1000;
    /**
     * 缓存大小
     */
    private static final int BUFFER_SIZE = 10 * 1024;

    public static final String INTENT_VERSION_NAME = "service.intent.version_name";
    public static final String INTENT_DOWNLOAD_URL = "service.intent.download_url";
    public static final String SAVE_FILE_NAME = "CMPP.apk";

    private static final int NOTIFICATION_ID = UUID.randomUUID().hashCode();

    private String mDownloadUrl;
    private String mVersionName;

    private NotificationManager mNotifyManager;
    private NotificationCompat.Builder mBuilder;


    public DownloadIntentService() {
        super("DownloadService");
    }

    /**
     * Creates an IntentService.  Invoked by your subclass's constructor.
     *
     * @param name Used to name the worker thread, important only for debugging.
     */
    public DownloadIntentService(String name) {
        super(name);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // 移除通知
        mNotifyManager.cancel(NOTIFICATION_ID);
    }


    @Override
    protected void onHandleIntent(Intent intent) {
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
    }

    /**
     * 开始下载
     */
    private void startDownload() {
        InputStream in = null;
        FileOutputStream out = null;
        try {
            URL url = new URL(mDownloadUrl);
            HttpURLConnection urlConnection = (HttpURLConnection) url.openConnection();

            urlConnection.setRequestMethod("GET");
            urlConnection.setDoOutput(false);
            urlConnection.setConnectTimeout(DEFAULT_TIME_OUT);
            urlConnection.setReadTimeout(DEFAULT_TIME_OUT);
            urlConnection.setRequestProperty("Connection", "Keep-Alive");
            urlConnection.setRequestProperty("Charset", "UTF-8");
            urlConnection.setRequestProperty("Accept-Encoding", "gzip, deflate");

            urlConnection.connect();
            long bytetotal = urlConnection.getContentLength();
            long bytesum = 0;
            int byteread = 0;
            in = urlConnection.getInputStream();
            String apkDownLoadDir = FileManager.getApkDownLoadDir(getApplicationContext());
            File apkFile = new File(apkDownLoadDir, SAVE_FILE_NAME);
            out = new FileOutputStream(apkFile);
            byte[] buffer = new byte[BUFFER_SIZE];

            int oldProgress = 0;

            while ((byteread = in.read(buffer)) != -1) {
                bytesum += byteread;
                out.write(buffer, 0, byteread);

                int progress = (int) (bytesum * 100L / bytetotal);
                // 如果进度与之前进度相等，则不更新，如果更新太频繁，否则会造成界面卡顿
                if (progress != oldProgress) {
                    updateProgress(progress);
                }
                oldProgress = progress;
            }
            // 下载完成
            installAPk();
        } catch (Exception e) {
            if (e != null) {
                Log.e("TEST", "download apk file error:" + e.getMessage());
            } else {
                Log.e("TEST", "download apk file error:下载失败");
            }
        } finally {
            if (out != null) {
                try {
                    out.close();
                } catch (IOException ignored) {

                }
            }
            if (in != null) {
                try {
                    in.close();
                } catch (IOException ignored) {

                }
            }
        }

    }

    /**
     * 更新通知栏的进度(下载中)
     *
     * @param progress
     */
    private void updateProgress(int progress) {
        mBuilder.setContentText(String.format("正在下载:%1$d%%", progress)).setProgress(100, progress, false);
        PendingIntent pendingintent = PendingIntent.getActivity(this, 0, new Intent(), PendingIntent
                .FLAG_UPDATE_CURRENT);
        mBuilder.setContentIntent(pendingintent);
        mNotifyManager.notify(NOTIFICATION_ID, mBuilder.build());
    }


    /**
     * 开始安装
     */
    private void installAPk() {
        startActivity(getInstallIntent());
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
```


methtwo：第二种方式是用Service实现的，这种方式的好处是，下载完成后可以监听安装完成后删除安装包


```
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
```
