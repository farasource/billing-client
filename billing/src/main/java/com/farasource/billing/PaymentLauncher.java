package com.farasource.billing;

import android.app.PendingIntent;
import android.content.Intent;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.ActivityResultRegistry;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;

public final class PaymentLauncher {

    private static final String BILLING_SERVICE_KEY = "billing_service_key";
    private final ActivityResultLauncher<Intent> activityLauncher;
    private final ActivityResultLauncher<IntentSenderRequest> activitySenderLauncher;

    private PaymentLauncher(ActivityResultLauncher<Intent> activityLauncher,
                            ActivityResultLauncher<IntentSenderRequest> activitySenderLauncher) {
        this.activityLauncher = activityLauncher;
        this.activitySenderLauncher = activitySenderLauncher;
    }

    public void startIntent(PendingIntent pendingIntent) {
        activitySenderLauncher.launch(new IntentSenderRequest.Builder(pendingIntent.getIntentSender()).build());
    }

    public void startIntent(Intent intent) {
        activityLauncher.launch(intent);
    }

    public void unregister() {
        activityLauncher.unregister();
        activitySenderLauncher.unregister();
    }

    protected static class Builder {

        public PaymentLauncher build(ActivityResultRegistry registry, ActivityResultCallback<ActivityResult> callback) {
            ActivityResultLauncher<Intent> activityLauncher = registry.register(
                    PaymentLauncher.BILLING_SERVICE_KEY,
                    new ActivityResultContracts.StartActivityForResult(),
                    callback
            );
            ActivityResultLauncher<IntentSenderRequest> activitySenderLauncher = registry.register(
                    PaymentLauncher.BILLING_SERVICE_KEY,
                    new ActivityResultContracts.StartIntentSenderForResult(),
                    callback
            );
            return new PaymentLauncher(activityLauncher, activitySenderLauncher);
        }

    }
}