package com.jr12866.dartcoach.billing

import android.app.Activity
import android.content.Context
import android.util.Log
import com.android.billingclient.api.AcknowledgePurchaseParams
import com.android.billingclient.api.BillingClient
import com.android.billingclient.api.BillingClientStateListener
import com.android.billingclient.api.BillingFlowParams
import com.android.billingclient.api.BillingResult
import com.android.billingclient.api.PendingPurchasesParams
import com.android.billingclient.api.ProductDetails
import com.android.billingclient.api.Purchase
import com.android.billingclient.api.PurchasesUpdatedListener
import com.android.billingclient.api.QueryProductDetailsParams
import com.android.billingclient.api.QueryPurchasesParams
import com.android.billingclient.api.acknowledgePurchase
import com.android.billingclient.api.queryProductDetails
import com.android.billingclient.api.queryPurchasesAsync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

/**
 * The Pro purchase.
 *
 * Pro is a one-time purchase, and it is a non-consumable: the player buys
 * it once and keeps it, including across reinstalls, because Play holds the
 * purchase and the app reads it back.
 *
 * There is no server. The purchase lives in the Play Store, the app asks
 * Play what the player owns, and the answer is cached on the phone so the
 * app still works when Play cannot be reached. That is what keeps "no
 * account" true: the app never asks the player who they are.
 */
class BillingRepository(private val context: Context) {

    /**
     * The work that the purchase callback needs.
     *
     * The callback Play makes is not a coroutine, so the work that does -
     * acknowledging a purchase and asking what the player owns - is handed
     * to this.
     */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** What the player owns. */
    private val _entitlement = MutableStateFlow(false)

    /** What the player owns, as the screen sees it. */
    val entitlement: StateFlow<Boolean> = _entitlement.asStateFlow()

    /** The price Play shows for Pro, in the player's own currency. */
    private val _price = MutableStateFlow<String?>(null)

    /** The price, as the screen sees it. */
    val price: StateFlow<String?> = _price.asStateFlow()

    /** What went wrong, if something did. */
    private val _error = MutableStateFlow<String?>(null)

    /** The error, as the screen sees it. */
    val error: StateFlow<String?> = _error.asStateFlow()

    /** The details Play gave us for Pro. */
    private var productDetails: ProductDetails? = null

    /**
     * Told when a purchase finishes, so the app can save the entitlement.
     */
    var onPurchased: (() -> Unit)? = null

    private val listener = PurchasesUpdatedListener { result, purchases ->
        when (result.responseCode) {
            BillingClient.BillingResponseCode.OK -> {
                var bought = false
                purchases?.forEach {
                    if (it.products.contains(PRODUCT_ID) &&
                        it.purchaseState == Purchase.PurchaseState.PURCHASED
                    ) {
                        bought = true
                    }
                }
                // The callback is not a coroutine, so the work that needs one
                // is handed to the scope.
                scope.launch {
                    purchases?.forEach { handlePurchase(it) }
                    refreshEntitlement()
                    if (bought) {
                        onPurchased?.invoke()
                    }
                }
            }

            BillingClient.BillingResponseCode.USER_CANCELED -> {
                // The player backed out. That is not an error and it is not
                // worth a message.
            }

            else -> {
                _error.value = "The purchase did not go through."
                Log.w("DartsCoach", "Purchase failed: ${result.debugMessage}")
            }
        }
    }

    private val client: BillingClient by lazy {
        BillingClient.newBuilder(context)
            .setListener(listener)
            .enablePendingPurchases(
                PendingPurchasesParams.newBuilder()
                    .enableOneTimeProducts()
                    .build()
            )
            .enableAutoServiceReconnection()
            .build()
    }

    /**
     * Connects to Play and works out what the player owns.
     *
     * This is called when the app starts. If Play cannot be reached, the
     * cached entitlement is used, so the app still works offline.
     */
    suspend fun connect() {
        val connected = awaitConnection()
        if (!connected) {
            Log.i("DartsCoach", "Play Billing would not connect")
            return
        }
        refreshEntitlement()
        queryPrice()
    }

    /**
     * Waits for the connection to Play.
     *
     * The library reconnects by itself if the connection drops, so this only
     * has to handle the first attempt.
     */
    private suspend fun awaitConnection(): Boolean =
        suspendCancellableCoroutine { continuation ->
            try {
                client.startConnection(object : BillingClientStateListener {
                    override fun onBillingSetupFinished(result: BillingResult) {
                        continuation.resume(
                            result.responseCode == BillingClient.BillingResponseCode.OK
                        )
                    }

                    override fun onBillingServiceDisconnected() {
                        // The library reconnects on the next call, so there is
                        // nothing to do here.
                    }
                })
            } catch (e: Exception) {
                Log.w("DartsCoach", "Billing connection threw", e)
                continuation.resume(false)
            }
        }

