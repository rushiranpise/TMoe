package cc.ioctl.tmoe.startup;

import com.github.kyuubiran.ezxhelper.init.EzXHelperInit;

import java.util.List;

import cc.ioctl.tmoe.R;
import de.robv.android.xposed.IXposedHookLoadPackage;
import de.robv.android.xposed.IXposedHookZygoteInit;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

public class HookEntry implements IXposedHookLoadPackage, IXposedHookZygoteInit {

    private static final List<String> TELEGRAM_CLIENT_PACKAGE_NAME_LIST = List.of(
            "app.nicegram",
            "com.blxueya.gugugram",
            "com.cool2645.nekolite",
            "com.evildayz.code.telegraher",
            "com.exteragram.messenger.beta",
            "com.exteragram.messenger",
            "com.iMe.android",
            "com.jasonkhew96.pigeongram",
            "ellipi.messenger",
            "ir.ilmili.telegraph",
            "it.owlgram.android",
            "me.ninjagram.messenger",
            "nekox.messenger",
            "icu.ketal.yunigram.beta",
            "icu.ketal.yunigram.lspatch.beta",
            "icu.ketal.yunigram.lspatch",
            "icu.ketal.yunigram",
            "me.luvletter.nekox",
            "nekox.messenger",
            "org.aka.messenger",
            "org.forkclient.messenger.beta",
            "org.forkclient.messenger",
            "org.forkgram.messenger",
            "org.nift4.catox",
            "org.ninjagram.messenger",
            "com.radolyn.ayugram",
            "org.telegram.BifToGram",
            "org.telegram.mdgram",
            "org.telegram.mdgramyou",
            "org.telegram.messenger.beta",
            "org.telegram.messenger.web",
            "org.telegram.messenger",
            "org.telegram.plus",
            "top.qwq2333.nullgram",
            "tw.nekomimi.nekogram",
            "ua.itaysonlab.messenger",
            "xyz.nextalone.nnngram"
    );


    private static String sModulePath = null;

    @Override
    public void handleLoadPackage(XC_LoadPackage.LoadPackageParam lpparam) throws Throwable {
        if (R.string.res_inject_success >>> 24 == 0x7f) {
            XposedBridge.log("package id must NOT be 0x7f, reject loading...");
            return;
        }
        String packageName = lpparam.packageName;
        // check LSPosed dex-obfuscation
        Class<?> kXposedBridge = XposedBridge.class;
        if (!"de.robv.android.xposed.XposedBridge".equals(kXposedBridge.getName())) {
            String className = kXposedBridge.getName();
            String pkgName = className.substring(0, className.lastIndexOf('.'));
            HybridClassLoader.setObfuscatedXposedApiPackage(pkgName);
        }
        if (TELEGRAM_CLIENT_PACKAGE_NAME_LIST.contains(packageName)) {
            StartupHook.INSTANCE.doInit(lpparam.classLoader);
            EzXHelperInit.INSTANCE.initHandleLoadPackage(lpparam);
            EzXHelperInit.INSTANCE.setLogTag("TMoe");
        }
    }

    @Override
    public void initZygote(StartupParam startupParam) {
        EzXHelperInit.INSTANCE.initZygote(startupParam);
        sModulePath = startupParam.modulePath;
    }

    public static String getModulePath() {
        String path = sModulePath;
        if (path == null) {
            throw new IllegalStateException("sModulePath is null");
        }
        return path;
    }
}
