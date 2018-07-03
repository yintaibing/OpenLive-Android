package io.agora.util;

import android.content.Context;
import android.util.DisplayMetrics;

public class DensityUtils {
    public static int dp2px(Context context, float dp) {
        DisplayMetrics dm = context.getResources().getDisplayMetrics();
        return (int) (dp * dm.density + 0.5f);
    }
}
