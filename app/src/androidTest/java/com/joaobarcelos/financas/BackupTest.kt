package com.joaobarcelos.financas

import android.content.pm.ApplicationInfo
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BackupTest {
    @Test
    fun `RN16_backup_automatico_ligado`() {
        val flags = InstrumentationRegistry.getInstrumentation().targetContext.applicationInfo.flags
        assertTrue((flags and ApplicationInfo.FLAG_ALLOW_BACKUP) != 0)
    }
}
