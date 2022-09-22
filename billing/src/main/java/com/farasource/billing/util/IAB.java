package com.farasource.billing.util;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.RemoteException;

import org.json.JSONException;

import com.farasource.billing.PaymentHelper;
import com.farasource.billing.PaymentLauncher;
import com.farasource.billing.communication.BillingSupportCommunication;

import static com.farasource.billing.PaymentHelper.BILLING_RESPONSE_RESULT_OK;
import static com.farasource.billing.PaymentHelper.IABHELPER_BAD_RESPONSE;
import static com.farasource.billing.PaymentHelper.IABHELPER_UNKNOWN_ERROR;
import static com.farasource.billing.PaymentHelper.IABHELPER_UNKNOWN_PURCHASE_RESPONSE;
import static com.farasource.billing.PaymentHelper.IABHELPER_USER_CANCELLED;
import static com.farasource.billing.PaymentHelper.IABHELPER_VERIFICATION_FAILED;
import static com.farasource.billing.PaymentHelper.RESPONSE_CODE;
import static com.farasource.billing.PaymentHelper.RESPONSE_INAPP_PURCHASE_DATA;
import static com.farasource.billing.PaymentHelper.RESPONSE_INAPP_SIGNATURE;
import static com.farasource.billing.PaymentHelper.getResponseDesc;

public abstract class IAB {

    private final String mSignatureBase64;
    // Are subscriptions supported?
    public boolean mSubscriptionsSupported = false;
    // Is setup done?
    public boolean mSetupDone = false;
    protected String bindAddress;
    protected String marketId;
    IABLogger logger;
    int apiVersion = 3;
    // The item type of the current purchase flow
    String mPurchasingItemType;
    // The listener registered on launchPurchaseFlow, which we have to call back when
    // the purchase finishes
    PaymentHelper.OnIabPurchaseFinishedListener mPurchaseListener;
    private final ResultReceiver purchaseResultReceiver = new ResultReceiver() {
        @Override
        public void onReceiver(int resultCode, Intent data) {
            IabResult result;
            flagEndAsync();
            if (data == null) {
                logger.logError("Null data in IAB activity result.");
                result = new IabResult(IABHELPER_BAD_RESPONSE, "Null data in IAB result");
                if (mPurchaseListener != null) {
                    mPurchaseListener.onIabPurchaseFinished(result, null);
                }
                return;
            }

            int responseCode = getResponseCodeFromIntent(data);
            String purchaseData = data.getStringExtra(RESPONSE_INAPP_PURCHASE_DATA);
            String dataSignature = data.getStringExtra(RESPONSE_INAPP_SIGNATURE);

            if (resultCode == Activity.RESULT_OK && responseCode == BILLING_RESPONSE_RESULT_OK) {
                logger.logDebug("Successful resultCode from purchase activity.");
                logger.logDebug("Purchase data: " + purchaseData);
                logger.logDebug("Data signature: " + dataSignature);
                logger.logDebug("Extras: " + data.getExtras());
                logger.logDebug("Expected item type: " + mPurchasingItemType);

                if (purchaseData == null || dataSignature == null) {
                    logger.logError("BUG: either purchaseData or dataSignature is null.");
                    logger.logDebug("Extras: " + data.getExtras().toString());
                    result = new IabResult(IABHELPER_UNKNOWN_ERROR,
                            "IAB returned null purchaseData or dataSignature");
                    if (mPurchaseListener != null) {
                        mPurchaseListener.onIabPurchaseFinished(result, null);
                    }
                    return;
                }

                Purchase purchase = null;
                try {
                    purchase = new Purchase(mPurchasingItemType, purchaseData, dataSignature);
                    String sku = purchase.getSku();

                    // Verify signature
                    if (!Security.verifyPurchase(mSignatureBase64, purchaseData, dataSignature)) {
                        logger.logError("Purchase signature verification FAILED for sku " + sku);
                        result = new IabResult(IABHELPER_VERIFICATION_FAILED,
                                "Signature verification failed for sku " + sku);
                        if (mPurchaseListener != null) {
                            mPurchaseListener.onIabPurchaseFinished(result, purchase);
                        }
                        return;
                    }

                    logger.logDebug("Purchase signature successfully verified.");
                } catch (JSONException e) {
                    logger.logError("Failed to parse purchase data.");
                    e.printStackTrace();
                    result = new IabResult(IABHELPER_BAD_RESPONSE, "Failed to parse purchase data.");
                    if (mPurchaseListener != null) {
                        mPurchaseListener.onIabPurchaseFinished(result, null);
                    }
                    return;
                }

                if (mPurchaseListener != null) {
                    mPurchaseListener
                            .onIabPurchaseFinished(new IabResult(BILLING_RESPONSE_RESULT_OK, "Success"),
                                    purchase);
                }
            } else if (resultCode == Activity.RESULT_OK) {
                // result code was OK, but in-app billing response was not OK.
                logger.logDebug("Result code was OK but in-app billing response was not OK: " +
                        getResponseDesc(responseCode));
                if (mPurchaseListener != null) {
                    result = new IabResult(responseCode, "Problem purchasing item.");
                    mPurchaseListener.onIabPurchaseFinished(result, null);
                }
            } else if (resultCode == Activity.RESULT_CANCELED) {
                logger.logDebug("Purchase canceled - Response: " + getResponseDesc(responseCode));
                result = new IabResult(IABHELPER_USER_CANCELLED, "User canceled.");
                if (mPurchaseListener != null) {
                    mPurchaseListener.onIabPurchaseFinished(result, null);
                }
            } else {
                logger.logError("Purchase failed. Result code: " + resultCode
                        + ". Response: " + getResponseDesc(responseCode));
                result = new IabResult(IABHELPER_UNKNOWN_PURCHASE_RESPONSE, "Unknown purchase response.");
                if (mPurchaseListener != null) {
                    mPurchaseListener.onIabPurchaseFinished(result, null);
                }
            }
        }
    };
    boolean mDisposed = false;

