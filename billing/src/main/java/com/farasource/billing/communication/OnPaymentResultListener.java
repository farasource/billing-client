package com.farasource.billing.communication;

import com.farasource.billing.util.Inventory;
import com.farasource.billing.util.Purchase;

public interface OnPaymentResultListener {

    void onBillingSuccess(Purchase purchase);

    void onConsumeFinished(Purchase purchase, boolean success);

    void onBillingStatus(int code);

    void onQueryInventoryFinished(Inventory inventory);

}