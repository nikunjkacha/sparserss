/**
 * Sparse rss
 *
 * Copyright (c) 2010-2013 Stefan Handschuh
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN
 * THE SOFTWARE.
 *
 */

package de.shandschuh.sparserss;

import java.util.HashSet;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences.Editor;
import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.Preference.OnPreferenceClickListener;
import android.preference.PreferenceActivity;
import android.preference.PreferenceCategory;
import android.preference.PreferenceManager;
import android.widget.Toast;
import de.shandschuh.sparserss.service.RefreshService;

public class ApplicationPreferencesActivity extends PreferenceActivity {
	private static final String TIME = "time";
	
	private static final String PACKAGE = "package";
	
	private long broadcastTime;
	
	private BroadcastReceiver pluginGuiResponseReceiver;
	
	private IntentFilter pluginGuiReponseIntentFilter;
	
	private PreferenceCategory pluginsCategory;
	
	private boolean pluginsCategoryAdded;
	
	private HashSet<String> addedPLugins;
	
	private int includeStoppedPackagesFlag;
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		if (MainTabActivity.isLightTheme(this)) {
			setTheme(R.style.Theme_Light);
		}
		super.onCreate(savedInstanceState);
		
		addedPLugins = new HashSet<String>();
		pluginGuiResponseReceiver = new BroadcastReceiver() {
			@Override
			public synchronized void onReceive(Context context, Intent intent) {
				long time = intent.getLongExtra(TIME, 0l);
				
				final String packageName = intent.getStringExtra(PACKAGE);
				
				if (time != broadcastTime && packageName != null && packageName.length() > 2 && !addedPLugins.contains(packageName)) { // broadcastTime > 0 due to the ordering
					if (pluginsCategory == null) {
						pluginsCategory = new PreferenceCategory(ApplicationPreferencesActivity.this);
						pluginsCategory.setTitle(R.string.settings_category_plugins);
					}
					if (!pluginsCategoryAdded) {
						pluginsCategoryAdded = true;
						getPreferenceScreen().addPreference(pluginsCategory);
					}
					
					Preference preference = new Preference(ApplicationPreferencesActivity.this);
					
					preference.setTitle(intent.getStringExtra("name"));
					preference.setSummary(packageName);
					
					// preference.setIntent(new Intent(packageName+".OPENCONFIG").addFlags(includeStoppedPackagesFlag)); does also work but we cannot catch exceptions there
					preference.setOnPreferenceClickListener(new OnPreferenceClickListener() {
						@Override
						public boolean onPreferenceClick(Preference preference) {
							try {
								startActivity(new Intent(packageName+".OPENCONFIG").addFlags(includeStoppedPackagesFlag));
							} catch (Exception e) {
								Toast.makeText(getApplicationContext(), R.string.error, Toast.LENGTH_LONG).show();
							}
							return true;
						}
					});
					
					addedPLugins.add(packageName);
					pluginsCategory.addPreference(preference);
				}
			}
		};
		
		pluginGuiReponseIntentFilter = new IntentFilter(Strings.ACTION_PLUGINGUIRESPONSE);
		// the receiver will be registered in the onResume method
		
		try {
			includeStoppedPackagesFlag = Intent.class.getField("FLAG_INCLUDE_STOPPED_PACKAGES").getInt(null);
		} catch (Exception e) {
			includeStoppedPackagesFlag = 0;
		}
		
		addPreferencesFromResource(R.layout.preferences);
		
		Preference preference = (Preference) findPreference(Strings.SETTINGS_REFRESHENABLED);

		preference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if (Boolean.TRUE.equals(newValue)) {
					new Thread() {
						public void run() {
							startService(new Intent(ApplicationPreferencesActivity.this, RefreshService.class));
						}
					}.start();
				} else {
					getPreferences(MODE_PRIVATE).edit().putLong(Strings.PREFERENCE_LASTSCHEDULEDREFRESH, 0).commit();
					stopService(new Intent(ApplicationPreferencesActivity.this, RefreshService.class));
				}
				return true;
			}
		});
		
		preference = (Preference) findPreference(Strings.SETTINGS_SHOWTABS);
		preference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				if (MainTabActivity.INSTANCE != null ) {
					MainTabActivity.INSTANCE.setTabWidgetVisible(Boolean.TRUE.equals(newValue));
				}
				return true;
			}
		});
		
		preference = (Preference) findPreference(Strings.SETTINGS_LIGHTTHEME);
		preference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(Preference preference, Object newValue) {
				Editor editor = PreferenceManager.getDefaultSharedPreferences(ApplicationPreferencesActivity.this).edit();
				
				editor.putBoolean(Strings.SETTINGS_LIGHTTHEME, Boolean.TRUE.equals(newValue));
				editor.commit();
				android.os.Process.killProcess(android.os.Process.myPid());
				
				// this return statement will never be reached
				return true;
			}
		});
		
		preference = (Preference) findPreference(Strings.SETTINGS_EFFICIENTFEEDPARSING);
		preference.setOnPreferenceChangeListener(new OnPreferenceChangeListener() {
			public boolean onPreferenceChange(final Preference preference, Object newValue) {
				if (newValue.equals(Boolean.FALSE)) {
					AlertDialog.Builder builder = new AlertDialog.Builder(ApplicationPreferencesActivity.this);
					
					builder.setIcon(android.R.drawable.ic_dialog_alert);
					builder.setTitle(android.R.string.dialog_alert_title);
					builder.setPositiveButton(android.R.string.ok, new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							Editor editor = PreferenceManager.getDefaultSharedPreferences(ApplicationPreferencesActivity.this).edit();
							
							editor.putBoolean(Strings.SETTINGS_EFFICIENTFEEDPARSING, Boolean.FALSE);
							editor.commit();
							((CheckBoxPreference) preference).setChecked(false);
							dialog.dismiss();
						}
					});
					builder.setNegativeButton(android.R.string.cancel, new OnClickListener() {
						public void onClick(DialogInterface dialog, int which) {
							dialog.dismiss();
						}
					});
					builder.setMessage(R.string.warning_moretraffic);
					builder.show();
					return false;
				} else {
					return true;
				}
			}
		});
		
	}
	
	@Override
	protected void onPause() {
		unregisterReceiver(pluginGuiResponseReceiver);
		addedPLugins.clear();
		if (pluginsCategory != null) {
			getPreferenceScreen().removePreference(pluginsCategory);
			pluginsCategory.removeAll();
		}
		pluginsCategoryAdded = false;
		
		super.onPause();
	}

	@Override
	protected void onResume() {
		super.onResume();
		broadcastTime = System.currentTimeMillis();
		registerReceiver(pluginGuiResponseReceiver, pluginGuiReponseIntentFilter, Strings.PERMISSION_PLUGINGUIRESONSE, null);
		
		// now we broadcast the intent to ask for plugins with the current time such that we can filter old responses
		broadcastTime = System.currentTimeMillis();
		
		sendBroadcast(new Intent(Strings.ACTION_PLUGINGUIREQUEST).addFlags(includeStoppedPackagesFlag).putExtra(TIME, broadcastTime));
	}
	
}
