package com.farasource.billing;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.activity.result.ActivityResultRegistry;

import com.farasource.billing.util.IABLogger;
import com.farasource.billing.util.IabResult;
import com.farasource.billing.util.Inventory;
import com.farasource.billing.util.Purchase;
import com.farasource.billing.util.TableCodes;
import com.farasource.billing.communication.OnPaymentResultListener;

public class Payment {

    private final IABLogger logger = new IABLogger();
    private final Context context;
    private final ActivityResultRegistry activityResultRegistry;
    // The helper object
    PaymentHelper mHelper;
    private String SKU = null;
    private final String RSA;
    private boolean globalAutoConsume, autoConsume, disposed, hasLaunch, startedSetup;
    private OnPaymentResultListener onPaymentResultListener;
    // Called when consumption is complete
    PaymentHelper.OnConsumeFinishedListener mConsumeFinishedListener = new PaymentHelper.OnConsumeFinishedListener() {
        public void onConsumeFinished(Purchase purchase, IabResult result) {
            logger.logDebug("Consumption finished. Purchase: " + purchase + ", result: " + result);

            // if we were disposed of in the meantime, quit.
            if (mHelper == null) return;

            // We know this is the "gas" sku because it's the only one we consume,
            // so we don't check which sku was consumed. If you have more than one
            // sku, you probably should check...
            if (result.isSuccess()) {
                // successfully consumed, so we apply the effects of the item in our
                // game world's logic, which in our case means filling the gas tank a bit
                logger.logDebug("Consumption successful. Provisioning.");

            } else {
                logger.logDebug("Error while consuming: " + result);
            }
            if (onPaymentResultListener != null)
                onPaymentResultListener.onConsumeFinished(purchase, result.isSuccess());
            logger.logDebug("End consumption flow.");
        }
    };
    // Callback for when a purchase is finished
    PaymentHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new PaymentHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            logger.logDebug("Purchase finished: " + result + ", purchase: " + purchase);

            hasLaunch = false;

            // if we were disposed of in the meantime, quit.
            if (mHelper == null) return;

            if (result.isFailure() || !purchase.getSku().equalsIgnoreCase(SKU)) {
                onBillingStatus(TableCodes.PAYMENT_FAILED);
                return;
            } else {

                if (autoConsume) consume(purchase);

                if (onPaymentResultListener != null)
                    onPaymentResultListener.onBillingSuccess(purchase);
            }

