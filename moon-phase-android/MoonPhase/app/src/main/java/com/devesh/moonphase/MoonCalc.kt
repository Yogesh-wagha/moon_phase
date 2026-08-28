package com.devesh.moonphase

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.roundToLong
import kotlin.math.sin

/**
 * Lunar phase / illumination from Meeus, *Astronomical Algorithms* (2nd ed.).
 *
 *  - Moon position: truncated ELP-2000/82 series (ch. 47). ~0.02 deg in longitude,
 *    which is far below what matters for an illuminated fraction.
 *  - Sun position: low-accuracy series (ch. 25).
 *  - Illuminated fraction: ch. 48, k = (1 + cos i) / 2.
 *  - Phase instants (new / quarters / full): ch. 49, main periodic terms only
 *    (the omitted A1..A14 planetary terms are worth < 0.001 d).
 *
 * Accuracy of the illuminated fraction is a few parts in 10^4 -- better than the
 * eye can judge on a rendered disc.
 */
object MoonCalc {

    private const val D2R = PI / 180.0
    private const val R2D = 180.0 / PI
    private const val AU_KM = 149_597_870.7
    private const val MOON_RADIUS_KM = 1737.4
    const val SYNODIC_MONTH = 29.530588861

    data class MoonInfo(
        /** Julian Day (UT) of the instant this was evaluated for. */
        val jd: Double,
        /** Illuminated fraction of the disc, 0..1. */
        val illumination: Double,
        /** True while the Moon is filling (elongation < 180 deg). */
        val waxing: Boolean,
        val phaseName: String,
        /** Geocentric elongation Moon - Sun, 0..360 deg. 0 = new, 180 = full. */
        val elongationDeg: Double,
        /** Sun-Moon-Earth phase angle, 0 deg at full, 180 deg at new. */
        val phaseAngleDeg: Double,
        /** Position in the cycle, 0..1. 0 = new, 0.25 = first quarter, 0.5 = full. */
        val cyclePosition: Double,
        /** Geocentric distance in km. */
        val distanceKm: Double,
        /** Apparent angular diameter in degrees. */
        val angularDiameterDeg: Double
    ) {
        val illuminationPercent: Double get() = illumination * 100.0
    }

    data class MoonEvents(
        /** Days elapsed since the last new moon. */
        val ageDays: Double,
        val nextNewMoon: Instant,
        val nextFirstQuarter: Instant,
        val nextFullMoon: Instant,
        val nextLastQuarter: Instant
    )

    // ---------------------------------------------------------------- time

    fun julianDay(instant: Instant): Double =
        instant.toEpochMilli() / 86_400_000.0 + 2_440_587.5

    fun jdToInstant(jd: Double): Instant =
        Instant.ofEpochMilli(((jd - 2_440_587.5) * 86_400_000.0).roundToLong())

    /** Local noon is the fair representative instant for a whole calendar day. */
    fun noonOf(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): Instant =
        date.atTime(12, 0).atZone(zone).toInstant()

    /** Delta T in days (Espenak & Meeus polynomial, valid ~2005-2050). */
    private fun deltaTDays(jd: Double): Double {
        val year = 2000.0 + (jd - 2451545.0) / 365.25
        val t = year - 2000.0
        return (62.92 + 0.32217 * t + 0.005589 * t * t) / 86400.0
    }

    private fun norm360(x: Double): Double {
        val r = x % 360.0
        return if (r < 0) r + 360.0 else r
    }

    // ------------------------------------------------------- phase / illum

    fun info(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): MoonInfo =
        info(noonOf(date, zone))

