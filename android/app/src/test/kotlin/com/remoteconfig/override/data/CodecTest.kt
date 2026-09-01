package com.remoteconfig.override.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class MigtCodecTest {

    private val full =
        "com.tencent.ig;0:384000 1:384000 6:1017600 7:1017600;30;90:18 60:20;2;10;1;0:0 1:0"

    @Test
    fun `full 8-segment entry round-trips`() {
        val pack = MigtCodec.parse(full)!!
        assertEquals("com.tencent.ig", pack.pkg)
        assertEquals("0:384000 1:384000 6:1017600 7:1017600", pack.migtFreq)
        assertEquals(30L, pack.migtMs)
        assertEquals("90:18 60:20", pack.fpsThresh)
        assertEquals(2L, pack.boostPolicy)
        assertEquals(10L, pack.fpsVarianceRatio)
        assertEquals(1L, pack.superTaskMaxNum)
        assertEquals("0:0 1:0", pack.migtCeilingFreq)
        assertEquals(full, MigtCodec.serialize(pack))
    }

    @Test
    fun `minimal 2-segment entry round-trips`() {
        val pack = MigtCodec.parse("com.a.b;0:384000 1:384000")!!
        assertNull(pack.migtMs)
        assertNull(pack.fpsThresh)
        assertEquals("com.a.b;0:384000 1:384000", MigtCodec.serialize(pack))
    }

    @Test
    fun `4-segment entry keeps trailing nulls`() {
        val pack = MigtCodec.parse("com.a.b;0:384000;25;90:15")!!
        assertEquals(25L, pack.migtMs)
        assertEquals("90:15", pack.fpsThresh)
        assertNull(pack.boostPolicy)
        assertEquals("com.a.b;0:384000;25;90:15", MigtCodec.serialize(pack))
    }

    @Test
    fun `malformed entries are rejected`() {
        assertNull(MigtCodec.parse("com.a.b"))                       // 只有包名
        assertNull(MigtCodec.parse("com.a b;0:384000"))              // 包名含空格
        assertNull(MigtCodec.parse("com.a.b;junk"))                  // freq 不是映射表
        assertNull(MigtCodec.parse("com.a.b;0:384000;abc"))          // migt_ms 非数字
        assertNull(MigtCodec.parse("com.a.b;0:384000;30;junk"))      // fps:thresh 不是映射表
        assertNull(MigtCodec.parse("com.a.b;" + "1;".repeat(8)))     // 超过 8 段
    }

    @Test
    fun `serialize truncates at last non-null segment`() {
        val pack = MigtCodec.Pack(
            pkg = "com.a.b",
            migtFreq = "0:384000",
            migtMs = 30,
            fpsThresh = "60:18",
            boostPolicy = null, // 之后全 null → 截断
        )
        assertEquals("com.a.b;0:384000;30;60:18", MigtCodec.serialize(pack))
    }

    @Test
    fun `5-6-7 segment entries round-trip`() {
        val s5 = "com.a.b;0:384000 1:384000;25;90:15;2;10"
        val p5 = MigtCodec.parse(s5)!!
        assertEquals(2L, p5.boostPolicy)
        assertEquals(10L, p5.fpsVarianceRatio)
        assertNull(p5.superTaskMaxNum)
        assertEquals(s5, MigtCodec.serialize(p5))

        val s6 = "$s5;1"
        val p6 = MigtCodec.parse(s6)!!
        assertEquals(1L, p6.superTaskMaxNum)
        assertNull(p6.migtCeilingFreq)
        assertEquals(s6, MigtCodec.serialize(p6))

        val s7 = "com.a.b;0:384000 1:384000;25;90:15;2;10;1"
        val p7 = MigtCodec.parse(s7)!!
        assertEquals(1L, p7.superTaskMaxNum)
        assertNull(p7.migtCeilingFreq)
        assertEquals(s7, MigtCodec.serialize(p7))
    }

    @Test
    fun `empty segments are rejected`() {
        // 与 Rust validate_entry 一致：任何空段（含尾部）整条拒绝，不静默截断
        assertNull(MigtCodec.parse("com.a.b;0:384000;;90:15"))
        assertNull(MigtCodec.parse("com.a.b;0:384000;30;;2"))
        assertNull(MigtCodec.parse("com.a.b;0:384000;30;90:15;;10"))
        assertNull(MigtCodec.parse("com.a.b;0:384000;30;90:15;;"))
    }

    @Test
    fun `mapping table tolerates extra whitespace`() {
        // 与 Rust 侧 split_whitespace 口径一致
        val pack = MigtCodec.parse("com.a.b;0:384000  1:384000 ;30")!!
        assertEquals("0:384000  1:384000", pack.migtFreq)
        assertEquals(30L, pack.migtMs)
    }
}

