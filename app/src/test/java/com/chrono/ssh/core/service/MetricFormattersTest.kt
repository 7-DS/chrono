package com.chrono.ssh.core.service

import java.util.Locale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricFormattersTest {
    @Test
    fun bytesLabelsScaleAtBinaryBoundaries() {
        assertEquals("0.00 B", MetricFormatters.bytesLabel(0))
        assertEquals("512.00 B", MetricFormatters.bytesLabel(512))
        assertEquals("1.00 K", MetricFormatters.bytesLabel(1024))
        assertEquals("1023.00 K", MetricFormatters.bytesLabel(1023L * 1024L))
        assertEquals("1.00 M", MetricFormatters.bytesLabel(1024L * 1024L))
        assertEquals("1023.00 M", MetricFormatters.bytesLabel(1023L * 1024L * 1024L))
        assertEquals("1.00 G", MetricFormatters.bytesLabel(1024L * 1024L * 1024L))
        assertEquals("1.00 T", MetricFormatters.bytesLabel(1024L * 1024L * 1024L * 1024L))
    }

    @Test
    fun capacityLabelsUseTheSameBinaryThresholdsAsByteLabels() {
        assertEquals("512.00 M", MetricFormatters.megaBytesLabel(512f))
        assertEquals("1.00 G", MetricFormatters.megaBytesLabel(1024f))
        assertEquals("1023.00 G", MetricFormatters.gigaBytesLabel(1023f))
        assertEquals("1.00 T", MetricFormatters.gigaBytesLabel(1024f))
    }

    @Test
    fun capacityPairLabelsKeepNumeratorAndDenominatorInTheSameUnit() {
        assertEquals("512.00 / 1023.00 M", MetricFormatters.megaBytesPairLabel(512f, 1023f))
        assertEquals("1.00 / 2.00 G", MetricFormatters.megaBytesPairLabel(1024f, 2048f))
        assertEquals("512.00 / 1023.00 G", MetricFormatters.gigaBytesPairLabel(512f, 1023f))
        assertEquals("1.00 / 2.00 T", MetricFormatters.gigaBytesPairLabel(1024f, 2048f))
    }

    @Test
    fun amountAndUnitLabelsPreserveByteUnitsForMetricCards() {
        assertEquals("512.00" to "M", MetricFormatters.amountAndUnitLabel("512.00 M"))
        assertEquals("1.00" to "G", MetricFormatters.amountAndUnitLabel("1024.00 M"))
        assertEquals("1.50" to "G/s", MetricFormatters.amountAndUnitLabel("1536.00 M/s"))
        assertEquals("999.00" to "K/s", MetricFormatters.amountAndUnitLabel("999.00 K/s"))
        assertEquals("--" to "", MetricFormatters.amountAndUnitLabel("--"))
    }

    @Test
    fun compactAmountAndUnitLabelsKeepStableReadablePrecision() {
        assertEquals("9.50" to "M", MetricFormatters.compactAmountAndUnitLabel("9.50 M"))
        assertEquals("12.34" to "M", MetricFormatters.compactAmountAndUnitLabel("12.34 M"))
        assertEquals("123.45" to "M", MetricFormatters.compactAmountAndUnitLabel("123.45 M"))
        assertEquals("1.50" to "G/s", MetricFormatters.compactAmountAndUnitLabel("1536.00 M/s"))
        assertEquals("--" to "", MetricFormatters.compactAmountAndUnitLabel("--"))
    }

    @Test
    fun rateLabelsAndZeroDetectionAreConsistent() {
        assertEquals("0.00 B/s", MetricFormatters.bytesPerSecondLabel(0))
        assertEquals("1.00 K/s", MetricFormatters.bytesPerSecondLabel(1024))
        assertTrue(MetricFormatters.isZeroRateLabel("0.00 B/s"))
        assertTrue(MetricFormatters.isZeroRateLabel("0.00B/s"))
        assertTrue(MetricFormatters.isZeroRateLabel("0 B/s"))
        assertTrue(MetricFormatters.isTinyRateLabel("0.01 B/s"))
        assertTrue(MetricFormatters.isTinyRateLabel("512B/s"))
        assertFalse(MetricFormatters.isTinyRateLabel("1.00 K/s"))
        assertFalse(MetricFormatters.isTinyRateLabel("1.00K/s"))
        assertFalse(MetricFormatters.isZeroRateLabel("0.01 B/s"))
        assertFalse(MetricFormatters.isZeroRateLabel("0.00 B"))
    }

    @Test
    fun homeUsageLabelsPreferTotalsForIdleOrTinyRates() {
        assertEquals("5.00 G", MetricFormatters.homeUsageOrRateLabel("0.00 B/s", "5.00 G"))
        assertEquals("5.00 G", MetricFormatters.homeUsageOrRateLabel("512.00 B/s", "5.00 G"))
        assertEquals("2.00 K/s", MetricFormatters.homeUsageOrRateLabel("2.00 K/s", "5.00 G"))
        assertEquals("--", MetricFormatters.homeUsageOrRateLabel("0.00 B/s", "--"))
    }

    @Test
    fun homeTotalLabelsPreferStableUsageValuesForCards() {
        assertEquals("5.00 G", MetricFormatters.homeTotalOrRateLabel("2.00 M/s", "5.00 G"))
        assertEquals("2.00 M/s", MetricFormatters.homeTotalOrRateLabel("2.00 M/s", "--"))
        assertEquals("2.00 M/s", MetricFormatters.homeTotalOrRateLabel("2.00 M/s", "0.00 B"))
        assertEquals("--", MetricFormatters.homeTotalOrRateLabel("512.00 B/s", "0.00 B"))
    }

    @Test
    fun rateMagnitudeAcceptsShortAndLongUnits() {
        assertEquals(1f, MetricFormatters.rateMagnitude("1.00 M"), 0.001f)
        assertEquals(1f, MetricFormatters.rateMagnitude("1.00 G"), 0.001f)
        assertEquals(1f, MetricFormatters.rateMagnitude("1.00 GB"), 0.001f)
        assertEquals(1f, MetricFormatters.rateMagnitude("1.00 T"), 0.001f)
        assertEquals(0.5f, MetricFormatters.rateMagnitude("0.50 M"), 0.001f)
        assertEquals(1f / 1024f, MetricFormatters.rateMagnitude("1.00 K"), 0.001f)
        assertEquals(512f / (1024f * 1024f), MetricFormatters.rateMagnitude("512.00 B"), 0.000001f)
        assertEquals(0f, MetricFormatters.rateMagnitude("--"), 0.001f)
    }

    @Test
    fun labelsUseStableDotDecimalRegardlessOfDefaultLocale() {
        val previous = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)

            assertEquals("1.50 K", MetricFormatters.bytesLabel(1536))
            assertEquals("1.50 K/s", MetricFormatters.bytesPerSecondLabel(1536))
            assertEquals("1.50" to "K/s", MetricFormatters.amountAndUnitLabel("1536.00 B/s"))
            assertEquals("1.50 / 2.00 G", MetricFormatters.gigaBytesPairLabel(1.5f, 2f))
        } finally {
            Locale.setDefault(previous)
        }
    }
}
