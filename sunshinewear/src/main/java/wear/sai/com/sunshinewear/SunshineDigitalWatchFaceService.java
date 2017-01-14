/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package wear.sai.com.sunshinewear;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;
import android.widget.Toast;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;

import java.lang.ref.WeakReference;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Digital watch face with seconds. In ambient mode, the seconds aren't displayed. On devices with
 * low-bit ambient mode, the text is drawn without anti-aliasing in ambient mode.
 */
public class SunshineDigitalWatchFaceService extends CanvasWatchFaceService {
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final String LOG_TAG = "Wearable-WearableDataSync";

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;

    @Override
    public Engine onCreateEngine() {
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineDigitalWatchFaceService.Engine> mWeakReference;

        public EngineHandler(SunshineDigitalWatchFaceService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineDigitalWatchFaceService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine implements
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener,
            DataApi.DataListener {
        private static final long TIMEOUT_MS = 1000;
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTextPaint;
        Paint mSecondLineTextPaint;
        private static final String WEATHER_PATH = "/weather";
        private static final String WEATHER_MAX_TEMP = "max";
        private static final String WEATHER_MIN_TEMP = "min";
        private static final String WEATHER_ID = "weather_id";
        ;
        private final String LOG_TAG = Engine.class.getSimpleName();
        boolean mAmbient;
        Bitmap weatherIconBitmap;
        Calendar mCalendar;
        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };
        int weather_id;
        double mHigh;
        double mLow;
        float mXOffset;
        float mYOffset;
        float mY1Offset;
        ;
        float dp_10;
        float dp_5;
        //Connect to Wearable Api
        GoogleApiClient mClient;
        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            mClient = new GoogleApiClient.Builder(SunshineDigitalWatchFaceService.this)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .addApi(Wearable.API)
                    .build();
            mClient.connect();
            Log.d(LOG_TAG, "googleclient created");
            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineDigitalWatchFaceService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());
            Resources resources = SunshineDigitalWatchFaceService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);
            mY1Offset = resources.getDimension(R.dimen.digital_y1_offset);

