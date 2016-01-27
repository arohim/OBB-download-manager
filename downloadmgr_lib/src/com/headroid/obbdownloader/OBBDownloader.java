package com.headroid.obbdownloader;

import java.io.IOException;

import com.headroid.obbdownloader.interfaces.IDownloadCallback;

import android.content.Context;
import android.os.StrictMode;

public class OBBDownloader {

	private static Context mContext;
	private static IDownloadCallback mCallback;
	private static OBBDownloadMgr obbDownloadMgr;
	private static String obbURL;
	private static long mFileSize;
	private static String notiDesc;
	private static String notiTitle;

	public static void startDownloadIfRequired(Context context, String obbURL, String mNotiTitle, String mNotiDesc,
			IDownloadCallback callback, long fileSize) throws IOException {
		mContext = context;
		mCallback = callback;
		mFileSize = fileSize;
		OBBDownloader.obbURL = obbURL;
		notiDesc = mNotiDesc;
		notiTitle = mNotiTitle;

		strickNetwork();
		startDownload();
	}

	public static void startDownloadIfRequired(IDownloadCallback context) throws IOException {
		mContext = (Context) context;
		mCallback = context;
		strickNetwork();
		startDownload();
	}

	private static void strickNetwork() {
		StrictMode.ThreadPolicy policy = new StrictMode.ThreadPolicy.Builder().permitAll().build();
		StrictMode.setThreadPolicy(policy);
	}

	private static void startDownload() {
		try {
			obbDownloadMgr = new OBBDownloadMgr(mContext, OBBDownloader.obbURL, notiTitle, notiDesc, mCallback,
					mFileSize);
			obbDownloadMgr.startDownload();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