    fun info(instant: Instant): MoonInfo {
        val jd = julianDay(instant)
        val t = (jd - 2451545.0) / 36525.0
        val t2 = t * t
        val t3 = t2 * t
        val t4 = t3 * t

        // Fundamental arguments (Meeus 47.1 - 47.5)
        val lp = 218.3164477 + 481267.88123421 * t - 0.0015786 * t2 + t3 / 538841.0 - t4 / 65194000.0
        val d = (297.8501921 + 445267.1114034 * t - 0.0018819 * t2 + t3 / 545868.0 - t4 / 113065000.0) * D2R
        val m = (357.5291092 + 35999.0502909 * t - 0.0001536 * t2 + t3 / 24490000.0) * D2R
        val mp = (134.9633964 + 477198.8675055 * t + 0.0087414 * t2 + t3 / 69699.0 - t4 / 14712000.0) * D2R
        val f = (93.2720950 + 483202.0175233 * t - 0.0036539 * t2 - t3 / 3526000.0 + t4 / 863310000.0) * D2R

        // Moon: ecliptic longitude correction (deg), latitude (deg), distance (km)
        val dLon = 6.288774 * sin(mp) +
            1.274027 * sin(2 * d - mp) +
            0.658314 * sin(2 * d) +
            0.213618 * sin(2 * mp) -
            0.185116 * sin(m) -
            0.114332 * sin(2 * f) +
            0.058793 * sin(2 * d - 2 * mp) +
            0.057066 * sin(2 * d - m - mp) +
            0.053322 * sin(2 * d + mp) +
            0.045758 * sin(2 * d - m) -
            0.040923 * sin(m - mp) -
            0.034720 * sin(d) -
            0.030383 * sin(m + mp) +
            0.015327 * sin(2 * d - 2 * f) -
            0.012528 * sin(mp + 2 * f) +
            0.010980 * sin(mp - 2 * f) +
            0.010675 * sin(4 * d - mp) +
            0.010034 * sin(3 * mp)

        val beta = 5.128122 * sin(f) +
            0.280602 * sin(mp + f) +
            0.277693 * sin(mp - f) +
            0.173237 * sin(2 * d - f) +
            0.055413 * sin(2 * d - mp + f) +
            0.046271 * sin(2 * d - mp - f) +
            0.032573 * sin(2 * d + f) +
            0.017198 * sin(2 * mp + f) +
            0.009266 * sin(2 * d + mp - f) +
            0.008822 * sin(2 * mp - f) +
            0.008216 * sin(2 * d - m - f) +
            0.004324 * sin(2 * d - 2 * mp - f) +
            0.004200 * sin(2 * d + mp + f)

        val moonDistKm = 385000.56 +
            (-20905.355 * cos(mp)) +
            (-3699.111 * cos(2 * d - mp)) +
            (-2955.968 * cos(2 * d)) +
            (-569.925 * cos(2 * mp)) +
            48.888 * cos(m) +
            (-3.149 * cos(2 * f)) +
            246.158 * cos(2 * d - 2 * mp) +
            (-152.138 * cos(2 * d - m - mp)) +
            (-170.733 * cos(2 * d + mp)) +
            (-204.586 * cos(2 * d - m)) +
            (-129.620 * cos(m - mp)) +
            108.743 * cos(d) +
            104.755 * cos(m + mp) +
            10.321 * cos(2 * d - 2 * f) +
            79.661 * cos(mp - 2 * f)

        val moonLon = norm360(lp + dLon)

        // Sun (Meeus ch. 25, low accuracy)
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t2
        val ms = norm360(357.52911 + 35999.05029 * t - 0.0001537 * t2)
        val msr = ms * D2R
        val cEq = (1.914602 - 0.004817 * t - 0.000014 * t2) * sin(msr) +
            (0.019993 - 0.000101 * t) * sin(2 * msr) +
            0.000289 * sin(3 * msr)
        val sunLon = norm360(l0 + cEq)
        val nu = (ms + cEq) * D2R
        val ecc = 0.016708634 - 0.000042037 * t - 0.0000001267 * t2
        val sunDistKm = (1.000001018 * (1 - ecc * ecc) / (1 + ecc * cos(nu))) * AU_KM

        // Elongation and phase angle (Meeus 48.2 / 48.3)
        val cosPsi = (cos(beta * D2R) * cos((moonLon - sunLon) * D2R)).coerceIn(-1.0, 1.0)
        val psi = acos(cosPsi)
        val phaseAngle = atan2(sunDistKm * sin(psi), moonDistKm - sunDistKm * cos(psi))
        val k = (1.0 + cos(phaseAngle)) / 2.0

        val elong = norm360(moonLon - sunLon)
        val cyclePos = elong / 360.0

        return MoonInfo(
            jd = jd,
            illumination = k.coerceIn(0.0, 1.0),
            waxing = elong < 180.0,
            phaseName = nameFor(elong),
            elongationDeg = elong,
            phaseAngleDeg = phaseAngle * R2D,
            cyclePosition = cyclePos,
            distanceKm = moonDistKm,
            angularDiameterDeg = 2.0 * atan(MOON_RADIUS_KM / moonDistKm) * R2D
        )
    }

