package com.headroid.obbdownloader;

import java.io.File;
import java.io.IOException;

import com.headroid.obbdownloader.interfaces.DownloadProgressInfo;
import com.headroid.obbdownloader.interfaces.IDownloadCallback;
import com.headroid.obbdownloader.utils.Constants;
import com.headroid.obbdownloader.utils.DownloadManagerResolver;
import com.headroid.obbdownloader.utils.Helpers;
import com.sanook.ultimateracing.R;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.preference.PreferenceManager;
import android.provider.Settings;

public class OBBDownloadMgr implements IDownloadCallback {
	private DownloadManager manager;
	private Context mContext;
	private long downloadID = 0;
	private IDownloadCallback mCallback;
	private long mFileSize;
	private String obbURL;

	public static int DOWNLOAD_ID_NO_RECORD = -1;
	public String PREF_DOWNLOAD_ID_KEY = "OBBDOWNLOADER_ID";
	private String notiDescription = "Downloading...";
	private String notiTitle = "Game name";
	private static int mCurrentDownloadState;

	private String DOWNLOADURL = "";
	private String DESTINATION_PATH = "";
	private String OBBFILENAME = "";

	private int downloadTryCount = 0;
	private int maxDownloadTry = 10;

	public OBBDownloadMgr(Context context, String obbURL, String notiTitle, String notiDesc, IDownloadCallback callback,
			long mFileSize) {
		mContext = context;
		this.mCallback = callback;
		this.mFileSize = mFileSize;
		this.obbURL = obbURL;
		this.notiTitle = notiTitle;
		this.notiDescription = notiDesc;
		downloadTryCount = 0;

		manager = (DownloadManager) mContext.getSystemService(Context.DOWNLOAD_SERVICE);
	}

	private synchronized boolean initPath() {
		DESTINATION_PATH = Constants.EXP_PATH + mContext.getPackageName();
		try {
			File destination = new File(DESTINATION_PATH);
			if (!destination.exists())
				destination.mkdirs();
			
			boolean statusk = destination.exists();
			
			OBBFILENAME = Helpers.getExpansionAPKFileName(mContext, true, Helpers.getVersionCode(mContext));
			DOWNLOADURL = obbURL + OBBFILENAME;
		} catch (Exception e) {
			onDownloadStateChanged(IDownloadCallback.STATE_PAUSED_SDCARD_UNAVAILABLE);
			return false;
		}
		return true;
	}

	private void deleteSavePath() {
		// remove obb folder, except first error download
		try {
			File destination = new File(Helpers.getSaveFilePath(mContext));
			deleteDirectory(destination);
		} catch (Exception e) {
		}
	}

	public static boolean deleteDirectory(File directory) {
		if (directory.exists()) {
			File[] files = directory.listFiles();
			if (null != files) {
				for (int i = 0; i < files.length; i++) {
					if (files[i].isDirectory()) {
						deleteDirectory(files[i]);
					} else {
						files[i].delete();
					}
				}
			}
		}
		return (directory.delete());
	}

	public int startDownload() throws IOException {

		onDownloadStateChanged(IDownloadCallback.STATE_CONNECTING);

		// is external storage mounted
		if (!Helpers.isExternalMediaMounted()) {
			onDownloadStateChanged(IDownloadCallback.STATE_PAUSED_SDCARD_UNAVAILABLE);
			return IDownloadCallback.STATE_PAUSED_SDCARD_UNAVAILABLE;
		}

		// check is already exist
		if (Helpers.doesFileExistByFileSize((Context) mContext, mFileSize, false)) {
			onDownloadStateChanged(IDownloadCallback.STATE_OBB_ALREADY_EXIST);
			return IDownloadCallback.STATE_OBB_ALREADY_EXIST;
		}

		// check spaces is enough
		if (!isStorageEnought(mFileSize)) {
			onDownloadStateChanged(IDownloadCallback.STATE_STORAGE_SPACE_NOT_ENOUGHT);
			return IDownloadCallback.STATE_STORAGE_SPACE_NOT_ENOUGHT;
		}

		// init
		if (!initPath()) {
			onDownloadStateChanged(IDownloadCallback.STATE_FAILED);
			return IDownloadCallback.STATE_FAILED;
		}

		downloadID = getDownloadID();
		int downloadStatus = getDownloadStatusById();

		if (DOWNLOAD_ID_NO_RECORD != downloadID) {
			switch (downloadStatus) {
			case DownloadManager.STATUS_PAUSED:
			case DownloadManager.STATUS_PENDING:
			case DownloadManager.STATUS_RUNNING:
				// check status if status equal pause, pending, running then
				// show the progress
				onDownloadStateChanged(IDownloadCallback.STATE_DOWNLOADING);
				progress();
				break;
			case DownloadManager.STATUS_FAILED:
				// if status equal succesfull or fail then remove the download
				// ID
				clearDownloadID();
				download(DownloadManager.Request.NETWORK_WIFI);
				break;
			case DownloadManager.STATUS_SUCCESSFUL:
				// is .tmp file no remove
				if (Helpers.doesFileExistByFileSizeTMP((Context) mContext, mFileSize, false)) {
					removeTMPExp();
					clearDownloadID();
					onDownloadStateChanged(IDownloadCallback.STATE_OBB_ALREADY_EXIST);
					return IDownloadCallback.STATE_OBB_ALREADY_EXIST;
				} else {
					clearDownloadID();
					download(DownloadManager.Request.NETWORK_WIFI);
				}
				break;
			default:
				break;
			}
		} else {
			download(DownloadManager.Request.NETWORK_WIFI);
		}
		onDownloadStateChanged(IDownloadCallback.STATE_IDLE);
		return IDownloadCallback.STATE_IDLE;
	}

