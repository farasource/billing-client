[![](https://jitpack.io/v/farasource/billing-client.svg)](https://jitpack.io/#farasource/billing-client)
-
GooglePlay/CafeBazaar/Myket/Other In-app purchase sdk

## Getting Started

To start working with billing-client, you need to add its dependency into your `build.gradle` file:
### Dependency
```groovy
dependencies {
    implementation "com.github.farasource:billing-client:[latest_version]"
}
```

Then you need to add jitpack as your maven repository in `build.gradle`  file:

```groovy
repositories {
    google()
    jcenter()
    maven { url 'https://jitpack.io' }
}
```

Then you need to add config in `build.gradle`  file:

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

    payment = new Payment(getActivityResultRegistry(), this, BuildConfig.IAB_PUBLIC_KEY);
    payment.setCanAutoConsume(false);
    payment.setOnPaymentResultListener(new OnPaymentResultListener() {
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

* launchPayment
```JAVA
payment.launchPayment(SKU_INFINITE_GAS, IabHelper.ITEM_TYPE_SUBS, payload);
// or
payment.launchPayment(sku, IabHelper.ITEM_TYPE_SUBS, payload, canAutoConsume);
```

* consume
```JAVA
consume(purchase);
```

* onDestroy
```JAVA
@Override
public void onDestroy() {
    super.onDestroy();
    if (payment != null) {
        payment.dispose();
        payment = null;
    }
}
```