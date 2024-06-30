package com.farasource.billing.communication;

import android.content.Intent;

public interface BillingReceiverCommunicator {
    void onNewBroadcastReceived(Intent intent);
}
