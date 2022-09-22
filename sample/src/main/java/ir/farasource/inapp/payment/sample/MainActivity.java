/* Copyright (c) 2012 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ir.farasource.inapp.payment.sample;

import android.app.AlertDialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;

import com.farasource.billing.Payment;
import com.farasource.billing.PaymentHelper;
import com.farasource.billing.communication.OnPaymentResultListener;
import com.farasource.billing.util.Inventory;
import com.farasource.billing.util.Purchase;
import com.farasource.billing.util.TableCodes;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Example game using in-app billing version 3.
 * <p>
 * Before attempting to run this sample, please read the README file. It
 * contains important information on how to set up this project.
 * <p>
 * All the game-specific logic is implemented here in MainActivity, while the
 * general-purpose boilerplate that can be reused in any app is provided in the
 * classes in the util/ subdirectory. When implementing your own application,
 * you can copy over util/*.java to make use of those utility classes.
 * <p>
 * This game is a simple "driving" game where the player can buy gas
 * and drive. The car has a tank which stores gas. When the player purchases
 * gas, the tank fills up (1/4 tank at a time). When the player drives, the gas
 * in the tank diminishes (also 1/4 tank at a time).
 * <p>
 * The user can also purchase a "premium upgrade" that gives them a red car
 * instead of the standard blue one (exciting!).
 * <p>
 * The user can also purchase a subscription ("infinite gas") that allows them
 * to drive without using up any gas while that subscription is active.
 * <p>
 * It's important to note the consumption mechanics for each item.
 * <p>
 * PREMIUM: the item is purchased and NEVER consumed. So, after the original
 * purchase, the player will always own that item. The application knows to
 * display the red car instead of the blue one because it queries whether
 * the premium "item" is owned or not.
 * <p>
 * INFINITE GAS: this is a subscription, and subscriptions can't be consumed.
 * <p>
 * GAS: when gas is purchased, the "gas" item is then owned. We consume it
 * when we apply that item's effects to our app's world, which to us means
 * filling up 1/4 of the tank. This happens immediately after purchase!
 * It's at this point (and not when the user drives) that the "gas"
 * item is CONSUMED. Consumption should always happen when your game
 * world was safely updated to apply the effect of the purchase. So,
 * in an example scenario:
 * <p>
 * BEFORE:      tank at 1/2
 * ON PURCHASE: tank at 1/2, "gas" item is owned
 * IMMEDIATELY: "gas" is consumed, tank goes to 3/4
 * AFTER:       tank at 3/4, "gas" item NOT owned any more
 * <p>
 * Another important point to notice is that it may so happen that
 * the application crashed (or anything else happened) after the user
 * purchased the "gas" item, but before it was consumed. That's why,
 * on startup, we check if we own the "gas" item, and, if so,
 * we have to apply its effects to our world and consume it. This
 * is also very important!
 *
 * @author Bruno Oliveira (Google)
 */
public class MainActivity extends AppCompatActivity {
    // Debug tag, for logging
    static final String TAG = "TrivialDrive";
    // SKUs for our products: the premium upgrade (non-consumable) and gas (consumable)
    static final String SKU_PREMIUM = "premium";
    static final String SKU_GAS = "gas";
    // SKU for our subscription (infinite gas)
    static final String SKU_INFINITE_GAS = "infinite_gas";
    // How many units (1/4 tank is our unit) fill in the tank.
    static final int TANK_MAX = 4;
    // Graphics for the gas gauge
    static int[] TANK_RES_IDS = {R.drawable.gas0, R.drawable.gas1, R.drawable.gas2,
            R.drawable.gas3, R.drawable.gas4};
    // Does the user have the premium upgrade?
    boolean mIsPremium = false;
    // Does the user have an active subscription to the infinite gas plan?
    boolean mSubscribedToInfiniteGas = false;
    // Current amount of gas in tank, in units
    int mTank;

    Payment payment;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // load game data
        loadData();

        /* base64EncodedPublicKey should be YOUR APPLICATION'S PUBLIC KEY
         * (that you got from the Google Play developer console). This is not your
         * developer public key, it's the *app-specific* public key.
         *
         * Instead of just storing the entire literal string here embedded in the
         * program,  construct the key at runtime from pieces or
         * use bit manipulation (for example, XOR with some other string) to hide
         * the actual key.  The key itself is not secret information, but we don't
         * want to make it easy for an attacker to replace the public key with one
         * of their own and then fake messages from the server.
         */

