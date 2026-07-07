package com.chrono.ssh.core.data

import org.junit.Assert.assertEquals
import org.junit.Test

class MetricDetailsRefreshPolicyTest {
    @Test
    fun detailsAreDueAfterFirstFastMetricsRefresh() {
        assertEquals(true, MetricDetailsRefreshPolicy.due(null, nowEpochMillis = 2_000L, intervalMillis = 60_000L))
        assertEquals(false, MetricDetailsRefreshPolicy.due(2_000L, nowEpochMillis = 61_999L, intervalMillis = 60_000L))
        assertEquals(true, MetricDetailsRefreshPolicy.due(2_000L, nowEpochMillis = 62_000L, intervalMillis = 60_000L))
    }

    @Test
    fun collectionRunsForFirstDetailRefresh() {
        assertEquals(true, MetricDetailsCollectionPolicy.shouldCollect(null, nowEpochMillis = 2_000L, intervalMillis = 60_000L))
    }

    @Test
    fun autoCardRefreshCollectsBoundedDetailsWhenDue() {
        assertEquals(true, shouldCollectMetricDetailsDuringRefresh(detailsDue = true))
        assertEquals(false, shouldCollectMetricDetailsDuringRefresh(detailsDue = false))
        assertEquals(true, shouldCollectMetricDetailsDuringRefresh(detailsDue = false, forceDetails = true))
    }
}