    public IAB(IABLogger logger, String marketId, String bindAddress, String mSignatureBase64) {
        this.logger = logger;
        this.marketId = marketId;
        this.bindAddress = bindAddress;
        this.mSignatureBase64 = mSignatureBase64;
    }

    public ResultReceiver getPurchaseResultReceiver() {
        return purchaseResultReceiver;
    }

    public int getResponseCodeFromBundle(Bundle b) {
        Object o = b.get(RESPONSE_CODE);
        if (o == null) {
            logger.logDebug("Bundle with null response code, assuming OK (known issue)");
            return BILLING_RESPONSE_RESULT_OK;
        } else if (o instanceof Integer) {
            return ((Integer) o).intValue();
        } else if (o instanceof Long) {
            return (int) ((Long) o).longValue();
        } else {
            logger.logError("Unexpected type for bundle response code.");
            logger.logError(o.getClass().getName());
            throw new RuntimeException("Unexpected type for bundle response code: " + o.getClass().getName());
        }
    }

    // Workaround to bug where sometimes response codes come as Long instead of Integer
    int getResponseCodeFromIntent(Intent i) {
        Object o = i.getExtras().get(RESPONSE_CODE);
        if (o == null) {
            logger.logError("Intent with no response code, assuming OK (known issue)");
            return BILLING_RESPONSE_RESULT_OK;
        } else if (o instanceof Integer) {
            return ((Integer) o).intValue();
        } else if (o instanceof Long) {
            return (int) ((Long) o).longValue();
        } else {
            logger.logError("Unexpected type for intent response code.");
            logger.logError(o.getClass().getName());
            throw new RuntimeException("Unexpected type for intent response code: " + o.getClass().getName());
        }
    }

    protected boolean disposed() {
        return mDisposed;
    }

    public void dispose(Context context) {
        mSetupDone = false;
        mDisposed = true;
    }

    public abstract void isBillingSupported(
            int apiVersion,
            String packageName,
            BillingSupportCommunication communication);

    public abstract void launchPurchaseFlow(
            Context mContext, PaymentLauncher paymentLauncher,
            String sku,
            String itemType,
            PaymentHelper.OnIabPurchaseFinishedListener listener,
            String extraData);

    public abstract void consume(Context mContext, Purchase itemInfo) throws IabException;

    public void flagStartAsync(String refresh_inventory) {
    }

    public void flagEndAsync() {
    }

    public abstract Bundle getSkuDetails(int billingVersion, String packageName, String itemType,
                                         Bundle querySkus) throws RemoteException;

    public abstract Bundle getPurchases(int billingVersion, String packageName, String itemType,
                                        String continueToken) throws RemoteException;

    public interface ResultReceiver {
        void onReceiver(int resultCode, Intent data);
    }
}
