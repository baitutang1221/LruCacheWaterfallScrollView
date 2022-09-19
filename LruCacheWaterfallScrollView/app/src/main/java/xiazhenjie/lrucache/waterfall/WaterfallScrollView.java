package xiazhenjie.lrucache.waterfall;


import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashSet;
import xiazhenjie.lrucache.waterfall.DiskLruCache;
import xiazhenjie.lrucache.waterfall.DiskLruCache.Snapshot;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.AttributeSet;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.widget.ImageView;
import android.widget.ImageView.ScaleType;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Toast;
/**
 * Demo功能:
 * 加载网络图片实现图片瀑布流效果(参见截图)
 *
 * Demo流程:
 * 1 为了加载的众多图片可以在屏幕上滑动显示,所以需要一个ScrollView控件.
 *   于是自定义ScrollView
 * 2 将自定义ScrollView作为布局xml文件的根布局.
 *   在根布局下有一个LinearLayout它就是该自定义ScrollView的第一个子孩子.
 *   即代码中waterfallScrollView.getChildAt(0)
 *   将该LinearLayout均分成三个子LinearLayout,它们三的宽度平分屏幕的宽度.
 *   这样我们就可以往这三个LinearLayout中不断添加图片,形成瀑布流
 * 3 将网络图片添加到瀑布流的过程
 *   3.1 当手指在屏幕上停止滑动时(ACTION_UP)加载图片
 *   3.2 从网络中下载图片并将其保存到本地缓存和内存缓存中
 *   3.3 找到三个LinearLayout中当前高度最小的,将图片添加进去
 *   3.4 在添加图片后对ScrollView中所有ImageView进行检查.
 *       对于不在屏幕上显示的ImageView将其所加载的网络图片替换成本地一张小图片.
 * 4 为了加载速度和内存的有效使用,示例中采用了LruCache以及DiskLruCache
 *
 *
 * 错误总结:
 * 在使用ImageView.setTag(key, tag)看到第一个参数为int,于是为其指定一个final的int
 * 运行报错:
 * java.lang.IllegalArgumentException: The key must be an application-specific resource id.
 * 原因是不可自己指定该值,而应该使用系统指定的int值.这么做大概是为了防止自己指定的值与系统某个值冲突吧.
 * 解决办法:在Strings.xml中指定值string值然后使用其在R文件中的int值即可,例如:
 * imageView.setTag(R.string.IMAGE_URL_TAG, imageUrl);其中:
 * R.string.IMAGE_URL_TAG就是字符串IMAGE_URL_TAG在R文件中的int值
 *
 * 在此可见setTag方法的用途：为某个View保存数据.
 * 该方法还是挺有用的,可以把属于该View的某些属性保存到该View里面,而不用单独找个地方来存这些数据
 *
 */
public class WaterfallScrollView extends ScrollView implements OnTouchListener {
    // 每页加载的图片数量
    public final int PAGE_SIZE = 24;
    // 当前页码
    private int currentPage;
    // 每一列的宽度
    private int everyColumnWidth;
    // 第一列的高度
    private int firstColumnHeight;
    // 第一列的布局
    private LinearLayout mFirstLinearLayout;
    // 第二列的高度
    private int secondColumnHeight;
    // 第二列的布局
    private LinearLayout mSecondLinearLayout;
    // 第三列的高度
    private int thirdColumnHeight;
    // 第三列的布局
    private LinearLayout mThirdLinearLayout;
    // 是否已经进入该界面
    private boolean isFirstEnterThisScrollView = false;
    // LruCache
    private LruCacheImageLoader mLruCacheImageLoader;
    // 记录所有正在下载或等待下载的异步任务
    private HashSet<LoadImageAsyncTask> mLoadImageAsyncTaskHashSet;
    // 记录ScrollView中的所有ImageView
    private ArrayList<ImageView> mAllImageViewArrayList;
    // 该WaterfallScrollView控件的高度
    private int waterfallScrollViewHeight;
    // ScrollView顶端已经向上滑出屏幕长度
    private int scrollY = 0;
    private int lastScrollY = -1;
    // 处理消息的Handle
    private Handler mHandler;
    // Context
    private Context mContext;
    private final int REFRESH = 9527;

    public WaterfallScrollView(Context context) {
        super(context);
        init(context);
    }

