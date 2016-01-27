package com.headroid.obbdownloader.utils;

import com.headroid.obbdownloader.callbacks.IDialogCallback;
import com.sanook.ultimateracing.R;

import android.annotation.SuppressLint;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;

public final class DownloadManagerResolver {

	private static final String DOWNLOAD_MANAGER_PACKAGE_NAME = "com.android.providers.downloads";
	private static boolean isDialogShowing = false;

	/**
	 * Resolve whether the DownloadManager is enable in current devices.
	 *
	 * @return true if DownloadManager is enable,false otherwise.
	 */
	public static boolean resolve(Context context) {
		boolean enable = resolveEnable(context);
		if (!enable) {
			if (!isDialogShowing)
				createDialog(context);
		}
		return enable;
	}

	/**
	 * Resolve whether the DownloadManager is enable in current devices.
	 *
	 * @param context
	 * @return true if DownloadManager is enable,false otherwise.
	 */
	@SuppressLint("InlinedApi")
	private static boolean resolveEnable(Context context) {
		int state = context.getPackageManager().getApplicationEnabledSetting(DOWNLOAD_MANAGER_PACKAGE_NAME);

		if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
			return !(state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
					|| state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER
					|| state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_UNTIL_USED);
		} else {
			return !(state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED
					|| state == PackageManager.COMPONENT_ENABLED_STATE_DISABLED_USER);
		}
	}

	private static void createDialog(final Context context) {
		isDialogShowing = true;
		ShowAlertDialog(context, context.getResources().getString(R.string.dialog_allow_cellular_title),
				context.getResources().getString(R.string.download_manager_disabled),
				context.getResources().getString(R.string.download_manager_disabled_open), "", new IDialogCallback() {

					@Override
					public void onPositiveClick() {
						enableDownloadManager(context);
						isDialogShowing = false;
					}

					@Override
					public void onNegativeClick() {

					}
				});
	}

	/**
	 * Start activity to enable DownloadManager in Settings.
	 */
	private static void enableDownloadManager(Context context) {
		try {
			// Open the specific App Info page:
			Intent intent = new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
			intent.setData(Uri.parse("package:" + DOWNLOAD_MANAGER_PACKAGE_NAME));
			context.startActivity(intent);
		} catch (ActivityNotFoundException e) {
			e.printStackTrace();

			// Open the generic Apps page:
			Intent intent = new Intent(android.provider.Settings.ACTION_MANAGE_APPLICATIONS_SETTINGS);
			context.startActivity(intent);
		}
	}

	private static void ShowAlertDialog(Context context, String title, String msg, String wifiSetting, String cancel,
			final IDialogCallback callback) {
		AlertDialog.Builder adb = new AlertDialog.Builder(context);
		adb.setTitle(title);
		adb.setMessage(msg);
		adb.setCancelable(false);

		if (!wifiSetting.equals("")) {
			adb.setPositiveButton(wifiSetting, new OnClickListener() {

				@Override
				public void onClick(DialogInterface paramDialogInterface, int paramInt) {
					callback.onPositiveClick();
				}
			});
		}
		if (!cancel.equals("")) {
			adb.setNegativeButton(cancel, new OnClickListener() {

				@Override
				public void onClick(DialogInterface paramDialogInterface, int paramInt) {
					callback.onNegativeClick();
				}

			});
		}
		adb.show();
	}
}
