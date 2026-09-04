package com.remoteconfig.override.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * P2-26：libsu 没有每命令超时，`execRoot` 的看门狗是唯一的兜底。
 * 这里锁住它的两个不变量 —— 超时绝不能被当成成功，且必须带可展示的文案。
 */
class RootResultTest {

    @Test
    fun `only exit code zero counts as success`() {
        assertTrue(RootResult(0, listOf("{}"), emptyList()).isSuccess)
        assertFalse(RootResult(1, emptyList(), listOf("写入失败")).isSuccess)
        assertFalse(RootResult(-1, emptyList(), emptyList()).isSuccess)
    }

    @Test
    fun `watchdog result is a failure carrying a user facing message`() {
        val timeout = RootResult.TIMEOUT
        assertFalse("超时若被当成成功，写入按钮会亮绿但库里没改", timeout.isSuccess)
        // DatabaseManager / JoyoseManager 把 stdout+stderr 合并成展示文案，
        // 超时时 out 为空，文案必须从 err 里出来，否则用户只会看到兜底的"写入失败"。
        val shown = (timeout.out + timeout.err).map(String::trim).filter(String::isNotEmpty)
            .joinToString("\n")
        assertEquals(RootResult.TIMEOUT_MESSAGE, shown)
    }
}