    public WaterfallScrollView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init(context);
    }

    public WaterfallScrollView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        init(context);
    }

    /**
     * 判断scrollView是否滑动到底部的三个值:
     * scrollY:ScrollView顶端已经滑出去的高度
     * waterfallScrollViewHeight:ScrollView的布局高度
     * scrollView.getChildAt(0).getMeasuredHeight():ScrollView内容的高度.
     * 常常有一部分内容要滑动后才可见,这部分的高度也包含在了这里面
     */
    private void init(Context context){
        mContext = context;
        this.setOnTouchListener(this);
        mAllImageViewArrayList = new ArrayList<ImageView>();
        mLoadImageAsyncTaskHashSet = new HashSet<LoadImageAsyncTask>();
        mLruCacheImageLoader = LruCacheImageLoader.getLruCacheImageLoaderInstance(mContext);

        mHandler = new Handler(Looper.myLooper()){
            @Override
            public void handleMessage(Message msg) {
                super.handleMessage(msg);
                if (msg.what == REFRESH) {
                    WaterfallScrollView waterfallScrollView = (WaterfallScrollView) msg.obj;
                    scrollY = waterfallScrollView.getScrollY();
                    // 如果当前的滚动位置和上次相同，表示已停止滚动
                    if (lastScrollY == scrollY) {
                        // 当滚动到最底部,并且当前没有正在下载的任务时,开始加载下一页的图片
                        int scrollViewMeasuredHeight = waterfallScrollView.getChildAt(0).getMeasuredHeight();
                        boolean isAsyncTaskHashSetEmpty = mLoadImageAsyncTaskHashSet.isEmpty();
                        if (waterfallScrollViewHeight + scrollY >= scrollViewMeasuredHeight && isAsyncTaskHashSetEmpty) {
                            waterfallScrollView.loadNextPageImages();
                        }
                        //检查所有ImageView的可见性
                        checkAllImageViewVisibility();
                    } else {
                        lastScrollY = scrollY;
                        Message message = new Message();
                        message.what = REFRESH;
                        message.obj = WaterfallScrollView.this;
                        // 5毫秒后再次对滚动位置进行判断
                        mHandler.sendMessageDelayed(message, 5);
                    }
                }
            }
        };
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        super.onLayout(changed, l, t, r, b);
        if (!isFirstEnterThisScrollView) {
            isFirstEnterThisScrollView=true;
            waterfallScrollViewHeight=getHeight();
            mFirstLinearLayout=(LinearLayout) findViewById(R.id.firstLinearLayout);
            mSecondLinearLayout=(LinearLayout) findViewById(R.id.secondLinearLayout);
            mThirdLinearLayout=(LinearLayout) findViewById(R.id.thirdLinearLayout);
            everyColumnWidth=mFirstLinearLayout.getWidth();
            loadNextPageImages();
        }
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
    }

    /**
     * 这里对于手指抬起时(ACTION_UP)时,监听ScrollView是否已经停止滚动的判断的思路不错.
     * 在ACTION_UP时直接用Handler发送一个消息在handleMessage中处理判断,如果此时还
     * 没有停止滚动,则延时一定时间再次发送消息判断滚动是否停止.
     * 这样做避免的在ACTION_UP时去加载图片而是在ScrollView停止滚动时去加载.
     */
    @Override
    public boolean onTouch(View view, MotionEvent motionEvent) {
        if (motionEvent.getAction()== MotionEvent.ACTION_UP) {
            Message message = new Message();
            message.obj = this;
            message.what = REFRESH;
            mHandler.sendMessageDelayed(message, 5);
        }
        return false;
    }

    private void loadNextPageImages(){
        if (Utils.isExistSDCard()) {
            int start = PAGE_SIZE*currentPage;
            int end = PAGE_SIZE*currentPage+PAGE_SIZE;
            LoadImageAsyncTask loadImageAsyncTask;
            if (end > ImagesUrl.urlStringArray.length) {
                end = ImagesUrl.urlStringArray.length;
            }
            if (start < ImagesUrl.urlStringArray.length) {
                Toast.makeText(mContext, "开始加载", Toast.LENGTH_SHORT).show();
                for (int i = start;i < end; i++) {
                    System.out.println("加载"+i);
                    loadImageAsyncTask = new LoadImageAsyncTask();
                    loadImageAsyncTask.execute(ImagesUrl.urlStringArray[i]);
                    mLoadImageAsyncTaskHashSet.add(loadImageAsyncTask);
                }
                currentPage++;
            }
        } else {
            Toast.makeText(mContext, "无SD卡", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * 判断ImageView是否可见
     * 如果可见:
     * 1  从LruCache取出图片显示
     * 2 若不在LruCache中,则从本地缓存中取出
     * 3 若本地缓存中也不存在那么开启异步任务下载
     * 4 下载完成后将图片保存至本地和内存缓存中
     *   第2,3,4步可以参见LoadImageAsyncTask
     * 若不可见:
     * 将ImageView显示的图片替换成本地图片
     */
    private void checkAllImageViewVisibility(){
        ImageView imageView=null;
        for(int i=0;i<mAllImageViewArrayList.size();i++){
            imageView=mAllImageViewArrayList.get(i);
            int top_border=(Integer) imageView.getTag(R.string.TOP_BORDER_TAG);
            int bottom_border=(Integer) imageView.getTag(R.string.BOTTOM_BORDER_TAG);
            if (bottom_border > getScrollY() && top_border < getScrollY() + waterfallScrollViewHeight) {
                String imageUrl=(String) imageView.getTag(R.string.IMAGE_URL_TAG);
                Bitmap bitmap=mLruCacheImageLoader.getBitmapFromLruCache(imageUrl);
                if (bitmap==null) {
                    LoadImageAsyncTask loadImageAsyncTask=new LoadImageAsyncTask(imageView);
                    loadImageAsyncTask.execute(imageUrl);
                } else {
                    System.out.println("---> 从内存缓存中取出图片");
                    imageView.setImageBitmap(bitmap);
                }

            } else {
                imageView.setImageResource(R.drawable.img_default_bg);
            }
        }
    }

    /**
     * 该LoadImageAsyncTask是获取图片的入口:
     *
     * 注意不管这个图片是在SD卡还是从网络下载,这都是获取图片的入口,这么做的好处
     * 1   统一了获取图片的入口.
     *   如果把获取图片分为图片在LruCache,图片在SD卡,图片在网络上这几种不同
     *   的情况而去分别用对应的函数获取,这样势必会导致该需求的多入口.凌乱,不好优化.
     *   而且这几种方式放到AsyncTask中都不会出错,尤其是网络请求耗时的情况下.
     * 2 不管通过哪种方式获取到了图片,我们都要对图片进行保存,或者再次修整,比如缩放.
     *   我们可以把这些操作又统一放到异步操作的onPostExecute()方法中.
     *
     * 为什么这里有需要有两个构造方法呢？因为对应两种不同的情况
     * 情况一:
     * 图片第一次显示时利用LoadImageAsyncTask()建立一个异步任务.当图片下载完成时new一个ImageView显示
     * 该图片即可.
     * 比如进入应用时开始显示图片,就是这种情况.
     *
     * 情况二:
     * 当某图片再次显示时需要一个ImageView,于是就利用LoadImageAsyncTask传入原来的ImageView;当图片的获取或者下载完成
     * 就用原来的ImageView显示即可.
     * 比如把界面拉到最下方再拉回到最上面就是这种情况.
     *
     * 这两种情况查看addImageToScrollView()方法即可明白其体现.
     *
     */
    private class LoadImageAsyncTask extends AsyncTask<String, Void, Bitmap>{
        private String imageUrl;
        private Bitmap bitmap;
        ImageView imageView=null;

        public LoadImageAsyncTask(){

        }

        public LoadImageAsyncTask(ImageView imageView){
            this.imageView=imageView;
        }

        @Override
        protected Bitmap doInBackground(String... params) {
            FileDescriptor fileDescriptor = null;
            FileInputStream fileInputStream = null;
            Snapshot snapshot = null;
            imageUrl = params[0];
            bitmap = mLruCacheImageLoader.getBitmapFromLruCache(imageUrl);
            if (bitmap == null) {
                try {
                    String key = Utils.getStringByMD5(imageUrl);
                    snapshot = mLruCacheImageLoader.getSnapShotByKey(key);
                    // 从网络下载图片且保存至本地缓存
                    if (snapshot == null) {
                        DiskLruCache.Editor editor = mLruCacheImageLoader.getEditorByKey(key);
                        if (editor != null) {
                            OutputStream outputStream = editor.newOutputStream(0);
                            if (Utils.getBitmapFromNetWorkAndSaveToDiskLruCache(imageUrl, outputStream)) {
                                System.out.println("---> 从网络下载图片且保存至本地缓存");
                                editor.commit();
                                mLruCacheImageLoader.flushDiskLruCache();
                            } else {
                                editor.abort();
                            }
                        }
                        // 缓存被写入本地缓存后再次查找key对应的缓存
                        snapshot = mLruCacheImageLoader.getSnapShotByKey(key);
                    }else{
                        System.out.println("---> 图片不在内存中但是在本地缓存中");
                    }

                    // 将图片再保存至内存缓存
                    if (snapshot != null) {
                        fileInputStream = (FileInputStream) snapshot.getInputStream(0);
                        fileDescriptor = fileInputStream.getFD();
                        if (fileDescriptor != null) {
                            bitmap = BitmapFactory.decodeFileDescriptor(fileDescriptor);
                            if (bitmap != null) {
                                System.out.println("---> 从网络下载图片且保存至本地缓存后再缓存到内存");
                                mLruCacheImageLoader.addBitmapToLruCache(imageUrl, bitmap);
                            }
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return bitmap;
        }

        /**
         * 在onPostExecute()对图片进行修整
         * 因为在doInBackground()的loadImage()方法中已经把经过scale的图片存到了SD卡和LruCache中
         * 并且在计算inSampleSize的时候是以宽width为标准的.
         * 比如inSampleSize=2,那么保存的图的宽和高都是原来的二分之一.
         * 但是请注意inSampleSize是int类型的,那么缩放出来的比例多半不是我们期望的刚好屏幕宽度的三分之一,它是有偏差的.
         * 所以在这里进行修正,尤其是对高进行修正.
         * 这样就保证了宽是一个定值(屏幕的三分之一),高也得到了调整,不至于严重失真.
         *
         */
        @Override
        protected void onPostExecute(Bitmap bitmap) {
            super.onPostExecute(bitmap);
            mLoadImageAsyncTaskHashSet.remove(this);
            if (bitmap != null) {
                double ration = bitmap.getWidth()/(everyColumnWidth*1.0);
                int imageViewHeight = (int) (bitmap.getHeight()/ration);
                int imageViewWidth = everyColumnWidth;
                addImageToScrollView(bitmap,imageView,imageViewWidth,imageViewHeight,imageUrl);
            }
        }
    }

    /**
     * 将获取到的Bitmap添加到ImageView中.
     * 这里利用View.setTag()的方式为该ImageView保存了其相关信息.
     * 比如该ImageView加载的图片的url,它的上下边在ScrollView中的位置信息等.
     */
    private void addImageToScrollView(Bitmap bitmap,ImageView imageView,int imageViewWidth,int imageViewHeight,final String imageUrl){
        if (imageView != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            ImageView newImageView = new ImageView(mContext);
            LinearLayout.LayoutParams layoutParams = new LinearLayout.LayoutParams(imageViewWidth, imageViewHeight);
            newImageView.setImageBitmap(bitmap);
            newImageView.setLayoutParams(layoutParams);
            newImageView.setScaleType(ScaleType.FIT_XY);
            newImageView.setPadding(5, 5, 5, 5);
            newImageView.setTag(R.string.IMAGE_URL_TAG, imageUrl);
            newImageView.setOnClickListener(new OnClickListener() {
                @Override
                public void onClick(View view) {
                    Intent intent = new Intent(mContext,ShowImageActivity.class);
                    intent.putExtra("imageUrl", imageUrl);
                    mContext.startActivity(intent);
                }
            });
            addImageToColumn(newImageView);
            mAllImageViewArrayList.add(newImageView);
        }
    }


    /**
     * 找到高度最小的LinearLayout并且将ImageView添加进去
     */
    private void addImageToColumn(ImageView imageView){
        int imageViewHeight=imageView.getLayoutParams().height;
        if (firstColumnHeight <= secondColumnHeight) {
            if (firstColumnHeight <= thirdColumnHeight) {
                imageView.setTag(R.string.TOP_BORDER_TAG, firstColumnHeight);
                firstColumnHeight += imageViewHeight;
                imageView.setTag(R.string.BOTTOM_BORDER_TAG, firstColumnHeight);
                mFirstLinearLayout.addView(imageView);
            }else{
                imageView.setTag(R.string.TOP_BORDER_TAG, thirdColumnHeight);
                thirdColumnHeight += imageViewHeight;
                imageView.setTag(R.string.BOTTOM_BORDER_TAG, thirdColumnHeight);
                mThirdLinearLayout.addView(imageView);
            }
        } else {
            if (secondColumnHeight <= thirdColumnHeight) {
                imageView.setTag(R.string.TOP_BORDER_TAG, secondColumnHeight);
                secondColumnHeight += imageViewHeight;
                imageView.setTag(R.string.BOTTOM_BORDER_TAG, secondColumnHeight);
                mSecondLinearLayout.addView(imageView);
            }else{
                imageView.setTag(R.string.TOP_BORDER_TAG, thirdColumnHeight);
                thirdColumnHeight += imageViewHeight;
                imageView.setTag(R.string.BOTTOM_BORDER_TAG, thirdColumnHeight);
                mThirdLinearLayout.addView(imageView);
            }
        }

    }
}