            logger.logDebug("Purchase successful.");

        }
    };
    // Listener that's called when we finish querying the items and subscriptions we own
    PaymentHelper.QueryInventoryFinishedListener mGotInventoryListener = new PaymentHelper.QueryInventoryFinishedListener() {
        @Override
        public void onQueryInventoryFinished(IabResult result, Inventory inv) {
            logger.logDebug("Query inventory finished.");

            // Have we been disposed of in the meantime? If so, quit.
            if (mHelper == null) return;

            // Is it a failure?
            if (result.isFailure()) {
                //
                return;
            }

            logger.logDebug("Query inventory was successful.");

            /*
             * Check for items we own. Notice that for each purchase, we check
             * the developer payload to see if it's correct! See
             * verifyDeveloperPayload().
             */

            if (onPaymentResultListener != null)
                onPaymentResultListener.onQueryInventoryFinished(inv);

            logger.logDebug("Initial inventory query finished; enabling main UI.");
        }
    };

    public Payment(ActivityResultRegistry registry, Context context, String rsa) {
        this.activityResultRegistry = registry;
        this.context = context;
        this.RSA = rsa;
    }

    public void setOnPaymentResultListener(OnPaymentResultListener onPaymentResultListener) {
        this.onPaymentResultListener = onPaymentResultListener;
        mHelper = new PaymentHelper(activityResultRegistry, context, RSA);
        if (isMarketNotInstalled()) {
            onBillingStatus(TableCodes.MARKET_NOT_INSTALLED);
            return;
        } else if (startedSetup) {
            return;
        }
        try {
            mHelper.startSetup(result -> {
                if (result.isFailure()) {
                    logger.logDebug("startSetup failed.");

                    onBillingStatus(TableCodes.SETUP_FAILED);
                    //
                    dispose();
                } else {
                    // Have we been disposed of in the meantime? If so, quit.
                    if (mHelper == null) return;

                    // IAB is fully set up. Now, let's get an inventory of stuff we own.
                    logger.logDebug("Setup successful. Querying inventory.");
                    startedSetup = true;
                    onBillingStatus(TableCodes.SETUP_SUCCESS);
                    mHelper.queryInventoryAsync(mGotInventoryListener);
                }
            });
        } catch (Exception e) {
            logger.logDebug(e.toString());
            onBillingStatus(TableCodes.SETUP_FAILED);
        }
    }

    public void rebuildActivityResultRegistry(ActivityResultRegistry registry) {
        if (mHelper != null) {
            mHelper.buildPaymentLauncher(registry);
        }
    }

    public void launchPayment(String sku) {
        launchPayment(sku, PaymentHelper.ITEM_TYPE_INAPP, "", globalAutoConsume);
    }

    public void launchPayment(String sku, boolean autoConsume) {
        launchPayment(sku, PaymentHelper.ITEM_TYPE_INAPP, "", autoConsume);
    }

    public void launchPayment(String sku, String type) {
        launchPayment(sku, type, "", globalAutoConsume);
    }

    public void launchPayment(String sku, String type, boolean autoConsume) {
        launchPayment(sku, type, "", autoConsume);
    }

    public void launchPayment(String sku, String type, String payload) {
        launchPayment(sku, type, payload, globalAutoConsume);
    }

    public void launchPayment(String sku, String type, String payload, boolean autoConsume) {
        if (disposed) {
            onBillingStatus(TableCodes.PAYMENT_DISPOSED);
            return;
        } else if (hasLaunch) {
            onBillingStatus(TableCodes.PAYMENT_IS_IN_PROGRESS);
            return;
        } else if (isMarketNotInstalled()) {
            onBillingStatus(TableCodes.MARKET_NOT_INSTALLED);
            return;
        } else if (!com.farasource.billing.NetworkCheck.isOnline(context)) {
            onBillingStatus(TableCodes.NO_NETWORK);
            return;
        }
        this.SKU = sku;
        this.autoConsume = autoConsume;
        if (PaymentHelper.ITEM_TYPE_SUBS.equals(type) && !mHelper.subscriptionsSupported()) {
            logger.logDebug("Subscriptions not supported on your device yet. Sorry!");
            onBillingStatus(TableCodes.SUBSCRIPTIONS_NOT_SUPPORTED);
            return;
        }
        try {
            hasLaunch = true;
            mHelper.launchPurchaseFlow(sku, type, mPurchaseFinishedListener, payload);
        } catch (Exception e) {
            logger.logDebug(e.getMessage());
            hasLaunch = false;
            onBillingStatus(TableCodes.PAYMENT_FAILED);
        }
    }

    public void setGlobalAutoConsume(boolean autoConsume) {
        if (hasLaunch) {
            logger.logDebug("Can't be used while payment is active.");
            return;
        }
        this.globalAutoConsume = autoConsume;
    }

    public void consume(Purchase purchase) {
        if (disposed) {
            if (onPaymentResultListener != null)
                onPaymentResultListener.onConsumeFinished(purchase, false);
            return;
        }
        try {
            mHelper.consumeAsync(purchase, mConsumeFinishedListener);
        } catch (Exception exception) {
            logger.logDebug(exception.getMessage());
            if (onPaymentResultListener != null)
                onPaymentResultListener.onConsumeFinished(purchase, false);
        }
    }

    public void dispose() {
        disposed = true;
        try {
            if (mHelper != null) mHelper.dispose();
        } catch (Exception e) {
            logger.logError(e.getMessage());
        }
        mHelper = null;
        mGotInventoryListener = null;
        mPurchaseFinishedListener = null;
        mConsumeFinishedListener = null;
    }

    private boolean isMarketNotInstalled() {
        try {
            context.getPackageManager().getPackageInfo(mHelper.getMarketId(), PackageManager.GET_ACTIVITIES);
            return false;
        } catch (PackageManager.NameNotFoundException e) {
            return true;
        }
    }

    private void onBillingStatus(int code) {
        if (onPaymentResultListener != null) onPaymentResultListener.onBillingStatus(code);
    }

}