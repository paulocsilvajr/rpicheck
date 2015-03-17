/**
 * Copyright (C) 2015  RasPi Check Contributors
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along
 * with this program; if not, write to the Free Software Foundation, Inc.,
 * 51 Franklin Street, Fifth Floor, Boston, MA 02110-1301 USA.
 */
package de.eidottermihi.raspicheck;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.BaseColumns;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.Toast;

import com.google.common.base.Strings;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import de.eidottermihi.rpicheck.activity.NewRaspiAuthActivity;
import de.eidottermihi.rpicheck.adapter.DeviceSpinnerAdapter;
import de.eidottermihi.rpicheck.db.DeviceDbHelper;
import de.eidottermihi.rpicheck.db.RaspberryDeviceBean;
import de.fhconfig.android.library.injection.annotation.XmlLayout;
import de.fhconfig.android.library.injection.annotation.XmlMenu;
import de.fhconfig.android.library.injection.annotation.XmlView;
import de.fhconfig.android.library.ui.injection.InjectionActionBarActivity;


/**
 * The configuration screen for the {@link OverclockingWidget OverclockingWidget} AppWidget.
 */
@XmlLayout(R.layout.overclocking_widget_configure)
@XmlMenu(R.menu.activity_overclocking_widget_configure)
public class OverclockingWidgetConfigureActivity extends InjectionActionBarActivity implements AdapterView.OnItemSelectedListener {

    public static final String PREF_SHOW_TEMP_SUFFIX = "_temp";
    public static final String PREF_SHOW_ARM_SUFFIX = "_arm";
    public static final String PREF_SHOW_LOAD_SUFFIX = "_load";
    public static final String PREF_SHOW_MEMORY_SUFFIX = "_memory";
    private static final Logger LOGGER = LoggerFactory.getLogger(OverclockingWidgetConfigureActivity.class);
    private static final String PREFS_NAME = "de.eidottermihi.raspicheck.OverclockingWidget";
    private static final String PREF_PREFIX_KEY = "appwidget_";
    private static final String PREF_UPDATE_INTERVAL_SUFFIX = "_interval";
    int mAppWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    @XmlView(R.id.widgetPiSpinner)
    private Spinner widgetPiSpinner;
    @XmlView(R.id.textEditUpdateInterval)
    private EditText textEditUpdateInterval;
    @XmlView(R.id.widgetUpdateSpinner)
    private Spinner widgetUpdateSpinner;
    @XmlView(R.id.linLayoutCustomUpdateInterval)
    private LinearLayout linLayoutCustomInterval;
    @XmlView(R.id.checkBoxArm)
    private CheckBox checkBoxArm;
    @XmlView(R.id.checkBoxLoad)
    private CheckBox checkBoxLoad;
    @XmlView(R.id.checkBoxTemp)
    private CheckBox checkBoxTemp;
    @XmlView(R.id.checkBoxRam)
    private CheckBox checkBoxRam;

    private DeviceDbHelper deviceDbHelper;

    private int[] updateIntervalsMinutes;

    public OverclockingWidgetConfigureActivity() {
        super();
    }

