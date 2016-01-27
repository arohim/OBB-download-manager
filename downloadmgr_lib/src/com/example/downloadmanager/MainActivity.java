package com.example.downloadmanager;

import java.io.IOException;

import com.headroid.obbdownloader.OBBDownloader;
import com.headroid.obbdownloader.interfaces.DownloadProgressInfo;
import com.headroid.obbdownloader.interfaces.IDownloadCallback;
import com.headroid.obbdownloader.utils.Helpers;
import com.sanook.ultimateracing.R;

import android.app.Activity;
import android.app.DownloadManager;
import android.content.Context;
import android.os.Bundle;
import android.os.StrictMode;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;
import android.widget.TextView;

public class MainActivity extends Activity implements IDownloadCallback {

	private TextView percenTage;
	private TextView totalBytes;
	private TextView progressBytes;
	private long downloadId;
	private DownloadManager manager;

	String urlDownload = "http://ultimateracing.sanook.com/obbs/main.29.com.sanook.ultimateracing.obb";

	private String obbRootURL = "http://your_obb_url_root_path.com/obbs/"; // http://obbservices.com/obbs/main.xx.your.package.com.obb 
	private long fileSize = 666600000L;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);

		Button btn = (Button) findViewById(R.id.button1);

		percenTage = (TextView) findViewById(R.id.percentage);
		totalBytes = (TextView) findViewById(R.id.total);
		progressBytes = (TextView) findViewById(R.id.byteProgress);

		btn.setOnClickListener(new OnClickListener() {

			@Override
			public void onClick(View v) {
				// check file is correct or not by check the header
				// download();
				try {
					OBBDownloader.startDownloadIfRequired(MainActivity.this, obbRootURL, "desct", "title", MainActivity.this, fileSize);
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		});

		manager = (DownloadManager) getSystemService(Context.DOWNLOAD_SERVICE);
	}

	@Override
	public void onDownloadStateChanged(int newState) {
		Log.e("statechange", newState + "");
		switch (newState) {
		case IDownloadCallback.STATE_COMPLETED:
			break;
		case IDownloadCallback.STATE_DOWNLOADING:
			break;
		case IDownloadCallback.STATE_FAILED:
			break;
		case IDownloadCallback.STATE_OBB_ALREADY_EXIST:
			break;
		case IDownloadCallback.STATE_FAILED_SDCARD_FULL:
			break;
		case IDownloadCallback.STATE_PAUSED_SDCARD_UNAVAILABLE:
			break;
		}
	}

	@Override
	public void onDownloadProgress(DownloadProgressInfo progress) {
		final int percentage = (int) ((progress.mOverallProgress * 100l) / progress.mOverallTotal);
		final String progressFraction = Helpers.getDownloadProgressString(progress.mOverallProgress,
				progress.mOverallTotal);

		Log.e("progress", percentage + ", " + progressFraction);

		runOnUiThread(new Runnable() {

			@Override
			public void run() {
				percenTage.setText(percentage + "");
				totalBytes.setText(progressFraction);
				progressBytes.setText(progressFraction);
			}
		});
	}
}
