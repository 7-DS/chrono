package com.chrono.ssh.core.service

import com.chrono.ssh.core.model.VnStatRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VnStatParserTest {
    @Test
    fun parsesDayWeekMonthAndYearUsageTotals() {
        val json = """
            {
              "interfaces": [{
                "name": "eth0",
                "traffic": {
                  "day": [
                    {"id": 1, "date": {"year": 2026, "month": 6, "day": 22}, "rx": 1048576, "tx": 2097152},
                    {"id": 2, "date": {"year": 2026, "month": 6, "day": 23}, "rx": 1048576, "tx": 1048576},
                    {"id": 3, "date": {"year": 2026, "month": 6, "day": 24}, "rx": 1048576, "tx": 1048576},
                    {"id": 4, "date": {"year": 2026, "month": 6, "day": 25}, "rx": 1048576, "tx": 1048576},
                    {"id": 5, "date": {"year": 2026, "month": 6, "day": 26}, "rx": 1048576, "tx": 1048576},
                    {"id": 6, "date": {"year": 2026, "month": 6, "day": 27}, "rx": 1048576, "tx": 1048576},
                    {"id": 7, "date": {"year": 2026, "month": 6, "day": 28}, "rx": 1048576, "tx": 1048576}
                  ],
                  "month": [
                    {"date": {"year": 2026, "month": 6}, "rx": 10485760, "tx": 20971520}
                  ],
                  "year": [
                    {"date": {"year": 2026}, "rx": 1073741824, "tx": 2147483648}
                  ],
                  "fiveminute": [
                    {"date": {"year": 2026, "month": 6, "day": 28}, "rx": 999, "tx": 999}
                  ]
                }
              }, {
                "name": "wlan0",
                "traffic": {
                  "day": [
                    {"id": 1, "date": {"year": 2026, "month": 6, "day": 28}, "rx": 1024, "rxk": 0, "tx": 1024, "txk": 0}
                  ],
                  "month": [
                    {"date": {"year": 2026, "month": 6}, "rx": 1024, "rxk": 0, "tx": 1024, "txk": 0}
                  ],
                  "year": [
                    {"date": {"year": 2026}, "rx": 1024, "rxk": 0, "tx": 1024, "txk": 0}
                  ]
                }
              }]
            }
        """.trimIndent()

        val usage = VnStatParser.parse(json)!!

        assertEquals(4L * 1024 * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
        assertEquals(17L * 1024 * 1024, usage.forRange(VnStatRange.Week)!!.totalBytes)
        assertEquals(32L * 1024 * 1024, usage.forRange(VnStatRange.Month)!!.totalBytes)
        assertEquals(3L * 1024 * 1024 * 1024 + 2L * 1024 * 1024, usage.forRange(VnStatRange.Year)!!.totalBytes)
        assertTrue(usage.forRange(VnStatRange.Week)!!.label.contains("2026-06-22"))
    }

    @Test
    fun doesNotUseFiveMinuteSamplesAsPeriodUsage() {
        val json = """
            {"interfaces":[{"traffic":{"fiveminute":[{"rx":999999,"tx":999999}]}}]}
        """.trimIndent()

        val usage = VnStatParser.parse(json)

        assertNull(usage)
    }

    @Test
    fun parsesLegacyPluralPeriodArrays() {
        val json = """
            {
              "interfaces": [{
                "traffic": {
                  "days": [
                    {"date": {"year": 2026, "month": 6, "day": 27}, "rx": 1, "rxk": 0, "tx": 2, "txk": 0},
                    {"date": {"year": 2026, "month": 6, "day": 28}, "rx": 3, "rxk": 512, "tx": 4, "txk": 512}
                  ],
                  "months": [
                    {"date": {"year": 2026, "month": 6}, "rx": 10, "rxk": 0, "tx": 20, "txk": 0}
                  ],
                  "years": [
                    {"date": {"year": 2026}, "rx": 100, "rxk": 0, "tx": 200, "txk": 0}
                  ]
                }
              }]
            }
        """.trimIndent()

        val usage = VnStatParser.parse(json)!!

        assertEquals(8L * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
        assertEquals(11L * 1024, usage.forRange(VnStatRange.Week)!!.totalBytes)
        assertEquals(30L * 1024, usage.forRange(VnStatRange.Month)!!.totalBytes)
        assertEquals(300L * 1024, usage.forRange(VnStatRange.Year)!!.totalBytes)
    }

    @Test
    fun treatsModernVnStatJsonPlainCountersAsKib() {
        val json = """
            {
              "vnstatversion": "2.10",
              "jsonversion": "2",
              "interfaces": [{
                "name": "eth0",
                "traffic": {
                  "day": [
                    {"date": {"year": 2026, "month": 6, "day": 29}, "rx": 2048, "tx": 1024}
                  ],
                  "month": [
                    {"date": {"year": 2026, "month": 6}, "rx": 4096, "tx": 2048}
                  ],
                  "year": [
                    {"date": {"year": 2026}, "rx": 8192, "tx": 4096}
                  ]
                }
              }]
            }
        """.trimIndent()

        val usage = VnStatParser.parse(json)!!

        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Week)!!.totalBytes)
        assertEquals(6L * 1024 * 1024, usage.forRange(VnStatRange.Month)!!.totalBytes)
        assertEquals(12L * 1024 * 1024, usage.forRange(VnStatRange.Year)!!.totalBytes)
    }

    @Test
    fun prefersExplicitWeekBucketsWhenAvailable() {
        val json = """
            {
              "interfaces": [{
                "traffic": {
                  "day": [
                    {"date": {"year": 2026, "month": 6, "day": 29}, "rx": 1048576, "tx": 1048576}
                  ],
                  "week": [
                    {"date": {"year": 2026, "month": 6, "day": 22}, "rx": 10485760, "tx": 20971520}
                  ]
                }
              }, {
                "traffic": {
                  "weeks": [
                    {"date": {"year": 2026, "month": 6, "day": 22}, "rx": 1048576, "tx": 1048576}
                  ]
                }
              }]
            }
        """.trimIndent()

        val usage = VnStatParser.parse(json)!!

        assertEquals(32L * 1024 * 1024, usage.forRange(VnStatRange.Week)!!.totalBytes)
    }

    @Test
    fun sumsIdenticalBucketsFromDifferentInterfaces() {
        val json = """
            {
              "jsonversion": "2",
              "interfaces": [{
                "name": "eth0",
                "traffic": {
                  "day": [
                    {"date": {"year": 2026, "month": 6, "day": 30}, "rx": 1024, "tx": 1024}
                  ]
                }
              }, {
                "name": "eth1",
                "traffic": {
                  "day": [
                    {"date": {"year": 2026, "month": 6, "day": 30}, "rx": 1024, "tx": 1024}
                  ]
                }
              }]
            }
        """.trimIndent()

        val usage = VnStatParser.parse(json)!!

        assertEquals(4L * 1024 * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
        assertEquals(4L * 1024 * 1024, usage.forRange(VnStatRange.Week)!!.totalBytes)
    }

    @Test
    fun ignoresLifetimeTotalWhenPeriodBucketsAreMissing() {
        val json = """
            {
              "interfaces": [{
                "traffic": {
                  "total": [{"rx": 1048576, "tx": 2097152}]
                }
              }]
            }
        """.trimIndent()

        val usage = VnStatParser.parse(json)

        assertNull(usage)
    }

    @Test
    fun ignoresSingleLifetimeTotalObjectWhenPeriodBucketsAreMissing() {
        val json = """
            {
              "interfaces": [{
                "name": "eth0",
                "traffic": {
                  "total": {"rx": 1048576, "tx": 2097152}
                }
              }, {
                "name": "eth1",
                "traffic": {
                  "total": {"rx": {"bytes": 3145728}, "tx": {"bytes": 1048576}}
                }
              }]
            }
        """.trimIndent()

        val usage = VnStatParser.parse(json)

        assertNull(usage)
    }

    @Test
    fun parsesQuotedAndNestedTrafficNumbers() {
        val json = """
            {
              "interfaces": [{
                "traffic": {
                  "day": [
                    {"date": {"year": 2026, "month": 6, "day": 28}, "rx": "1048576", "tx": "2097152"}
                  ],
                  "month": [
                    {"date": {"year": 2026, "month": 6}, "rx": {"bytes": 3145728}, "tx": {"bytes": 1048576}}
                  ],
                  "year": [
                    {"date": {"year": 2026}, "rx": {"kib": 1024}, "tx": {"kib": 2048}}
                  ]
                }
              }]
            }
        """.trimIndent()

        val usage = VnStatParser.parse(json)!!

        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
        assertEquals(4L * 1024 * 1024, usage.forRange(VnStatRange.Month)!!.totalBytes)
        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Year)!!.totalBytes)
    }

    @Test
    fun parsesVnStatNestedUnitVariants() {
        val json = """
            {
              "interfaces": [{
                "traffic": {
                  "day": [
                    {"date": {"year": 2026, "month": 6, "day": 29}, "rx": {"kibibytes": 1024}, "tx": {"mebibytes": 2}}
                  ],
                  "month": [
                    {"date": {"year": 2026, "month": 6}, "rx": {"gibibytes": 1}, "tx": {"bytes": 1048576}}
                  ],
                  "year": [
                    {"date": {"year": 2026}, "rx": {"KiB": 512}, "tx": {"MiB": 3}}
                  ]
                }
              }]
            }
        """.trimIndent()

        val usage = VnStatParser.parse(json)!!

        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
        assertEquals(1025L * 1024 * 1024, usage.forRange(VnStatRange.Month)!!.totalBytes)
        assertEquals((512L * 1024) + (3L * 1024 * 1024), usage.forRange(VnStatRange.Year)!!.totalBytes)
    }

    @Test
    fun parsesConcatenatedFallbackPeriodJson() {
        val dayJson = """
            {"interfaces":[{"traffic":{"day":[{"date":{"year":2026,"month":6,"day":28},"rx":1048576,"tx":1048576}]}}]}
        """.trimIndent()
        val monthJson = """
            {"interfaces":[{"traffic":{"month":[{"date":{"year":2026,"month":6},"rx":2097152,"tx":1048576}]}}]}
        """.trimIndent()
        val yearJson = """
            {"interfaces":[{"traffic":{"year":[{"date":{"year":2026},"rx":4194304,"tx":1048576}]}}]}
        """.trimIndent()

        val usage = VnStatParser.parse("$dayJson\n$monthJson\n$yearJson")!!

        assertEquals(2L * 1024 * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
        assertEquals(2L * 1024 * 1024, usage.forRange(VnStatRange.Week)!!.totalBytes)
        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Month)!!.totalBytes)
        assertEquals(5L * 1024 * 1024, usage.forRange(VnStatRange.Year)!!.totalBytes)
    }

    @Test
    fun parsesSingleObjectPeriodBuckets() {
        val json = """
            {
              "interfaces": [{
                "traffic": {
                  "day": {"date": {"year": 2026, "month": 6, "day": 29}, "rx": 1048576, "tx": 2097152},
                  "month": {"date": {"year": 2026, "month": 6}, "rx": 3145728, "tx": 1048576},
                  "year": {"date": {"year": 2026}, "rx": 5242880, "tx": 1048576}
                }
              }]
            }
        """.trimIndent()

        val usage = VnStatParser.parse(json)!!

        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Week)!!.totalBytes)
        assertEquals(4L * 1024 * 1024, usage.forRange(VnStatRange.Month)!!.totalBytes)
        assertEquals(6L * 1024 * 1024, usage.forRange(VnStatRange.Year)!!.totalBytes)
    }

    @Test
    fun parsesDecimalNumericStringsAsWholeBytes() {
        val json = """
            {
              "interfaces": [{
                "traffic": {
                  "day": [
                    {"date": {"year": 2026, "month": 6, "day": 29}, "rx": "1048576.0", "tx": "2097152.9"}
                  ]
                }
              }]
            }
        """.trimIndent()

        val usage = VnStatParser.parse(json)!!

        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
    }

    @Test
    fun parsesExplicitRxTxByteFields() {
        val json = """
            {
              "interfaces": [{
                "traffic": {
                  "day": [
                    {"date": {"year": 2026, "month": 6, "day": 29}, "rx_bytes": 1048576, "tx_bytes": 2097152}
                  ],
                  "week": [
                    {"date": {"year": 2026, "month": 6, "day": 22}, "rx_bytes": 3145728, "tx_bytes": 1048576}
                  ],
                  "month": [
                    {"date": {"year": 2026, "month": 6}, "rx_bytes": 5242880, "tx_bytes": 1048576}
                  ],
                  "year": [
                    {"date": {"year": 2026}, "rx_bytes": 7340032, "tx_bytes": 1048576}
                  ]
                }
              }]
            }
        """.trimIndent()

        val usage = VnStatParser.parse(json)!!

        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
        assertEquals(4L * 1024 * 1024, usage.forRange(VnStatRange.Week)!!.totalBytes)
        assertEquals(6L * 1024 * 1024, usage.forRange(VnStatRange.Month)!!.totalBytes)
        assertEquals(8L * 1024 * 1024, usage.forRange(VnStatRange.Year)!!.totalBytes)
    }

    @Test
    fun parsesNoisyConcatenatedVnStatCommandOutput() {
        val json = """
            vnStat 2.10 by Teemu Toivola
            warning: database update skipped
            {"interfaces":[{"name":"eth0","traffic":{"day":[{"date":{"year":2026,"month":6,"day":29},"rx":1048576,"tx":1048576}]}}]}
            Error: no matching interface for "docker0"
            {"interfaces":[{"name":"eth0","traffic":{"month":[{"date":{"year":2026,"month":6},"rx":2097152,"tx":1048576}],"year":[{"date":{"year":2026},"rx":4194304,"tx":1048576}]}}]}
        """.trimIndent()

        val usage = VnStatParser.parse(json)!!

        assertEquals(2L * 1024 * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
        assertEquals(2L * 1024 * 1024, usage.forRange(VnStatRange.Week)!!.totalBytes)
        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Month)!!.totalBytes)
        assertEquals(5L * 1024 * 1024, usage.forRange(VnStatRange.Year)!!.totalBytes)
    }

    @Test
    fun ignoresNoisyOutputWithoutUsablePeriodJson() {
        val json = """
            Error: no database found
            {"version":"2.10","interfaces":[]}
            vnstat-not-found
        """.trimIndent()

        val usage = VnStatParser.parse(json)

        assertNull(usage)
    }

    @Test
    fun describesInstalledVnStatWithNoPeriodTotals() {
        val json = """{"jsonversion":"2","interfaces":[{"name":"eth0","traffic":{"total":{"rx":0,"tx":0}}}]}"""

        val description = VnStatParser.describe(json, VnStatParser.parse(json))

        assertTrue(description.contains("installed or callable"))
        assertTrue(description.contains("no parsed totals"))
        assertTrue(description.contains("keys=total"))
    }

    @Test
    fun describesNonJsonVnStatOutputWithFirstSignal() {
        val output = """
            Error: No database found, nothing to do.
            Try --add to add a database.
        """.trimIndent()

        val description = VnStatParser.describe(output, null)

        assertTrue(description.contains("no usable JSON"))
        assertTrue(description.contains("No database found"))
    }

    @Test
    fun deduplicatesRepeatedFallbackBucketsFromMultipleVnStatCommands() {
        val bucket = """{"date":{"year":2026,"month":6,"day":29},"rx":1048576,"tx":2097152}"""
        val json = """
            {"interfaces":[{"name":"eth0","traffic":{"day":[$bucket]}}]}
            {"interfaces":[{"name":"eth0","traffic":{"day":[$bucket]}}]}
            {"interfaces":[{"name":"eth0","traffic":{"day":[$bucket]}}]}
        """.trimIndent()

        val usage = VnStatParser.parse(json)!!

        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Week)!!.totalBytes)
    }

    @Test
    fun deduplicatesFallbackBucketsWithDifferentMetadataOrder() {
        val json = """
            {"interfaces":[{"name":"eth0","traffic":{"day":[{"id":1,"date":{"year":2026,"month":6,"day":29},"rx":1048576,"tx":2097152}]}}]}
            {"interfaces":[{"name":"eth0","traffic":{"day":[{"tx":2097152,"date":{"day":29,"month":6,"year":2026},"rx":1048576,"id":99}]}}]}
        """.trimIndent()

        val usage = VnStatParser.parse(json)!!

        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Week)!!.totalBytes)
    }

    @Test
    fun appliesPlainCounterUnitsPerVnStatJsonFragment() {
        val modernJson = """
            {"jsonversion":"2","interfaces":[{"name":"eth0","traffic":{"day":[{"date":{"year":2026,"month":6,"day":28},"rx":1024,"tx":1024}]}}]}
        """.trimIndent()
        val explicitBytesFallback = """
            {"interfaces":[{"name":"eth0","traffic":{"day":[{"date":{"year":2026,"month":6,"day":29},"rx_bytes":1048576,"tx_bytes":1048576}]}}]}
        """.trimIndent()

        val usage = VnStatParser.parse("$modernJson\n$explicitBytesFallback")!!

        assertEquals(2L * 1024 * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
        assertEquals(4L * 1024 * 1024, usage.forRange(VnStatRange.Week)!!.totalBytes)
    }

    @Test
    fun parsesNestedValueUnitTrafficFields() {
        val json = """
            {
              "interfaces": [{
                "traffic": {
                  "day": [
                    {"date": {"year": 2026, "month": 6, "day": 29}, "rx": {"value": 2, "unit": "MiB"}, "tx": {"amount": 1, "units": "MiB"}}
                  ],
                  "month": [
                    {"date": {"year": 2026, "month": 6}, "rx": {"value": 1, "unit": "GiB"}, "tx": {"value": 512, "unit": "MiB"}}
                  ],
                  "year": [
                    {"date": {"year": 2026}, "rx": {"value": 1, "unit": "TiB"}, "tx": {"value": 1, "unit": "GiB"}}
                  ]
                }
              }]
            }
        """.trimIndent()

        val usage = VnStatParser.parse(json)!!

        assertEquals(3L * 1024 * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
        assertEquals(1536L * 1024 * 1024, usage.forRange(VnStatRange.Month)!!.totalBytes)
        assertEquals((1024L + 1L) * 1024 * 1024 * 1024, usage.forRange(VnStatRange.Year)!!.totalBytes)
    }

    @Test
    fun scalarTrafficDoesNotReadNextNestedTrafficObject() {
        val json = """
            {
              "interfaces": [{
                "traffic": {
                  "day": [
                    {"date": {"year": 2026, "month": 6, "day": 29}, "rx": 1048576, "tx": {"value": 1, "unit": "MiB"}}
                  ]
                }
              }]
            }
        """.trimIndent()

        val usage = VnStatParser.parse(json)!!

        assertEquals(2L * 1024 * 1024, usage.forRange(VnStatRange.Day)!!.totalBytes)
    }
}