        // Create the helper, passing it our context and the public key to verify signatures with
        Log.d(TAG, "Creating IAB helper.");
        payment = new Payment(getActivityResultRegistry(), this, "PUBLIC_KEY");
        payment.setCanAutoConsume(false);
        payment.setOnPaymentResultListener(new OnPaymentResultListener() {
            @Override
            public void onBillingSuccess(Purchase purchase) {
                if (!verifyDeveloperPayload(purchase)) {
                    complain("Error purchasing. Authenticity verification failed.");
                    setWaitScreen(false);
                    return;
                }

                Log.d(TAG, "Purchase successful.");

                if (purchase.getSku().equals(SKU_GAS)) {
                    // bought 1/4 tank of gas. So consume it.
                    Log.d(TAG, "Purchase is gas. Starting gas consumption.");
                    payment.consume(purchase);
                } else if (purchase.getSku().equals(SKU_PREMIUM)) {
                    // bought the premium upgrade!
                    Log.d(TAG, "Purchase is premium upgrade. Congratulating user.");
                    alert("Thank you for upgrading to premium!");
                    mIsPremium = true;
                    updateUi();
                    setWaitScreen(false);
                } else if (purchase.getSku().equals(SKU_INFINITE_GAS)) {
                    // bought the infinite gas subscription
                    Log.d(TAG, "Infinite gas subscription purchased.");
                    alert("Thank you for subscribing to infinite gas!");
                    mSubscribedToInfiniteGas = true;
                    mTank = TANK_MAX;
                    updateUi();
                    setWaitScreen(false);
                }
            }

            @Override
            public void onConsumeFinished(Purchase purchase, boolean success) {
                // successfully consumed, so we apply the effects of the item in our
                // game world's logic, which in our case means filling the gas tank a bit
                Log.d(TAG, "Consumption successful. Provisioning.");
                mTank = mTank == TANK_MAX ? TANK_MAX : mTank + 1;
                saveData();
                alert("You filled 1/4 tank. Your tank is now " + mTank + "/4 full!");
                updateUi();
                setWaitScreen(false);
                Log.d(TAG, "End consumption flow.");
            }

            @Override
            public void onBillingStatus(int code) {
                if (code != TableCodes.SETUP_SUCCESS) {
                    complain("Error purchasing code: " + code);
                    setWaitScreen(false);
                }
            }

            @Override
            public void onQueryInventoryFinished(Inventory inventory) {
                // Do we have the premium upgrade?
                Purchase premiumPurchase = inventory.getPurchase(SKU_PREMIUM);
                mIsPremium = (premiumPurchase != null && verifyDeveloperPayload(premiumPurchase));
                Log.d(TAG, "User is " + (mIsPremium ? "PREMIUM" : "NOT PREMIUM"));

                // Do we have the infinite gas plan?
                Purchase infiniteGasPurchase = inventory.getPurchase(SKU_INFINITE_GAS);
                mSubscribedToInfiniteGas = (infiniteGasPurchase != null &&
                        verifyDeveloperPayload(infiniteGasPurchase));
                Log.d(TAG, "User " + (mSubscribedToInfiniteGas ? "HAS" : "DOES NOT HAVE")
                        + " infinite gas subscription.");
                if (mSubscribedToInfiniteGas) mTank = TANK_MAX;

                // Check for gas delivery -- if we own gas, we should fill up the tank immediately
                Purchase gasPurchase = inventory.getPurchase(SKU_GAS);
                if (gasPurchase != null && verifyDeveloperPayload(gasPurchase)) {
                    Log.d(TAG, "We have gas. Consuming it.");
                    payment.consume(inventory.getPurchase(SKU_GAS));
                    return;
                }

                updateUi();
                setWaitScreen(false);
                Log.d(TAG, "Initial inventory query finished; enabling main UI.");
            }
        });