            dp_10 = resources.getDimension(R.dimen.dp_10);
            dp_5 = resources.getDimension(R.dimen.dp_5);
            mHigh = 0;
            mLow = 0;
            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.blue));
            mSecondLineTextPaint = new Paint();
            mSecondLineTextPaint = createTextPaint(resources.getColor(R.color.second_linetext));
            mSecondLineTextPaint.setTextSize(30);
            mTextPaint = new Paint();
            mTextPaint = createTextPaint(resources.getColor(R.color.digital_text));
            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineDigitalWatchFaceService.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineDigitalWatchFaceService.this.unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineDigitalWatchFaceService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float textSize = resources.getDimension(isRound
                    ? R.dimen.digital_text_size_round : R.dimen.digital_text_size);

            mTextPaint.setTextSize(textSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTextPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        /**
         * Captures tap event (and tap type) and toggles the background color if the user finishes
         * a tap.
         */
        @Override
        public void onTapCommand(int tapType, int x, int y, long eventTime) {
            switch (tapType) {
                case TAP_TYPE_TOUCH:
                    // The user has started touching the screen.
                    break;
                case TAP_TYPE_TOUCH_CANCEL:
                    // The user has started a different gesture or otherwise cancelled the tap.
                    break;
                case TAP_TYPE_TAP:
                    // The user has completed the tap gesture.
                    // TODO: Add code to handle the tap gesture.
                    Toast.makeText(getApplicationContext(), R.string.message, Toast.LENGTH_SHORT)
                            .show();
                    break;
            }
            invalidate();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.

            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {

                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);
            String time = getTime(mCalendar);
            String date = getdate(mCalendar);
            String text = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR_OF_DAY),
                    mCalendar.get(Calendar.MINUTE))
                    : time;
            float x = mTextPaint.measureText(text) / 2;
            canvas.drawText(text, bounds.centerX() - x, bounds.centerY(), mTextPaint);
            mXOffset=(mSecondLineTextPaint.measureText(date)/2);
            if (!isInAmbientMode()) {

                canvas.drawText(date, bounds.centerX() - (mSecondLineTextPaint.measureText(date)/2),
                        bounds.centerY() + 5 * dp_5, mSecondLineTextPaint);
                if (mHigh != 0 && mLow != 0) {
                    drawWeatherOnCanvas(canvas, bounds);
                }
            }
        }

        private void drawWeatherOnCanvas(Canvas canvas, Rect bounds) {
            Log.d(LOG_TAG, "weather id value : " + weather_id);
            int weatherResourceId = getIconForWeatherCondition(weather_id);
            if (weatherResourceId != -1) {
                Drawable dr = getResources().getDrawable(weatherResourceId, null);
                weatherIconBitmap = ((BitmapDrawable) dr).getBitmap();
                float scaleFactor = getResources().getDimension(R.dimen.high_temp_text_size);
                float scale = scaleFactor / (float) weatherIconBitmap.getHeight();
                Bitmap weatherIconBitMapScaled = Bitmap.createScaledBitmap(
                        weatherIconBitmap, (int) (weatherIconBitmap.getWidth() * scale),
                        (int) (weatherIconBitmap.getHeight() * scale), true
                );
                canvas.drawBitmap(weatherIconBitMapScaled, mXOffset, mYOffset + 2 * mY1Offset + dp_10, null);

                String highTemp = String.format(getResources().getString(R.string.format_temperature), mHigh);
                String lowTemp = String.format(getResources().getString(R.string.format_temperature), mLow);
                canvas.drawText(highTemp, mXOffset + 3 * dp_10, mYOffset + 2 * mY1Offset + 2 * dp_10 + dp_5, mSecondLineTextPaint);
                canvas.drawText(lowTemp, mXOffset + 8 * dp_10, mYOffset + 2 * mY1Offset + 2 * dp_10 + dp_5, mSecondLineTextPaint);
            }
        }

        private String getdate(Calendar mCalendar) {
            Date date = mCalendar.getTime();
            SimpleDateFormat dateformat = new SimpleDateFormat("MMM dd yyyy");
            return dateformat.format(date);
        }

        private String getTime(Calendar mCalendar) {
            Date date = mCalendar.getTime();
            SimpleDateFormat dateformat = new SimpleDateFormat("HH:mm:ss");

            return dateformat.format(date);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }


        @Override
        public void onConnected(@Nullable Bundle bundle) {
            if (bundle != null) {
                Log.d("Mobile-WearableDataSync", "wearable side onConnected" + bundle.toString());
            } else {
                Log.d("Mobile-WearableDataSync", "wearable side onConnected Bundle is null");
            }

            Wearable.DataApi.addListener(mClient, this);
        }

        @Override
        public void onConnectionSuspended(int i) {
            Log.d("Mobile-WearableDataSync", "wearable side suspended : " + i);
            Wearable.DataApi.removeListener(mClient, this);
            if (mClient.isConnected())
                mClient.disconnect();
        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
            Log.d("Mobile-WearableDataSync", "wearable side onConnectionFailed : " + connectionResult);

        }

        private final ResultCallback<DataItemBuffer> onConnectedResultCallBack = new ResultCallback<DataItemBuffer>() {
            @Override
            public void onResult(@NonNull DataItemBuffer dataItems) {
                Log.i(LOG_TAG, "Result Callback : " + String.valueOf(dataItems));
            }
        };

        public int getIconForWeatherCondition(int weatherId) {
            if (weatherId >= 200 && weatherId <= 232) {
                return R.drawable.ic_storm;
            } else if (weatherId >= 300 && weatherId <= 321) {
                return R.drawable.ic_light_rain;
            } else if (weatherId >= 500 && weatherId <= 504) {
                return R.drawable.ic_rain;
            } else if (weatherId == 511) {
                return R.drawable.ic_snow;
            } else if (weatherId >= 520 && weatherId <= 531) {
                return R.drawable.ic_rain;
            } else if (weatherId >= 600 && weatherId <= 622) {
                return R.drawable.ic_snow;
            } else if (weatherId >= 701 && weatherId <= 761) {
                return R.drawable.ic_fog;
            } else if (weatherId == 761 || weatherId == 771 || weatherId == 781) {
                return R.drawable.ic_storm;
            } else if (weatherId == 800) {
                return R.drawable.ic_clear;
            } else if (weatherId == 801) {
                return R.drawable.ic_light_clouds;
            } else if (weatherId >= 802 && weatherId <= 804) {
                return R.drawable.ic_cloudy;
            } else if (weatherId >= 900 && weatherId <= 906) {
                return R.drawable.ic_storm;
            } else if (weatherId >= 958 && weatherId <= 962) {
                return R.drawable.ic_storm;
            } else if (weatherId >= 951 && weatherId <= 957) {
                return R.drawable.ic_clear;
            }

            Log.e(LOG_TAG, "Unknown Weather: " + weatherId);
            return -1;
        }

        /*
          public Bitmap loadBitmapFromAsset(Asset asset) {
              if (asset == null) {
                  throw new IllegalArgumentException("Asset must be non-null");
              }
              ConnectionResult result =
                      mClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
              if (!result.isSuccess()) {
                  return null;
              }
              // convert asset into a file descriptor and block until it's ready
              final InputStream[] assetInputStream = new InputStream[1];
              Wearable.DataApi.getFdForAsset(
                      mClient, asset).await().getInputStream();

              if (assetInputStream[0] == null) {
                  Log.w(LOG_TAG, "Requested an unknown Asset.");
                  return null;
              }
              // decode the stream into a bitmap
              return BitmapFactory.decodeStream(assetInputStream[0]);
          }*/
        @Override
        public void onDataChanged(DataEventBuffer dataEvents) {
            Log.d(LOG_TAG, "New data received");
            for (DataEvent event : dataEvents) {
                if (event.getType() == DataEvent.TYPE_CHANGED) {
                    DataItem item = event.getDataItem();
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();
                    String path = item.getUri().getPath();
                    if (path.equals(WEATHER_PATH)) {
                        mHigh = dataMap.getDouble(WEATHER_MAX_TEMP);
                        mLow = dataMap.getDouble(WEATHER_MIN_TEMP);
                        weather_id = dataMap.getInt(WEATHER_ID);
                        // createIcons(dataMap.getString(ICON));

                        /*mWeather_bitmap_icon = loadBitmapFromAsset(weather_icon);
                        Log.d("bitmapicon", String.valueOf(weather_icon));
                        invalidate();*/

                    }
                }
            }
        }
    }
}