    /** Classic eight-fold naming: primary phases own a +/- 11.25 deg window (~0.9 d). */
    private fun nameFor(elongDeg: Double): String = when {
        elongDeg < 11.25 || elongDeg >= 348.75 -> "New Moon"
        elongDeg < 78.75 -> "Waxing Crescent"
        elongDeg < 101.25 -> "First Quarter"
        elongDeg < 168.75 -> "Waxing Gibbous"
        elongDeg < 191.25 -> "Full Moon"
        elongDeg < 258.75 -> "Waning Gibbous"
        elongDeg < 281.25 -> "Last Quarter"
        else -> "Waning Crescent"
    }

    // ---------------------------------------------------- phase instants

    fun events(date: LocalDate, zone: ZoneId = ZoneId.systemDefault()): MoonEvents =
        events(noonOf(date, zone))

    fun events(instant: Instant): MoonEvents {
        val jd = julianDay(instant)
        return MoonEvents(
            ageDays = jd - searchPhase(jd, 0.00, forward = false),
            nextNewMoon = jdToInstant(searchPhase(jd, 0.00, forward = true)),
            nextFirstQuarter = jdToInstant(searchPhase(jd, 0.25, forward = true)),
            nextFullMoon = jdToInstant(searchPhase(jd, 0.50, forward = true)),
            nextLastQuarter = jdToInstant(searchPhase(jd, 0.75, forward = true))
        )
    }

    private fun searchPhase(fromJd: Double, phaseFrac: Double, forward: Boolean): Double {
        val approx = (fromJd - 2451550.09766) / SYNODIC_MONTH
        var k = floor(approx) + if (forward) -2.0 else 2.0
        val step = if (forward) 1.0 else -1.0
        repeat(8) {
            val jd = phaseJd(k + phaseFrac)
            if (forward && jd > fromJd) return jd
            if (!forward && jd <= fromJd) return jd
            k += step
        }
        return phaseJd(k + phaseFrac)
    }

