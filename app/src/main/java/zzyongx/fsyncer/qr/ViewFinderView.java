package zzyongx.fsyncer.qr;

import android.content.Context;
import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Rect;
import android.util.AttributeSet;
import android.view.Display;
import android.view.View;
import android.view.WindowManager;

import zzyongx.fsyncer.R;

public final class ViewFinderView extends View {
  Paint paint;
  Rect rect;

  public ViewFinderView(Context context, AttributeSet attrs) {
    super(context, attrs);

    Resources resources = getResources();

    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(resources.getColor(R.color.colorViewfinderMask));
  }

  public void setFinderRect(Rect rect) {
    this.rect = rect;
  }

  @Override
  protected void onDraw(Canvas canvas) {
    WindowManager wm = (WindowManager) getContext().getSystemService(Context.WINDOW_SERVICE);
    Display display = wm.getDefaultDisplay();

    int w = display.getWidth();
    int h = display.getHeight();

    canvas.drawRect(0, 0, w, rect.top, paint);
    canvas.drawRect(0, rect.top, rect.left, rect.bottom, paint);
    canvas.drawRect(rect.right, rect.top, w, rect.bottom, paint);
    canvas.drawRect(0, rect.bottom, w, h, paint);
  }
}