class CurveCodecTest {

    @Test
    fun `temp-fps curve round-trips`() {
        val segs = CurveCodec.parse("43.5:60,45:40", CurveCodec.TEMP_FPS)!!
        assertEquals(2, segs.size)
        assertEquals(CurveCodec.Segment(null, "43.5", "60"), segs[0])
        assertEquals("43.5:60,45:40", CurveCodec.format(segs, CurveCodec.TEMP_FPS))
    }

    @Test
    fun `pid string with key prefix only on first segment round-trips`() {
        val raw = "60#10:0 0 0 0 0 0,42.5:43.5 60 30 10 1 2"
        val segs = CurveCodec.parse(raw, CurveCodec.FPS_TEMP_PARAM)!!
        assertEquals("60", segs[0].key)
        assertNull(segs[1].key) // 后续段沿用首段 key，串内不重复
        assertEquals("43.5 60 30 10 1 2", segs[1].y)
        assertEquals(raw, CurveCodec.format(segs, CurveCodec.FPS_TEMP_PARAM))
    }

    @Test
    fun `battery curve round-trips`() {
        val raw = "60#1:45,20:0,100:0"
        assertEquals(raw, CurveCodec.format(CurveCodec.parse(raw, CurveCodec.FPS_SOC_FPS)!!, CurveCodec.FPS_SOC_FPS))
    }

    @Test
    fun `cpufreq curve parses temperature key`() {
        val segs = CurveCodec.parse("43.5#60:1804800,45#60:1555200", CurveCodec.TEMP_FPS_FREQ)!!
        assertEquals("43.5", segs[0].key)
        assertEquals("60", segs[0].x)
        assertEquals("1804800", segs[0].y)
    }

    @Test
    fun `space separated fps-thresh table round-trips`() {
        val raw = "90:15 60:18 40:30 30:40"
        assertEquals(raw, CurveCodec.format(CurveCodec.parse(raw, CurveCodec.FPS_THRESH)!!, CurveCodec.FPS_THRESH))
    }

    @Test
    fun `empty text parses to empty list`() {
        assertEquals(emptyList<CurveCodec.Segment>(), CurveCodec.parse("", CurveCodec.TEMP_FPS))
        assertEquals(emptyList<CurveCodec.Segment>(), CurveCodec.parse("   ", CurveCodec.TEMP_FPS))
    }

    @Test
    fun `malformed segments return null`() {
        assertNull(CurveCodec.parse("43.5:60,,45:40", CurveCodec.TEMP_FPS)) // 空段
        assertNull(CurveCodec.parse("43.5", CurveCodec.TEMP_FPS))           // 缺 :
        assertNull(CurveCodec.parse("43.5:", CurveCodec.TEMP_FPS))          // 空 y
        assertNull(CurveCodec.parse("#10:0", CurveCodec.FPS_TEMP_PARAM))    // 空 key
    }

    @Test
    fun `validate checks axis kinds per format`() {
        val bad = listOf(CurveCodec.Segment(null, "abc", "60"))
        assertNull(CurveCodec.validate(listOf(CurveCodec.Segment(null, "43.5", "60")), CurveCodec.TEMP_FPS))
        assertEquals("第 1 行温度「abc」不是有效数字", CurveCodec.validate(bad, CurveCodec.TEMP_FPS))
        // TEXT 列接受任意非空
        assertNull(
            CurveCodec.validate(
                listOf(CurveCodec.Segment(null, "42.5", "43.5 60 30")),
                CurveCodec.FPS_TEMP_PARAM,
            ),
        )
    }

