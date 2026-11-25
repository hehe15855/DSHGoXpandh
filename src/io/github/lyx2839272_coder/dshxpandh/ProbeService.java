package io.github.lyx2839272_coder.dshxpandh;

import android.app.Service;
import android.content.Intent;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;

/**
 * 注入探针的宿主进程（android:process=":probe"）。
 *
 * 为什么需要它：普通 App 读不到 /data/adb（root:root 700），问不出「LSPosed 现在还
 * 给不给本模块注入」。但只要每次新起一个独立进程，LSPosed 就必须重新做一次注入决定：
 * 被注入的话 HookEntry 会写 probe 标记，没被注入就什么都不会写。用这个差值就能问出
 * 当前真实状态，不需要 root，也不受「已加载进程不会卸载 hook」的影响。
 *
 * 实测：两次探针拿到的是不同 pid（25112 → 26049），说明每次都是全新进程。
 */
public class ProbeService extends Service {
    @Override public IBinder onBind(Intent i) { return null; }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        stopSelf(startId);
        // 给 HookEntry 一点时间把标记写完，然后把这个探针进程彻底收掉：
        // 进程留着不复用，下次启动才会重新走一遍注入流程。
        new Handler(Looper.getMainLooper()).postDelayed(new Runnable() {
            @Override public void run() { System.exit(0); }
        }, 800L);
        return START_NOT_STICKY;
    }
}
