package app.narra.plus

import com.android.billingclient.api.Purchase.PurchaseState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlusTest {

    @Test
    fun soloUnaCompraPagadaDePlusDesbloquea() {
        assertTrue(unlocks(listOf("narra_plus"), PurchaseState.PURCHASED))
        assertFalse(unlocks(listOf("narra_plus"), PurchaseState.PENDING))
        assertFalse(unlocks(listOf("otro_producto"), PurchaseState.PURCHASED))
        assertFalse(unlocks(emptyList(), PurchaseState.PURCHASED))
    }
}