    @Test
    fun `real dynamic_targetfps multi-band value round-trips`() {
        // 真实云控值：`;` 分档，每档 `targetFps#温度:档位fps,...`
        val raw = "185#10:0,44.5:120,46:90,47.5:60;165#10:0,43:120,46:90,48:60"
        val segs = CurveCodec.parse(raw, CurveCodec.FPS_TARGET_BAND)!!
        assertEquals(8, segs.size)
        assertEquals(CurveCodec.Segment("185", "10", "0"), segs[0])
        assertEquals(CurveCodec.Segment(null, "44.5", "120"), segs[1])
        assertEquals(CurveCodec.Segment(null, "47.5", "60"), segs[3])
        // 第二档开始也要解析正确（不能把 `;` 粘进上一档的值）
        assertEquals(CurveCodec.Segment("165", "10", "0"), segs[4])
        assertEquals(CurveCodec.Segment(null, "48", "60"), segs[7])
        assertEquals(raw, CurveCodec.format(segs, CurveCodec.FPS_TARGET_BAND))
    }
}

class NovatekCodecTest {

    /** songyuan 实机 sgame 条目（4 token，FI 两级 / SR 一级 / FISR 两级）。 */
    private val sgame =
        "com.tencent.tmgp.sgame_73#144#72,144,1,0x2514#45#43#43#41|60.2#120#60,120,1,0x2514#45#43#43#41" +
            "_0#0#0,0,0,0,1,0x66,1,0x222,0,0,0,0,0x62,1,0x4#45#43#43#41" +
            "_73#144#72,144,1,0x2514,1,0x66,1,0x222,0,0,0,0,0x62,1,0x4#45#43#43#41" +
            "|60.2#120#60,120,1,0x2514,1,0x66,1,0x222,0,0,0,0,0x62,1,0x4#45#43#43#41"

    @Test
    fun `real sgame entry round-trips losslessly`() {
        val e = NovatekCodec.parse(sgame)!!
        assertEquals("com.tencent.tmgp.sgame", e.pkg)
        assertEquals(2, e.fi.levels.size)
        assertEquals(listOf("72", "144", "1", "0x2514"), e.fi.levels[0].params)
        assertEquals(1, e.sr.levels.size)
        assertEquals(2, e.fisr!!.levels.size)
        assertEquals("45", e.fi.levels[0].tgTh)
        assertEquals("43", e.fi.levels[0].tgRec)
        assertEquals("43", e.fi.levels[0].mgTh)
        assertEquals("41", e.fi.levels[0].mgRec)
        assertEquals(sgame, NovatekCodec.serialize(e))
    }

    @Test
    fun `legacy 3-token entry keeps fisr null and round-trips`() {
        val raw = "com.a.b_60#90#45,90,1,0x2012#45#43#43#41_0#0#0#45#43#43#41"
        val e = NovatekCodec.parse(raw)!!
        assertNull(e.fisr)
        assertEquals("60", e.fi.levels[0].dynamicFps)
        assertEquals(raw, NovatekCodec.serialize(e))
    }

    @Test
    fun `malformed entries return null`() {
        assertNull(NovatekCodec.parse("com.a.b"))                      // 1 token
        assertNull(NovatekCodec.parse("com.a.b_x#0#0#0#0#0"))          // 等级 6 字段
        assertNull(NovatekCodec.parse("com.a.b_x#0#0#0#0#0#0#0_y#0#0#0#0#0#0_z#0#0#0#0#0#0#0_w")) // 5 token
        assertNull(NovatekCodec.parse("com#0#0_60#90#1#45#43#43#41_x#0#0#0#0#0#0"))  // 包名含 #
    }

    @Test
    fun `param names align to 17-key vocabulary`() {
        val e = NovatekCodec.parse(sgame)!!
        val names = NovatekCodec.paramNames(e.fi.levels[0].params).map { it.first }
        assertEquals(listOf("InputFPS", "TargetFPS", "MEMC", "MEMCMode"), names)
        assertEquals(NovatekCodec.PARAM_KEYS.size, 17)
    }
}