    /**
     * Asks Play what the player owns.
     *
     * A non-consumable purchase is owned for good, so this is the whole
     * entitlement check: if Play says the player has it, they have it.
     */
    suspend fun refreshEntitlement() {
        try {
            val result = client.queryPurchasesAsync(
                QueryPurchasesParams.newBuilder()
                    .setProductType(BillingClient.ProductType.INAPP)
                    .build()
            )
            val owned = result.purchasesList
                .any { it.products.contains(PRODUCT_ID) }
            _entitlement.value = owned
        } catch (e: Exception) {
            Log.w("DartsCoach", "Could not read the purchases", e)
        }
    }

    /**
     * Asks Play what Pro costs.
     *
     * The price comes from Play rather than being written into the app, so
     * it is right for the player's country and their currency.
     */
    suspend fun queryPrice() {
        try {
            val result = client.queryProductDetails(
                QueryProductDetailsParams.newBuilder()
                    .setProductList(
                        listOf(
                            QueryProductDetailsParams.Product.newBuilder()
                                .setProductId(PRODUCT_ID)
                                .setProductType(BillingClient.ProductType.INAPP)
                                .build()
                        )
                    )
                    .build()
            )
            val details = result.productDetailsList?.firstOrNull()
            if (details == null) {
                Log.w(
                    "DartsCoach",
                    "Play did not return the product details: " +
                        "${result.billingResult.debugMessage}",
                )
                return
            }
            productDetails = details
            _price.value = details.oneTimePurchaseOfferDetailsList
                ?.firstOrNull()
                ?.formattedPrice
        } catch (e: Exception) {
            Log.w("DartsCoach", "Could not read the price", e)
        }
    }

    /**
     * Starts the purchase.
     *
     * Returns false when the purchase could not be started, so the screen
     * can say so rather than doing nothing.
     */
    suspend fun purchase(activity: Activity): Boolean {
        val details = productDetails
        if (details == null) {
            // The price is not known yet, so the details are asked for now.
            queryPrice()
            _error.value = "The price is not known yet. Try again in a moment."
            return false
        }

        val offerToken = details.oneTimePurchaseOfferDetailsList
            ?.firstOrNull()
            ?.offerToken
        if (offerToken == null) {
            _error.value = "The purchase could not be started."
            return false
        }

        val params = BillingFlowParams.newBuilder()
            .setProductDetailsParamsList(
                listOf(
                    BillingFlowParams.ProductDetailsParams.newBuilder()
                        .setProductDetails(details)
                        .setOfferToken(offerToken)
                        .build()
                )
            )
            .build()

        val result = client.launchBillingFlow(activity, params)
        if (result.responseCode != BillingClient.BillingResponseCode.OK) {
            _error.value = "The purchase could not be started."
            Log.w("DartsCoach", "The billing flow failed: ${result.debugMessage}")
            return false
        }
        return true
    }

    /**
     * Does what needs doing to a purchase once it has been made.
     *
     * A non-consumable has to be acknowledged, or Play refunds it after
     * three days and takes the entitlement away again.
     */
    private suspend fun handlePurchase(purchase: Purchase) {
        if (purchase.products.contains(PRODUCT_ID) &&
            purchase.purchaseState == Purchase.PurchaseState.PURCHASED &&
            !purchase.isAcknowledged
        ) {
            try {
                client.acknowledgePurchase(
                    AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.purchaseToken)
                        .build()
                )
            } catch (e: Exception) {
                Log.w("DartsCoach", "Could not acknowledge the purchase", e)
            }
        }
    }

    /**
     * Checks the purchases again.
     *
     * This is the restore button. Play holds the purchase, so there is
     * nothing to restore from - asking Play what the player owns is the
     * whole of it.
     */
    suspend fun restore(): Boolean {
        awaitConnection()
        refreshEntitlement()
        return _entitlement.value
    }

    companion object {
        /**
         * The product id of Pro in the Play Console.
         *
         * It has to match the product set up in the Play Console exactly.
         */
        const val PRODUCT_ID = "dartcoach_pro"

        /**
         * The price shown when Play has not told us the real one.
         *
         * It is only a placeholder: the price that is actually shown comes
         * from Play, so it is right for the player's country.
         */
        const val FALLBACK_PRICE = "$2.99"
    }
}
