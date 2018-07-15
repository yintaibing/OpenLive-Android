package io.agora.util;

import android.content.Context;
import android.util.DisplayMetrics;
import android.util.Log;

public class DensityUtils {
    public static int dp2px(Context context, float dp) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return (int) (dp * dm.density + 0.5f);
    }

    public static int getStatusBarHeight(Context context) {
        int result = 0;
        int resourceId = context.getResources().getIdentifier("status_bar_height", "dimen",
                "android");
        if (resourceId > 0) {
            result = context.getResources().getDimensionPixelSize(resourceId);
        }
        return result;
    }

    /**
     * 获取导航栏高度
     * @param context
     * @return
     */
    public static int getDaoHangHeight(Context context) {
        int result = 0;
        int resourceId=0;
        int rid = context.getResources().getIdentifier("config_showNavigationBar", "bool", "android");
        if (rid!=0){
            resourceId = context.getResources().getIdentifier("navigation_bar_height", "dimen", "android");
            result = context.getResources().getDimensionPixelSize(resourceId);
            Log.e("density", "高度："+result);
            return result;
        }else
            return 0;
    }
}