	private boolean isStorageEnought(long fileSizeBytes) {
		try {
			long availableBytes = Helpers.getAvailableBytes(Environment.getExternalStorageDirectory());
			if (availableBytes >= fileSizeBytes) {
				return true;
			}
		} catch (Exception e) {
			e.printStackTrace();
		}

		return false;
	}

	@TargetApi(Build.VERSION_CODES.JELLY_BEAN)
	private void download(int networkFlags) {
		if (!isDeviceOnWifi() && (networkFlags == DownloadManager.Request.NETWORK_WIFI)) {
			// wifi is disabled and request download only wifi
			dialogAllowCellular();
		} else {
			deleteSavePath();

			try {
				if (DownloadManagerResolver.resolve(mContext)) {
					DownloadManager.Request request = new DownloadManager.Request(Uri.parse(DOWNLOADURL));
					request.setDescription(notiDescription);
					request.setTitle(notiTitle);
					request.allowScanningByMediaScanner();
					request.setAllowedOverRoaming(false);
					request.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE);
					request.setDestinationInExternalPublicDir(DESTINATION_PATH, OBBFILENAME + Helpers.TEMP_EXT);

					request.setAllowedNetworkTypes(networkFlags);
					if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.JELLY_BEAN) {
						request.setAllowedOverMetered(true);
					}

					downloadID = manager.enqueue(request);
					saveDownloadID(downloadID);

					onDownloadStateChanged(IDownloadCallback.STATE_DOWNLOADING);
					progress();
				}
			} catch (IllegalStateException e) {
				downloadTryCount++;
				if (downloadTryCount <= maxDownloadTry) {
					File dest = new File(DESTINATION_PATH);
					dest.mkdirs();
					download(DownloadManager.Request.NETWORK_WIFI);
				} else {
					onDownloadStateChanged(IDownloadCallback.STATE_FAILED);
				}
			} catch (Exception e) {
				onDownloadStateChanged(IDownloadCallback.STATE_FAILED);
			}
		}
	}

	@SuppressWarnings("deprecation")
	public boolean isDeviceOnWifi() {
		ConnectivityManager connManager = (ConnectivityManager) mContext.getSystemService(Context.CONNECTIVITY_SERVICE);
		NetworkInfo mWifi = connManager.getNetworkInfo(ConnectivityManager.TYPE_WIFI);
		return mWifi != null && mWifi.isConnectedOrConnecting();
	}

	private boolean dialogAllowCellular() {
		AlertDialog.Builder adb = new AlertDialog.Builder(mContext);
		adb.setTitle(mContext.getString(R.string.dialog_allow_cellular_title));
		adb.setMessage(mContext.getString(R.string.dialog_allow_cellular_msg));
		adb.setCancelable(false);
		adb.setPositiveButton(mContext.getString(R.string.dialog_allow_cellular_proceed),
				new DialogInterface.OnClickListener() {
					@Override
					public void onClick(DialogInterface dialog, int which) {
						mCallback.onDownloadStateChanged(IDownloadCallback.STATE_CELLULAR_DOWNLOADING);
						download(DownloadManager.Request.NETWORK_MOBILE | DownloadManager.Request.NETWORK_WIFI);
					}
				});
		adb.setNegativeButton(mContext.getString(R.string.dialog_allow_cellular_wifi),
				new DialogInterface.OnClickListener() {

					@Override
					public void onClick(DialogInterface dialog, int which) {
						mContext.startActivity(new Intent(Settings.ACTION_WIFI_SETTINGS));
					}
				});
		adb.show();
		return false;
	}

	private int getDownloadStatusById() {
		try {
			DownloadManager.Query q = new DownloadManager.Query();
			q.setFilterById(getDownloadID());
			Cursor cursor = manager.query(q);
			if (cursor.moveToFirst()) {
				return cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
			}
		} catch (Exception e) {
		}
		return DownloadManager.STATUS_FAILED;
	}

	private long getDownloadID() {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
		long downID = preferences.getLong(PREF_DOWNLOAD_ID_KEY, DOWNLOAD_ID_NO_RECORD);
		return downID;
	}

	private void saveDownloadID(long downloadID) {
		this.downloadID = downloadID;
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
		SharedPreferences.Editor editor = preferences.edit();
		editor.putLong(PREF_DOWNLOAD_ID_KEY, downloadID);
		editor.apply();
	}

	private void clearDownloadID() {
		// don't remove download manager, because it's file no longer.
		// manager.remove(getDownloadID());
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(mContext);
		SharedPreferences.Editor editor = preferences.edit();
		editor.putLong(PREF_DOWNLOAD_ID_KEY, DOWNLOAD_ID_NO_RECORD);
		editor.apply();
	}

	private void progress() {
		new Thread(new Runnable() {

			@Override
			public void run() {

				boolean downloading = true;

				while (downloading) {

					DownloadManager.Query q = new DownloadManager.Query();
					q.setFilterById(downloadID);

					Cursor cursor = manager.query(q);
					if (null == cursor)
						continue;

					if (cursor.moveToFirst()) {
						final int bytes_downloaded = cursor
								.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR));
						final int bytes_total = cursor
								.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES));

						if (cursor.getInt(cursor
								.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_SUCCESSFUL) {
							downloading = false;
							clearDownloadID();
							removeTMPExp();
							onDownloadStateChanged(IDownloadCallback.STATE_COMPLETED);
							break;
						} else if (cursor.getInt(cursor
								.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_FAILED) {
							downloading = false;
							clearDownloadID();
							onDownloadStateChanged(IDownloadCallback.STATE_FAILED);
							break;
						}

						if (cursor.getInt(cursor.getColumnIndex(
								DownloadManager.COLUMN_REASON)) == DownloadManager.ERROR_INSUFFICIENT_SPACE) {

							break;
						}

						int columnStatus = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_STATUS));
						switch (columnStatus) {
						case DownloadManager.STATUS_SUCCESSFUL:
							downloading = false;
							clearDownloadID();
							onDownloadStateChanged(IDownloadCallback.STATE_COMPLETED);
							break;
						case DownloadManager.STATUS_FAILED:
							downloading = false;
							clearDownloadID();
							onDownloadStateChanged(IDownloadCallback.STATE_FAILED);
							break;
						}

						int columnReason = cursor.getInt(cursor.getColumnIndex(DownloadManager.COLUMN_REASON));
						switch (columnReason) {
						case DownloadManager.ERROR_INSUFFICIENT_SPACE:
							downloading = false;
							clearDownloadID();
							onDownloadStateChanged(IDownloadCallback.STATE_STORAGE_SPACE_NOT_ENOUGHT);
							break;
						case DownloadManager.ERROR_CANNOT_RESUME:
						case DownloadManager.ERROR_DEVICE_NOT_FOUND:
						case DownloadManager.ERROR_FILE_ALREADY_EXISTS:
						case DownloadManager.ERROR_FILE_ERROR:
						case DownloadManager.ERROR_UNHANDLED_HTTP_CODE:
						case DownloadManager.ERROR_TOO_MANY_REDIRECTS:
						case DownloadManager.ERROR_UNKNOWN:
							downloading = false;
							clearDownloadID();
							onDownloadStateChanged(IDownloadCallback.STATE_FAILED);
							break;
						case DownloadManager.ERROR_HTTP_DATA_ERROR:
							downloading = false;
							clearDownloadID();
							onDownloadStateChanged(IDownloadCallback.STATE_ERROR_HTTP_DATA_ERROR);
							break;
						}

						onDownloadProgress(new DownloadProgressInfo(bytes_total, bytes_downloaded, 0, 0));
					}
					cursor.close();
				}
			}
		}).start();
	}

	protected void removeTMPExp() {
		try {
			String tempPath = Helpers.generateSaveFileNameTMP(mContext);
			File tempFile = new File(tempPath);
			if (tempFile.exists()) {
				tempFile.renameTo(new File(Helpers.generateSaveFileNameFullPath(mContext)));
			}
		} catch (Exception e) {
		}
	}

	@SuppressWarnings("unused")
	private static String statusMessage(Cursor c) {
		String msg = "???";

		mCurrentDownloadState = c.getInt(c.getColumnIndex(DownloadManager.COLUMN_STATUS));
		switch (mCurrentDownloadState) {
		case DownloadManager.STATUS_FAILED:
			msg = "Download failed!";
			break;

		case DownloadManager.STATUS_PAUSED:
			msg = "Download paused!";
			break;

		case DownloadManager.STATUS_PENDING:
			msg = "Download pending!";
			break;

		case DownloadManager.STATUS_RUNNING:
			msg = "Download in progress!";
			break;

		case DownloadManager.STATUS_SUCCESSFUL:
			msg = "Download complete!";
			break;
		default:
			msg = "Download is nowhere in sight";
			break;
		}

		return (msg);
	}

	@Override
	public void onDownloadStateChanged(int newState) {
		mCallback.onDownloadStateChanged(newState);
	}

	@Override
	public void onDownloadProgress(DownloadProgressInfo progress) {
		mCallback.onDownloadProgress(progress);
	}
}
