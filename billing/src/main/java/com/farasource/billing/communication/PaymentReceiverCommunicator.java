package com.farasource.billing.communication;

import android.content.Intent;

public interface PaymentReceiverCommunicator {
    void onNewBroadcastReceived(Intent intent);
}