    /**
     * @param context
     * @param appWidgetId
     * @param deviceId    ID of the chosen device
     */
    static void saveChosenDevicePref(Context context, int appWidgetId, Long deviceId, int updateInterval, boolean showTemp, boolean showArm, boolean showLoad, boolean showMemory) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.putLong(PREF_PREFIX_KEY + appWidgetId, deviceId);
        prefs.putInt(prefKey(PREF_UPDATE_INTERVAL_SUFFIX, appWidgetId), updateInterval);
        prefs.putBoolean(prefKey(PREF_SHOW_TEMP_SUFFIX, appWidgetId), showTemp);
        prefs.putBoolean(prefKey(PREF_SHOW_ARM_SUFFIX, appWidgetId), showArm);
        prefs.putBoolean(prefKey(PREF_SHOW_LOAD_SUFFIX, appWidgetId), showLoad);
        prefs.putBoolean(prefKey(PREF_SHOW_MEMORY_SUFFIX, appWidgetId), showMemory);
        prefs.commit();
    }

    static String prefKey(String key, int appWidgetId) {
        return PREF_PREFIX_KEY + appWidgetId + key;
    }

    static boolean loadShowStatus(Context context, String key, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getBoolean(prefKey(key, appWidgetId), true);
    }

    static Long loadDeviceId(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        Long deviceId = prefs.getLong(PREF_PREFIX_KEY + appWidgetId, 0);
        if (deviceId != 0) {
            return deviceId;
        } else {
            return null;
        }
    }

    static Integer loadUpdateInterval(Context context, int appWidgetId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, 0);
        return prefs.getInt(prefKey(PREF_UPDATE_INTERVAL_SUFFIX, appWidgetId), Integer.parseInt(context.getString(R.string.default_update_interval)));
    }

    static void deleteDevicePref(Context context, int appWidgetId) {
        SharedPreferences.Editor prefs = context.getSharedPreferences(PREFS_NAME, 0).edit();
        prefs.remove(PREF_PREFIX_KEY + appWidgetId);
        prefs.remove(prefKey(PREF_UPDATE_INTERVAL_SUFFIX, appWidgetId));
        prefs.remove(prefKey(PREF_SHOW_LOAD_SUFFIX, appWidgetId));
        prefs.remove(prefKey(PREF_SHOW_ARM_SUFFIX, appWidgetId));
        prefs.remove(prefKey(PREF_SHOW_TEMP_SUFFIX, appWidgetId));
        prefs.commit();
    }

    @Override
    public void onCreate(Bundle icicle) {
        super.onCreate(icicle);

        // Set the result to CANCELED.  This will cause the widget host to cancel
        // out of the widget placement if the user presses the back button.
        setResult(RESULT_CANCELED);

        // Find the widget id from the intent.
        Intent intent = getIntent();
        Bundle extras = intent.getExtras();
        if (extras != null) {
            mAppWidgetId = extras.getInt(
                    AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        }

        // If this activity was started with an intent without an app widget ID, finish with an error.
        if (mAppWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }

        this.getSupportActionBar().setTitle(getString(R.string.widget_configure_title));
        deviceDbHelper = new DeviceDbHelper(this);
        initSpinners();
        linLayoutCustomInterval.setVisibility(View.GONE);
    }

    private void initSpinners() {
        final DeviceSpinnerAdapter deviceSpinnerAdapter = new DeviceSpinnerAdapter(OverclockingWidgetConfigureActivity.this, deviceDbHelper.getFullDeviceCursor(), true);
        widgetPiSpinner.setAdapter(deviceSpinnerAdapter);
        this.updateIntervalsMinutes = this.getResources().getIntArray(R.array.widget_update_intervals_values);
        final ArrayAdapter<CharSequence> updateIntervalAdapter = ArrayAdapter.createFromResource(OverclockingWidgetConfigureActivity.this, R.array.widget_update_intervals, android.R.layout.simple_spinner_item);
        updateIntervalAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        widgetUpdateSpinner.setAdapter(updateIntervalAdapter);
        widgetUpdateSpinner.setOnItemSelectedListener(this);
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_save:
                final Context context = OverclockingWidgetConfigureActivity.this;

                long selectedItemId = widgetPiSpinner.getSelectedItemId();
                LOGGER.info("Selected Device - Item ID = {}", selectedItemId);
                RaspberryDeviceBean deviceBean = deviceDbHelper.read(selectedItemId);
                if (deviceBean.getAuthMethod().equals(NewRaspiAuthActivity.AUTH_PUBLIC_KEY_WITH_PASSWORD) && deviceBean.getKeyfilePass() == null) {
                    Toast.makeText(context, getString(R.string.widget_key_pass_error), Toast.LENGTH_LONG).show();
                    return super.onOptionsItemSelected(item);
                }
                int updateIntervalInMinutes = 0;
                if (updateIntervalsMinutes[widgetUpdateSpinner.getSelectedItemPosition()] == -1) {
                    String s = textEditUpdateInterval.getText().toString().trim();
                    if (Strings.isNullOrEmpty(s)) {
                        textEditUpdateInterval.setError(getString(R.string.widget_update_interval_error));
                        return super.onOptionsItemSelected(item);
                    }
                    updateIntervalInMinutes = Integer.parseInt(s);
                    if (updateIntervalInMinutes == 0) {
                        textEditUpdateInterval.setError(getString(R.string.widget_update_interval_zero));
                        return super.onOptionsItemSelected(item);
                    }
                } else {
                    updateIntervalInMinutes = updateIntervalsMinutes[widgetUpdateSpinner.getSelectedItemPosition()];
                }
                // save Device ID in prefs
                saveChosenDevicePref(context, mAppWidgetId, selectedItemId, updateIntervalInMinutes, checkBoxTemp.isChecked(), checkBoxArm.isChecked(), checkBoxLoad.isChecked(), checkBoxRam.isChecked());
                if (updateIntervalInMinutes > 0) {
                    long updateIntervalMillis = updateIntervalInMinutes * 60 * 1000;
                    // Setting alarm via AlarmManager
                    AlarmManager alarmManager = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
                    PendingIntent selfPendingIntent = OverclockingWidget.getSelfPendingIntent(context, mAppWidgetId, OverclockingWidget.ACTION_WIDGET_UPDATE_ONE);
                    alarmManager.setRepeating(AlarmManager.RTC_WAKEUP, System.currentTimeMillis() + updateIntervalMillis, updateIntervalMillis, selfPendingIntent);
                    OverclockingWidget.addUri(mAppWidgetId, OverclockingWidget.getPendingIntentUri(mAppWidgetId));
                    LOGGER.debug("Added alarm for periodic updates of Wigdet[ID={}], update interval: {} ms.", mAppWidgetId, updateIntervalMillis);
                } else {
                    LOGGER.debug("No periodic updates for Widget[ID={}].", mAppWidgetId);
                }
                // It is the responsibility of the configuration activity to update the app widget
                AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
                OverclockingWidget.updateAppWidget(context, appWidgetManager, mAppWidgetId, deviceDbHelper);

                // Make sure we pass back the original appWidgetId
                Intent resultValue = new Intent();
                resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, mAppWidgetId);
                setResult(RESULT_OK, resultValue);
                finish();
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    @Override
    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
        LOGGER.debug("Item pos {}, id {} selected.", position, id);
        int updateIntervalInMinutes = updateIntervalsMinutes[position];
        if (updateIntervalInMinutes == -1) {
            // custom time interval
            linLayoutCustomInterval.setVisibility(View.VISIBLE);
        } else {
            linLayoutCustomInterval.setVisibility(View.GONE);
        }
    }

    @Override
    public void onNothingSelected(AdapterView<?> parent) {
        // nothing to do
    }
}