        // Start setup. This is asynchronous and the specified listener
        // will be called once setup completes.
        Log.d(TAG, "Starting setup.");
    }

    // User clicked the "Buy Gas" button
    public void onBuyGasButtonClicked(View arg0) {
        Log.d(TAG, "Buy gas button clicked.");

        if (mSubscribedToInfiniteGas) {
            complain("No need! You're subscribed to infinite gas. Isn't that awesome?");
            return;
        }

        if (mTank >= TANK_MAX) {
            complain("Your tank is full. Drive around a bit!");
            return;
        }

        // launch the gas purchase UI flow.
        // We will be notified of completion via mPurchaseFinishedListener
        setWaitScreen(true);
        Log.d(TAG, "Launching purchase flow for gas.");

        /* TODO: for security, generate your payload here for verification. See the comments on
         *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
         *        an empty string, but on a production app you should carefully generate this. */
        String payload = "";

        payment.launchPayment(SKU_GAS, PaymentHelper.ITEM_TYPE_INAPP, payload);
    }

    // User clicked the "Upgrade to Premium" button.
    public void onUpgradeAppButtonClicked(View arg0) {
        Log.d(TAG, "Upgrade button clicked; launching purchase flow for upgrade.");
        setWaitScreen(true);

        /* TODO: for security, generate your payload here for verification. See the comments on
         *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
         *        an empty string, but on a production app you should carefully generate this. */
        String payload = "";

        payment.launchPayment(SKU_PREMIUM, PaymentHelper.ITEM_TYPE_INAPP, payload);
    }

    // "Subscribe to infinite gas" button clicked. Explain to user, then start purchase
    // flow for subscription.
    public void onInfiniteGasButtonClicked(View arg0) {
        /* TODO: for security, generate your payload here for verification. See the comments on
         *        verifyDeveloperPayload() for more info. Since this is a SAMPLE, we just use
         *        an empty string, but on a production app you should carefully generate this. */
        String payload = "";

        setWaitScreen(true);
        Log.d(TAG, "Launching purchase flow for infinite gas subscription.");
        payment.launchPayment(SKU_INFINITE_GAS, PaymentHelper.ITEM_TYPE_SUBS, payload);
    }

    /**
     * Verifies the developer payload of a purchase.
     */
    boolean verifyDeveloperPayload(Purchase p) {
        String payload = p.getDeveloperPayload();

        /*
         * TODO: verify that the developer payload of the purchase is correct. It will be
         * the same one that you sent when initiating the purchase.
         *
         * WARNING: Locally generating a random string when starting a purchase and
         * verifying it here might seem like a good approach, but this will fail in the
         * case where the user purchases an item on one device and then uses your app on
         * a different device, because on the other device you will not have access to the
         * random string you originally generated.
         *
         * So a good developer payload has these characteristics:
         *
         * 1. If two different users purchase an item, the payload is different between them,
         *    so that one user's purchase can't be replayed to another user.
         *
         * 2. The payload must be such that you can verify it even when the app wasn't the
         *    one who initiated the purchase flow (so that items purchased by the user on
         *    one device work on other devices owned by the user).
         *
         * Using your own server to store and verify developer payloads across app
         * installations is recommended.
         */

        return true;
    }

    // Drive button clicked. Burn gas!
    public void onDriveButtonClicked(View arg0) {
        Log.d(TAG, "Drive button clicked.");
        if (!mSubscribedToInfiniteGas && mTank <= 0)
            alert("Oh, no! You are out of gas! Try buying some!");
        else {
            if (!mSubscribedToInfiniteGas) --mTank;
            saveData();
            alert("Vroooom, you drove a few miles.");
            updateUi();
            Log.d(TAG, "Vrooom. Tank is now " + mTank);
        }
    }

    // We're being destroyed. It's important to dispose of the helper here!
    @Override
    public void onDestroy() {
        super.onDestroy();

        // very important:
        Log.d(TAG, "Destroying helper.");
        if (payment != null) {
            payment.dispose();
            payment = null;
        }
    }

    // updates UI to reflect model
    public void updateUi() {
        // update the car color to reflect premium status or lack thereof
        ((ImageView) findViewById(R.id.free_or_premium)).setImageResource(mIsPremium ? R.drawable.premium : R.drawable.free);

        // "Upgrade" button is only visible if the user is not premium
        findViewById(R.id.upgrade_button).setVisibility(mIsPremium ? View.GONE : View.VISIBLE);

        // "Get infinite gas" button is only visible if the user is not subscribed yet
        findViewById(R.id.infinite_gas_button).setVisibility(mSubscribedToInfiniteGas ?
                View.GONE : View.VISIBLE);

        // update gas gauge to reflect tank status
        if (mSubscribedToInfiniteGas) {
            ((ImageView) findViewById(R.id.gas_gauge)).setImageResource(R.drawable.gas_inf);
        } else {
            int index = mTank >= TANK_RES_IDS.length ? TANK_RES_IDS.length - 1 : mTank;
            ((ImageView) findViewById(R.id.gas_gauge)).setImageResource(TANK_RES_IDS[index]);
        }
    }

    // Enables or disables the "please wait" screen.
    void setWaitScreen(boolean set) {
        findViewById(R.id.screen_main).setVisibility(set ? View.GONE : View.VISIBLE);
        findViewById(R.id.screen_wait).setVisibility(set ? View.VISIBLE : View.GONE);
    }

    void complain(String message) {
        Log.e(TAG, "**** TrivialDrive Error: " + message);
        alert("Error: " + message);
    }

    void alert(String message) {
        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setMessage(message);
        bld.setNeutralButton("OK", null);
        Log.d(TAG, "Showing alert dialog: " + message);
        bld.create().show();
    }

    void saveData() {

        /*
         * WARNING: on a real application, we recommend you save data in a secure way to
         * prevent tampering. For simplicity in this sample, we simply store the data using a
         * SharedPreferences.
         */

        SharedPreferences.Editor spe = getPreferences(MODE_PRIVATE).edit();
        spe.putInt("tank", mTank);
        spe.commit();
        Log.d(TAG, "Saved data: tank = " + mTank);
    }

    void loadData() {
        SharedPreferences sp = getPreferences(MODE_PRIVATE);
        mTank = sp.getInt("tank", 2);
        Log.d(TAG, "Loaded data: tank = " + mTank);
    }
}
