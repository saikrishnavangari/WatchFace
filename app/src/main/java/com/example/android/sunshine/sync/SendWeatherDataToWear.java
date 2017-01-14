package com.example.android.sunshine.sync;

import android.content.ContentValues;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.utilities.SunshineWeatherUtils;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataItemBuffer;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * Created by krrish on 10/01/2017.
 */

public class SendWeatherDataToWear implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
    private static String LOG_TAG=SendWeatherDataToWear.class.getSimpleName();
    private ContentValues[] mContentValues;
    private Context mContext;
    private static final String WEATHER_MAX_TEMP = "max";
    private static final String WEATHER_MIN_TEMP = "min";
    private static final String WEATHER_ID = "weather_id";
    private static final String WEATHER_PATH="/weather";
    private GoogleApiClient mGoogleApiClient;
    private static SendWeatherDataToWear sendWeatherDataToWear;

    private SendWeatherDataToWear(){ }

    public static synchronized SendWeatherDataToWear getInstance( ) {
        if (sendWeatherDataToWear == null)
            sendWeatherDataToWear =new SendWeatherDataToWear();
        return sendWeatherDataToWear;
    }



    public void initialize(ContentValues[] values, Context context) {
        mContentValues = values;
        mContext=context;
        mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .addApi(Wearable.API)
                .build();

        if (!mGoogleApiClient.isConnected()){
            mGoogleApiClient.connect();
        }
    }


    void sendWeatherData() {
        ContentValues values=mContentValues[0];
        PutDataMapRequest putDataMapRequest=PutDataMapRequest.create((WEATHER_PATH));
        double Max_temp = (double) values.get(WEATHER_MAX_TEMP);
        double Min_temp = (double) values.get(WEATHER_MIN_TEMP);
        int weather_id= (int) values.get(WEATHER_ID);
        Log.d("weather_id", String.valueOf(weather_id));
        int weather_icon_resource_id= SunshineWeatherUtils
                .getSmallArtResourceIdForWeatherCondition(weather_id);
       /* Bitmap weather_bitmap_image= BitmapFactory.decodeResource(mContext.getResources(),weather_icon_resource_id);
        Asset asset = createAssetFromBitmap(weather_bitmap_image);*/
        putDataMapRequest.getDataMap().putDouble(WEATHER_MAX_TEMP, Max_temp);
        putDataMapRequest.getDataMap().putDouble(WEATHER_MIN_TEMP, Min_temp);
        putDataMapRequest.getDataMap().putInt(WEATHER_ID, weather_id);
       // putDataMapRequest.getDataMap().putAsset("weather_image", asset);
        putDataMapRequest.getDataMap().putLong("time", System.currentTimeMillis());
        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest().setUrgent();
        Wearable.DataApi.putDataItem(mGoogleApiClient, putDataRequest).
                setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(final DataApi.DataItemResult result) {
                if(result.getStatus().isSuccess()) {
                    Log.d("result", "Data item set: " + result.getDataItem().getUri());
                }
            }
        });
        Log.d("Mobile-WearableDataSync", "Items transfered" );
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        if (bundle != null) {
            Log.d("Mobile-WearableDataSync", "mobile side onConnected" + bundle.toString());
        }else {
            Log.d("Mobile-WearableDataSync", "mobile side onConnected Bundle is null" );
        }
        Wearable.DataApi.getDataItems(mGoogleApiClient).setResultCallback(onConnectedResultCallBack);

    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d("Mobile-WearableDataSync", "mobile side suspended : " + i);

    }


    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d("Mobile-WearableDataSync", "mobile side onConnectionFailed : " + connectionResult);

    }
    private final ResultCallback<DataItemBuffer> onConnectedResultCallBack = new ResultCallback<DataItemBuffer>() {
        @Override
        public void onResult(@NonNull DataItemBuffer dataItems) {
            Log.i(LOG_TAG, "Result Callback : " + String.valueOf(dataItems));
        }
    };
   /* private static Asset createAssetFromBitmap(Bitmap bitmap) {
        final ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, byteStream);
        return Asset.createFromBytes(byteStream.toByteArray());
    }*/
}