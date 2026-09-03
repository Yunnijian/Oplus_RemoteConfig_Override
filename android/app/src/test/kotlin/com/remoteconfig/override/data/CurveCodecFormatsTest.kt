package com.remoteconfig.override.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * 十种 [CurveCodec.Format] 的 round-trip + NaN/Infinity 拒绝。
 *
 * 名称带 Formats：`CodecTest.kt` 里已有同包的 `CurveCodecTest`（真实云控值/分组解析），
 * 两者互补，重名会撞出 duplicate JVM class 使整个测试源集编译失败。
 */
class CurveCodecFormatsTest {
    private fun roundTrip(sample: String, format: CurveCodec.Format) {
        val parsed = CurveCodec.parse(sample, format)
        assertNotNull(sample, parsed)
        assertEquals(sample, CurveCodec.format(parsed!!, format))
        assertNull(CurveCodec.validate(parsed, format))
    }

    @Test fun tempFps() = roundTrip("43.5:60,45:40", CurveCodec.TEMP_FPS)
    @Test fun tempPower() = roundTrip("42:4.5,45:5", CurveCodec.TEMP_POWER)
    @Test fun fpsThresh() = roundTrip("90:15 60:18 40:30", CurveCodec.FPS_THRESH)
    @Test fun cpuFreq() = roundTrip("0:384000 6:1017600", CurveCodec.CPU_FREQ)
    @Test fun sceneScale() = roundTrip("1:0.8,2:1.0", CurveCodec.SCENE_SCALE)
    @Test fun rrFpsScale() = roundTrip("120#60:0.8", CurveCodec.RR_FPS_SCALE)
    @Test fun fpsTargetBand() = roundTrip("165#10:0,45:120;120#10:0,45:90", CurveCodec.FPS_TARGET_BAND)
    @Test fun fpsSocFps() = roundTrip("165#1:60,4:0;90#1:60,4:0", CurveCodec.FPS_SOC_FPS)

    @Test
    fun rejectsNanAndInfinity() {
        val parsed = CurveCodec.parse("NaN:60", CurveCodec.TEMP_FPS)
        assertNotNull(parsed)
        assertNotNull(CurveCodec.validate(parsed!!, CurveCodec.TEMP_FPS))
        val inf = CurveCodec.parse("Infinity:60", CurveCodec.TEMP_FPS)
        assertNotNull(inf)
        assertNotNull(CurveCodec.validate(inf!!, CurveCodec.TEMP_FPS))
    }
}
