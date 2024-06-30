package com.farasource.billing.receiver;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.farasource.billing.communication.BillingReceiverCommunicator;

import java.util.ArrayList;
import java.util.List;

public class BillingReceiver extends BroadcastReceiver {

    private static final Object observerLock = new Object();
    private static final List<BillingReceiverCommunicator> observers = new ArrayList<>();

    public static void addObserver(BillingReceiverCommunicator communicator) {
        synchronized (observerLock) {
            observers.add(communicator);
        }
    }

    public static void removeObserver(BillingReceiverCommunicator communicator) {
        synchronized (observerLock) {
            observers.remove(communicator);
        }
    }

    private static void notifyObservers(Intent intent) {
        synchronized (observerLock) {
            for (BillingReceiverCommunicator observer : observers) {
                observer.onNewBroadcastReceived(intent);
            }
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        Intent sendIntent = new Intent();
        sendIntent.setAction(intent.getAction() + ".iab");
        Bundle bundle = intent.getExtras();
        if (bundle != null) {
            sendIntent.putExtras(bundle);
        }
        notifyObservers(sendIntent);
    }
}
