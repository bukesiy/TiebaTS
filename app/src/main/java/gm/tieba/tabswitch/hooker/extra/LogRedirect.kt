package gm.tieba.tabswitch.hooker.extra

import de.robv.android.xposed.XposedBridge
import gm.tieba.tabswitch.XposedContext
import gm.tieba.tabswitch.hooker.IHooker

class LogRedirect : XposedContext(), IHooker {

    override fun key(): String {
        return "log_redirect"
    }

    override fun hook() {
        XposedBridge.log("TbLog redirect enabled")
        hookReplaceMethod("com.baidu.searchbox.config.AppConfig", "isDebug") { true }
        hookAfterMethod("com.baidu.tieba.log.TbLog",
            "d", String::class.java, String::class.java
        ) { param ->
            (param.args[0] as? String)?.let { tag ->
                (param.args[1] as? String)?.let { msg ->
                    XposedBridge.log("[D] [$tag] $msg")
                }
            }
        }
        hookAfterMethod("com.baidu.tieba.log.TbLog",
            "e", String::class.java, String::class.java
        ) { param ->
            (param.args[0] as? String)?.let { tag ->
                (param.args[1] as? String)?.let { msg ->
                    XposedBridge.log("[E] [$tag] $msg")
                }
            }
        }
        hookAfterMethod("com.baidu.tieba.log.TbLog",
            "i", String::class.java, String::class.java
        ) { param ->
            (param.args[0] as? String)?.let { tag ->
                (param.args[1] as? String)?.let { msg ->
                    XposedBridge.log("[I] [$tag] $msg")
                }
            }
        }
        hookAfterMethod("com.baidu.tieba.log.TbLog",
            "v", String::class.java, String::class.java
        ) { param ->
            (param.args[0] as? String)?.let { tag ->
                (param.args[1] as? String)?.let { msg ->
                    XposedBridge.log("[V] [$tag] $msg")
                }
            }
        }
        hookAfterMethod("com.baidu.tieba.log.TbLog",
            "w", String::class.java, String::class.java
        ) { param ->
            (param.args[0] as? String)?.let { tag ->
                (param.args[1] as? String)?.let { msg ->
                    XposedBridge.log("[W] [$tag] $msg")
                }
            }
        }
    }
}
