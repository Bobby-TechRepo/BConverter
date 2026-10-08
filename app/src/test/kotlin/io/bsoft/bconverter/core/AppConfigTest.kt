package io.bsoft.bconverter.core.common

import org.junit.Assert.assertEquals
import org.junit.Test

class AppConfigTest {
    @Test fun application_id_is_io_bsoft_bconverter() =
        assertEquals("io.bsoft.bconverter", AppConfig.APPLICATION_ID)

    @Test fun application_name_is_BConverter() =
        assertEquals("BConverter", AppConfig.APP_NAME)
}
