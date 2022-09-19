package xiazhenjie.lrucache.waterfall;



import android.app.Activity;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.Window;

/**
 * @ClassName ShowImageActivity
 * @Description TODO
 * @Author xiazhenjie
 * @Date 2022/9/8 17:33
 * @Version 1.0
 */
public class ShowImageActivity extends Activity {
    private Bitmap mBitmap=null;
    private ZoomImageView mZoomImageView;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.showimage);
        init();
    }


    private void init(){
        mZoomImageView=(ZoomImageView) findViewById(R.id.zoomImageView);
        String imageUrl=getIntent().getStringExtra("imageUrl");
        mBitmap=LruCacheImageLoader.getBitmapFromDiskLruCache(imageUrl);
        if (mBitmap!=null) {
            mZoomImageView.setBitmap(mBitmap);
        }
    }


    /**
     * 回收Bitmap避免内存溢出
     */
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (mBitmap!=null) {
            mBitmap.recycle();
        }
    }

}