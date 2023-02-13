package com.farasource.billing;

import android.content.Context;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

class NetworkCheck {

    static boolean isOnline(Context context) {
        try {
            ConnectivityManager cm = (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo activeNetwork = cm.getActiveNetworkInfo();

            NetworkInfo wifiNetwork = cm.getNetworkInfo(ConnectivityManager.TYPE_WIFI);

            NetworkInfo mobileNetwork = cm.getNetworkInfo(ConnectivityManager.TYPE_MOBILE);

            boolean isWifi = false;
            boolean isOtherNetwork = false;

            if (wifiNetwork != null) {
                isWifi = wifiNetwork.isConnectedOrConnecting();
            }

            if (mobileNetwork != null) {
                isOtherNetwork = mobileNetwork.isConnectedOrConnecting();
            }

            if (activeNetwork != null) {
                isOtherNetwork = activeNetwork.isConnectedOrConnecting();

            }

            return isWifi || isOtherNetwork;
        } catch (Exception e) {
            //
        }
        return true;
    }

}
