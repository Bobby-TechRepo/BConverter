package io.bsoft.bconverter

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Test

class AppIdentityTest {
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun package_name_matches_spec() = assertEquals("io.bsoft.bconverter", context.packageName)
    @Test fun app_label_is_BConverter() = assertEquals("BConverter", context.getString(R.string.app_name))
}