    /**
     * Julian Day (UT) of the phase with lunation index [k]; the fractional part of [k]
     * selects the phase: .00 new, .25 first quarter, .50 full, .75 last quarter.
     */
    fun phaseJd(k: Double): Double {
        val t = k / 1236.85
        val t2 = t * t
        val t3 = t2 * t
        val t4 = t3 * t

        val mean = 2451550.09766 + 29.530588861 * k +
            0.00015437 * t2 - 0.000000150 * t3 + 0.00000000073 * t4

        val e = 1 - 0.002516 * t - 0.0000074 * t2
        val m = norm360(2.5534 + 29.10535670 * k - 0.0000014 * t2 - 0.00000011 * t3) * D2R
        val mp = norm360(201.5643 + 385.81693528 * k + 0.0107582 * t2 + 0.00001238 * t3 - 0.000000058 * t4) * D2R
        val f = norm360(160.7108 + 390.67050284 * k - 0.0016118 * t2 - 0.00000227 * t3 + 0.000000011 * t4) * D2R
        val om = norm360(124.7746 - 1.56375588 * k + 0.0020672 * t2 + 0.00000215 * t3) * D2R

        val frac = k - floor(k)
        val isNew = frac < 0.125 || frac >= 0.875
        val isFull = frac >= 0.375 && frac < 0.625

        var corr: Double = if (isNew || isFull) {
            val a = if (isNew) -0.40720 else -0.40614
            val b = if (isNew) 0.17241 else 0.17302
            val c = if (isNew) 0.01608 else 0.01614
            val dd = if (isNew) 0.01039 else 0.01043
            val ee = if (isNew) 0.00739 else 0.00734
            val ff = if (isNew) -0.00514 else -0.00515
            val gg = if (isNew) 0.00208 else 0.00209
            a * sin(mp) + b * e * sin(m) + c * sin(2 * mp) + dd * sin(2 * f) +
                ee * e * sin(mp - m) + ff * e * sin(mp + m) + gg * e * e * sin(2 * m) -
                0.00111 * sin(mp - 2 * f) - 0.00057 * sin(mp + 2 * f) +
                0.00056 * e * sin(2 * mp + m) - 0.00042 * sin(3 * mp) +
                0.00042 * e * sin(m + 2 * f) + 0.00038 * e * sin(m - 2 * f) -
                0.00024 * e * sin(2 * mp - m) - 0.00017 * sin(om) -
                0.00007 * sin(mp + 2 * m)
        } else {
            val base = -0.62801 * sin(mp) + 0.17172 * e * sin(m) -
                0.01183 * e * sin(mp + m) + 0.00862 * sin(2 * mp) +
                0.00804 * sin(2 * f) + 0.00454 * e * sin(mp - m) +
                0.00204 * e * e * sin(2 * m) - 0.00180 * sin(mp - 2 * f) -
                0.00070 * sin(mp + 2 * f) - 0.00040 * sin(3 * mp) -
                0.00034 * e * sin(2 * mp - m) + 0.00032 * e * sin(m + 2 * f) +
                0.00032 * e * sin(m - 2 * f) - 0.00028 * e * e * sin(mp + 2 * m) +
                0.00027 * e * sin(2 * mp + m) - 0.00017 * sin(om)
            val w = 0.00306 - 0.00038 * e * cos(m) + 0.00026 * cos(mp) -
                0.00002 * cos(mp - m) + 0.00002 * cos(mp + m) + 0.00002 * cos(2 * f)
            base + if (abs(frac - 0.25) < 0.01) w else -w
        }

        // Meeus table 49.A: planetary arguments, worth up to ~100 s in total.
        for (a in ADDITIONAL) {
            corr += a[3] * sin(norm360(a[0] + a[1] * k + a[2] * t2) * D2R)
        }

        val jde = mean + corr
        return jde - deltaTDays(jde) // TD -> UT
    }

    /** A1..A14: constant term, k coefficient, T^2 coefficient, amplitude in days. */
    private val ADDITIONAL = arrayOf(
        doubleArrayOf(299.77, 0.107408, -0.009173, 0.000325),
        doubleArrayOf(251.88, 0.016321, 0.0, 0.000165),
        doubleArrayOf(251.83, 26.651886, 0.0, 0.000164),
        doubleArrayOf(349.42, 36.412478, 0.0, 0.000126),
        doubleArrayOf(84.66, 18.206239, 0.0, 0.000110),
        doubleArrayOf(141.74, 53.303771, 0.0, 0.000062),
        doubleArrayOf(207.14, 2.453732, 0.0, 0.000060),
        doubleArrayOf(154.84, 7.306860, 0.0, 0.000056),
        doubleArrayOf(34.52, 27.261239, 0.0, 0.000047),
        doubleArrayOf(207.19, 0.121824, 0.0, 0.000042),
        doubleArrayOf(291.34, 1.844379, 0.0, 0.000040),
        doubleArrayOf(161.72, 24.198154, 0.0, 0.000037),
        doubleArrayOf(239.56, 25.513099, 0.0, 0.000035),
        doubleArrayOf(331.55, 3.592518, 0.0, 0.000023)
    )
}
