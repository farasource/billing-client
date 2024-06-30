package com.farasource.billing;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.activity.result.ActivityResultRegistry;

import com.farasource.billing.util.IABLogger;
import com.farasource.billing.util.IabResult;
import com.farasource.billing.util.Inventory;
import com.farasource.billing.util.Purchase;
import com.farasource.billing.util.Security;
import com.farasource.billing.util.TableCodes;
import com.farasource.billing.communication.OnBillingResultListener;

public class BillingClient {

    private final IABLogger logger = new IABLogger();
    private final Context context;
    private final ActivityResultRegistry activityResultRegistry;
    // The helper object
    BillingHelper mHelper;
    private String sku = null;
    private boolean globalAutoConsume, autoConsume, disposed, hasLaunch, startedSetup, hasGotInventory;
    private OnBillingResultListener onBillingResultListener;
    // Called when consumption is complete
    BillingHelper.OnConsumeFinishedListener mConsumeFinishedListener = new BillingHelper.OnConsumeFinishedListener() {
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
            if (onBillingResultListener != null)
                onBillingResultListener.onConsumeFinished(purchase, result.isSuccess());
            logger.logDebug("End consumption flow.");
        }
    };
    // Callback for when a purchase is finished
    BillingHelper.OnIabPurchaseFinishedListener mPurchaseFinishedListener = new BillingHelper.OnIabPurchaseFinishedListener() {
        public void onIabPurchaseFinished(IabResult result, Purchase purchase) {
            logger.logDebug("Purchase finished: " + result + ", purchase: " + purchase);

            hasLaunch = false;

            // if we were disposed of in the meantime, quit.
            if (mHelper == null) return;

            if (result.isFailure() || !purchase.getSku().equalsIgnoreCase(sku)) {
                onBillingStatus(TableCodes.BILLING_FAILED);
                return;
            } else {

                if (autoConsume) consume(purchase);

                if (onBillingResultListener != null)
                    onBillingResultListener.onBillingSuccess(purchase);
            }

            logger.logDebug("Purchase successful.");

        }
    };
    // Listener that's called when we finish querying the items and subscriptions we own
    BillingHelper.QueryInventoryFinishedListener mGotInventoryListener = new BillingHelper.QueryInventoryFinishedListener() {
        @Override
        public void onQueryInventoryFinished(IabResult result, Inventory inv) {
            logger.logDebug("Query inventory finished.");

            hasGotInventory = false;

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

            if (onBillingResultListener != null)
                onBillingResultListener.onQueryInventoryFinished(inv);

            logger.logDebug("Initial inventory query finished; enabling main UI.");
        }
    };

    public BillingClient(ActivityResultRegistry registry, Context context) {
        this.activityResultRegistry = registry;
        this.context = context;
    }

    public void setOnBillingResultListener(OnBillingResultListener onBillingResultListener) {
        this.onBillingResultListener = onBillingResultListener;
        mHelper = new BillingHelper(activityResultRegistry, context);
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
                    endConnection();
                } else {
                    // Have we been disposed of in the meantime? If so, quit.
                    if (mHelper == null) return;

                    // IAB is fully set up. Now, let's get an inventory of stuff we own.
                    logger.logDebug("Setup successful. Querying inventory.");

                    if (!hasLaunch) {
                        try {
                            hasGotInventory = true;
                            mHelper.queryInventoryAsync(mGotInventoryListener);
                        } catch (Exception e) {
                            hasGotInventory = false;
                        }
                    }
                    startedSetup = true;
                    onBillingStatus(TableCodes.SETUP_SUCCESS);
                }
            });
        } catch (Exception e) {
            logger.logDebug(e.toString());
            onBillingStatus(TableCodes.SETUP_FAILED);
        }
    }

    public void rebuildActivityResultRegistry(ActivityResultRegistry registry) {
        if (mHelper != null) {
            mHelper.buildBillingLauncher(registry);
        }
    }

    @Deprecated()
    public static boolean verifyPurchase(Purchase purchase, String base64PublicKey) {
        return Security.verifyPurchase(base64PublicKey, purchase.getOriginalJson(), purchase.getSignature());
    }

    public void launchBilling(String sku) {
        launchBilling(sku, BillingHelper.ITEM_TYPE_INAPP, "", globalAutoConsume);
    }

    public void launchBilling(String sku, boolean autoConsume) {
        launchBilling(sku, BillingHelper.ITEM_TYPE_INAPP, "", autoConsume);
    }

    public void launchBilling(String sku, String type) {
        launchBilling(sku, type, "", globalAutoConsume);
    }

    public void launchBilling(String sku, String type, boolean autoConsume) {
        launchBilling(sku, type, "", autoConsume);
    }

    public void launchBilling(String sku, String type, String payload) {
        launchBilling(sku, type, payload, globalAutoConsume);
    }

    public void launchBilling(String sku, String type, String payload, boolean autoConsume) {
        if (disposed) {
            onBillingStatus(TableCodes.BILLING_DISPOSED);
            return;
        } else if (hasLaunch || hasGotInventory) {
            onBillingStatus(TableCodes.BILLING_IS_IN_PROGRESS);
            return;
        } else if (isMarketNotInstalled()) {
            onBillingStatus(TableCodes.MARKET_NOT_INSTALLED);
            return;
        } else if (!com.farasource.billing.NetworkCheck.isOnline(context)) {
            onBillingStatus(TableCodes.NO_NETWORK);
            return;
        }
        this.sku = sku;
        this.autoConsume = autoConsume;
        if (BillingHelper.ITEM_TYPE_SUBS.equals(type) && !mHelper.subscriptionsSupported()) {
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
            onBillingStatus(TableCodes.BILLING_FAILED);
        }
    }

    public void setGlobalAutoConsume(boolean autoConsume) {
        if (hasLaunch) {
            logger.logDebug("Can't be used while billing is active.");
            return;
        }
        this.globalAutoConsume = autoConsume;
    }

    public void consume(Purchase purchase) {
        if (disposed) {
            if (onBillingResultListener != null)
                onBillingResultListener.onConsumeFinished(purchase, false);
            return;
        }
        try {
            mHelper.consumeAsync(purchase, mConsumeFinishedListener);
        } catch (Exception exception) {
            logger.logDebug(exception.getMessage());
            if (onBillingResultListener != null)
                onBillingResultListener.onConsumeFinished(purchase, false);
        }
    }

    public void endConnection() {
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

    public static boolean isPackageInstalled(Context context, String packageID) {
        try {
            context.getPackageManager().getPackageInfo(packageID, PackageManager.GET_ACTIVITIES);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    private boolean isMarketNotInstalled() {
        return !isPackageInstalled(context, mHelper.getMarketId());
    }


    private void onBillingStatus(int code) {
        if (onBillingResultListener != null) onBillingResultListener.onBillingStatus(code);
    }

}