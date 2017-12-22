package com.xiaoxi.update;


import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Toast;

import com.xiaoxi.update.version.methone.service.DownloadIntentService;
import com.xiaoxi.update.version.methtwo.DownloadService;

public class MainActivity extends AppCompatActivity {
    private String mVersionName = "2.0.14";
//    private String mDownloadUrl = "http://download.sharejoy.com" +
//            ".cn/8AT54G3R/86T52EBL/86W52EBS/CMPP_Lakala_Release_2.0.14.apk";
    private String mDownloadUrl = "http://imtt.dd.qq.com/16891/2FD2E21FB312CFF4E8E5195A88FF24C1.apk?fsname=com" +
            ".ishangbin.shop_2.0.12_26.apk&csr=1bbd";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    /**
     * IntentService现在
     *
     * @param view
     */
    public void updateIntentService(View view) {
        Toast.makeText(this, "开始下载", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, DownloadIntentService.class);
        intent.putExtra(DownloadIntentService.INTENT_VERSION_NAME, mVersionName);
        intent.putExtra(DownloadIntentService.INTENT_DOWNLOAD_URL, mDownloadUrl);
        startService(intent);
    }

    /**
     * Service下载
     *
     * @param view
     */
    public void updateService(View view) {
        Toast.makeText(this, "开始下载", Toast.LENGTH_SHORT).show();
        Intent intent = new Intent(this, DownloadService.class);
        intent.putExtra(DownloadService.INTENT_VERSION_NAME, mVersionName);
        intent.putExtra(DownloadService.INTENT_DOWNLOAD_URL, mDownloadUrl);
        startService(intent);
    }


}
