package dev.local.ridecompact;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.util.Base64;
import android.view.View;
import org.json.JSONObject;

final class SliderChallengeView extends View {
    private final Bitmap background,piece;
    private final int y;
    private final Paint paint=new Paint(Paint.FILTER_BITMAP_FLAG);
    private int offset;
    SliderChallengeView(Context context,JSONObject challenge) throws Exception {
        super(context); background=decode(challenge.getString("bigImage")); piece=decode(challenge.getString("smallImage")); y=challenge.getInt("locationY");
        if(piece.getWidth()>=background.getWidth() || piece.getHeight()>background.getHeight() || y<0 || y+piece.getHeight()>background.getHeight()) throw new IllegalArgumentException("验证码图片尺寸不匹配");
        setContentDescription("滑块验证图片");
    }
    private static Bitmap decode(String encoded) {
        if(encoded.length()>3*1024*1024) throw new IllegalArgumentException("验证码图片过大");
        String raw=encoded.startsWith("data:image/")?encoded.substring(encoded.indexOf(',')+1):encoded;
        byte[] bytes=Base64.decode(raw,Base64.DEFAULT);
        BitmapFactory.Options bounds=new BitmapFactory.Options(); bounds.inJustDecodeBounds=true; BitmapFactory.decodeByteArray(bytes,0,bytes.length,bounds);
        if(bounds.outWidth<=0 || bounds.outHeight<=0 || bounds.outWidth>4096 || bounds.outHeight>4096 || (long)bounds.outWidth*bounds.outHeight>4000000L) throw new IllegalArgumentException("验证码图片无效");
        Bitmap bitmap=BitmapFactory.decodeByteArray(bytes,0,bytes.length);
        if(bitmap==null) throw new IllegalArgumentException("验证码图片无法解码");
        return bitmap;
    }
    int maximum() { return background.getWidth()-piece.getWidth(); }
    int offset() { return offset; }
    void offset(int value) { offset=Math.max(0,Math.min(maximum(),value)); invalidate(); }
    @Override protected void onMeasure(int widthSpec,int heightSpec) {
        int width=MeasureSpec.getSize(widthSpec);
        setMeasuredDimension(width,Math.max(1,Math.round((float)width*background.getHeight()/background.getWidth())));
    }
    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas); canvas.save(); float scale=(float)getWidth()/background.getWidth(); canvas.scale(scale,scale);
        canvas.drawBitmap(background,0,0,paint); canvas.drawBitmap(piece,offset,y,paint); canvas.restore();
    }
}
