package io.bsoft.bconverter.feature.editor

import org.robolectric.RuntimeEnvironment
import io.bsoft.bconverter.domain.filter.FilterGroup
import io.bsoft.bconverter.domain.filter.FilterParam
import io.bsoft.bconverter.domain.filter.FilterPresets
import io.bsoft.bconverter.domain.filter.ToneMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class FilterLabelsTest {
    private val context = RuntimeEnvironment.getApplication() as android.content.Context

    @Test fun every_preset_label_is_the_resource_named_by_its_key() {
        for (p in FilterPresets.all) {
            val byName = context.resources.getIdentifier(p.nameKey, "string", context.packageName)
            assertTrue(p.id, byName != 0)
            assertEquals(p.id, byName, FilterLabels.preset(p.id))
        }
    }
    @Test fun every_param_group_and_tone_has_a_non_empty_label() {
        for (p in FilterParam.entries) assertTrue(p.key, context.getString(FilterLabels.param(p)).isNotBlank())
        for (g in FilterGroup.entries) assertTrue(g.name, context.getString(FilterLabels.group(g)).isNotBlank())
        for (t in ToneMode.entries) assertTrue(t.name, context.getString(FilterLabels.tone(t)).isNotBlank())
    }
}
