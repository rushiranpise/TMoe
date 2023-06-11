package cc.ioctl.tmoe.hook.func

import cc.ioctl.tmoe.hook.base.CommonDynamicHook
import com.github.kyuubiran.ezxhelper.utils.*

object MaxAccounts : CommonDynamicHook() {
    override fun initOnce(): Boolean = tryOrFalse {
        findMethod(loadClass("org.telegram.messenger.UserConfig")){
            name=="hasPremiumOnAccounts"
        }.hookBefore {
            if (!isEnabled)return@hookBefore

            it.result = true
        }
        findMethod(loadClass("org.telegram.messenger.UserConfig")) {
            name == "getMaxAccountCount"
        }.hookBefore {
            if (!isEnabled) return@hookBefore
            it.result = 999
        }

        true
    }
}