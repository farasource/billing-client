[![](https://jitpack.io/v/farasource/billing-client.svg)](https://jitpack.io/#farasource/billing-client)
-
CafeBazaar/Myket in-app purchase sdk

## Getting Started

**Step 1.** Add the dependency in your root `build.gradle`:
```groovy
dependencies {
    implementation "com.github.farasource:billing-client:[latest_version]"
}
```

**Step 2.** Add it at the end of repositories:
```groovy
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven { url 'https://jitpack.io' }
    }
}
```

**Step 3.** Add config:
```GRADLE
android {
    defaultConfig {
        def marketApplicationId = "com.farsitel.bazaar" // com.android.vending or ir.mservices.market
        def marketBindAddress = "ir.cafebazaar.pardakht.InAppBillingService.BIND" // com.android.vending.billing.InAppBillingService.BIND or ir.mservices.market.InAppBillingService.BIND
        manifestPlaceholders = [marketApplicationId: "${marketApplicationId}",
                                marketBindAddress  : "${marketBindAddress}",
                                marketPermission   : "${marketApplicationId}.permission.PAY_THROUGH_BAZAAR"] // .BILLING
    }
}
```
## Sample
There is a fully functional sample application that demonstrates the usage of billing-client, all you have to do is cloning the project and running the [sample](https://github.com/farasource/billing-client/tree/master/sample) module.
## How to use
### AppCompatActivity

* onCreate
```JAVA
@Override
public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);

    billingClient = new BillingClient(getActivityResultRegistry(), this);
    billingClient.setGlobalAutoConsume(false);
    billingClient.setOnBillingResultListener(new OnBillingResultListener() {
        @Override
        public void onBillingSuccess(Purchase purchase) {
            //
        }
        
        @Override
        public void onConsumeFinished(Purchase purchase, boolean success) {
            //
        }
        
        @Override
        public void onBillingStatus(int code) {
            if (code != TableCodes.SETUP_SUCCESS) {
                //
            }
        }
        
        @Override
        public void onQueryInventoryFinished(Inventory inventory) {
            //
        }
    });
}
```

* launchBilling
```JAVA
billingClient.launchBilling(sku);
// or
billingClient.launchBilling(sku, IabHelper.ITEM_TYPE_SUBS, payload, canAutoConsume);
```

* consume
```JAVA
billingClient.consume(purchase);
```

* onDestroy
```JAVA
@Override
public void onDestroy() {
    super.onDestroy();
    if (billingClient != null) {
        billingClient.endConnection();
        billingClient = null;
    }
}
```