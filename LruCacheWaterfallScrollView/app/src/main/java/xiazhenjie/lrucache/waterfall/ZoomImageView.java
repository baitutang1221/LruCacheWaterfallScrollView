package xiazhenjie.lrucache.waterfall;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.util.AttributeSet;
import android.util.FloatMath;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
/**
 * 流程说明:
 * 在该自定义View中主要是运用到了Matrix对图片进行缩放和位移
 * 1 初始时,将图片绘制到该控件上
 * 2 每当对图片拖动或者缩放时重新绘制图片
 *
 * 核心方法:
 * canvas.drawBitmap(bitmap, matrix, paint)
 * 重点在于去操作一个Matrix.
 * 该处主要用到的是利用Matrix实现缩放(Scale)和位移(Translate)
 *
 *
 * mMatrix和mCurrentMatrix的说明
 * 这是以前写的Demo了,今天在重新整理看到这两个Matrix居然一下子
 * 没有反应过来.所以在此写下笔记,记录下来.
 * 在这个示例中一共涉及到两个Matrix,它们分别有什么用呢?
 * mMatrix.postScale()和mMatrix.postTranslate()起到实际作用的
 * 是mMatrix.但是请注意,这些postScale和postTranslate是基于以往
 * 的matrix的,就是说现在这个mMatrix执行的操作是在原来的矩阵matrix
 * 的基础上进行的.
 * 比如第一次是scale缩放操作,得到的矩阵是matrix1,这个时候停止操作
 * 图片已经改变了原来的样子
 * 然后接着进行第二次的操作,再进行translate位移操作,它就是在第一次
 * 的结果上继续上操作的;从代码上来看,现在的matrix要在上一次的matrix
 * 进行操作.
 * 所以我们需要一个变量来记录上次操作后的矩阵,即此处的mCurrentMatrix
 *
 *
 *
 * 关于CURRENT_MODE == ZOOM_MODE时的说明:
 * 每次的缩放scale都是相对于两指头放在屏幕上的最初状态而言的.
 * 什么意思呢?解释如下:
 * if (CURRENT_MODE == ZOOM_MODE) {
 *    在这段代码中twoFingers_distance_before_move是不变的.
 *    但是twoFingers_distance_after_move在两指操作缩放的过程
 *    中是持续变大或者变小的.
 *    这样导致了计算出来的scale是持续变大或者边小的.
 *    比如在两指慢慢放大的过程中,从输出的Log可以发现这个scale在
 *    一直变大,哪怕是放大的动作很小此时的scale也是1.X,但是图片也只
 *    变大了一点点没有突然变很大.因为每次的缩放都是针对缩放前的状态
 *    而言的,而不是针对上一次缩放而言.举例吧:
 *    status1:两指放在屏幕上的状态
 *    然后两指持续在屏幕上慢慢的MOVE实现放大,每一次微小的放大都构成
 *    了一次新的状态
 *    status2:放大了一点
 *    status3:持续放大了一点
 *    status4:又持续放大了一点
 *    status5:再一次持续放大了一点
 *    .........................
 *    status5,status4的放大都是针对status1而言的,而不是针对它们的上一次
 *    status4或者status3而言.
 *    所以每次都要先复制原来的matrix再进行缩放,代码如下:
 *    * mMatrix.set(mCurrentMatrix);
 *    //依据缩放比例和中心点进行缩放
 *    mMatrix.postScale(scale, scale, mMiddlePointF.x,mMiddlePointF.y);
 *  }
 *
 *
 *
 * 注意事项:
 * 在该Demo中对于ImageView的设置
 * android:layout_width="match_parent"
 * android:layout_height="match_parent"
 * 是不太合理的,在具体项目中应调整
 *
 */
public class ZoomImageView extends View {
    //从SD卡获取的图片
    private Bitmap mRawBitmap;
    //该缩放控件自身的宽
    private int zoomImageViewWidth;
    //该缩放控件自身的高
    private int zoomImageViewHeight;
    //TAG
    private final String TAG="ZoomImageView";
    // 开始点
    private PointF mStartPoinF;
    // 图片位置的变换矩阵
    private Matrix mMatrix;
    // 图片当前矩阵
    private Matrix mCurrentMatrix;
    // 模式参数
    private int CURRENT_MODE = 0;
    // 初始模式
    private static final int INIT_MODE = 1;
    // 拖拉模式
    private static final int DRAG_MODE = 2;
    // 缩放模式
    private static final int ZOOM_MODE = 3;
    // 开启缩放的阈值
    private static final float ZOOM_THRESHOLD = 10.0f;
    // 缩放前两指间的距离
    private float twoFingers_distance_before_move;
    // 缩放后两指间的距离
    private float twoFingers_distance_after_move;
    // 两指间中心点
    private PointF mMiddlePointF;

    public ZoomImageView(Context context) {
        super(context);
    }

    public ZoomImageView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public ZoomImageView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setBitmap(Bitmap bitmap){
        CURRENT_MODE=INIT_MODE;
        mRawBitmap=bitmap;
        mStartPoinF = new PointF();
        mMatrix = new Matrix();
        mCurrentMatrix = new Matrix();
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        super.onLayout(changed, left, top, right, bottom);
        if (changed) {
            zoomImageViewWidth=getWidth();
            zoomImageViewHeight=getHeight();
        }
    }


    @Override
    public boolean onTouchEvent(MotionEvent event) {
        switch (event.getAction() & MotionEvent.ACTION_MASK) {
            case MotionEvent.ACTION_DOWN:
                CURRENT_MODE = DRAG_MODE;
                // 记录图片当前matrix
                mCurrentMatrix.set(mMatrix);
                // 记录开始坐标point
                mStartPoinF.set(event.getX(), event.getY());
                break;

            // 当屏幕上已经有触点(手指),再有手指按下时触发该事件
            case MotionEvent.ACTION_POINTER_DOWN:
                CURRENT_MODE = ZOOM_MODE;
                twoFingers_distance_before_move = getTwoPointsDistance(event);
                if (twoFingers_distance_before_move > ZOOM_THRESHOLD) {
                    // 计算两触点的中心点
                    mMiddlePointF = getMiddlePoint(event);
                }
                break;

            case MotionEvent.ACTION_MOVE:
                //拖动模式下--->处理图片的拖动
                if (CURRENT_MODE == DRAG_MODE) {
                    // 获取X轴移动距离
                    float distanceX = event.getX() - mStartPoinF.x;
                    // 获取Y轴移动距离
                    float distanceY = event.getY() - mStartPoinF.y;
                    // 在mCurrentMatrix的基础上平移图片,所以将mCurrentMatrix复制到mMatrix
                    mMatrix.set(mCurrentMatrix);
                    mMatrix.postTranslate(distanceX, distanceY);

                }
                //缩放模式下--->处理图片的缩放
                if (CURRENT_MODE == ZOOM_MODE) {
                    twoFingers_distance_after_move = getTwoPointsDistance(event);
                    if (twoFingers_distance_after_move > ZOOM_THRESHOLD) {
                        // 计算缩放比例
                        float scale = twoFingers_distance_after_move / twoFingers_distance_before_move;
                        // 在mCurrentMatrix的基础上缩放图片,所以将mCurrentMatrix复制到mMatrix
                        mMatrix.set(mCurrentMatrix);
                        // 依据缩放比例和中心点进行缩放
                        mMatrix.postScale(scale, scale, mMiddlePointF.x,mMiddlePointF.y);
                    }
                }
                break;

            case MotionEvent.ACTION_UP:
                // 当手指离开屏幕,但屏幕上仍有其他触点(手指)时触发该事件
            case MotionEvent.ACTION_POINTER_UP:
                CURRENT_MODE = 0;
                break;
        }
        invalidate();
        return true;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        switch (CURRENT_MODE) {
            case INIT_MODE:
                initZoomImageView(canvas);
                break;
            default:
                canvas.drawBitmap(mRawBitmap, mMatrix, null);
                break;
        }
    }


    /**
     * 将从SD卡获取的图片显示到该ZoomImageView控件
     * 1  图片的宽或高大于ZoomImageView控件本身的宽或者高则缩放.
     *   1.1 判断以宽为标准或者以高为标准进行压缩,如果:
     *      rawBitmapWidth-zoomImageViewWidth>rawBitmapHeight-zoomImageViewHeight
     *      则说明图片的宽超出控件的宽的程度要大于图片的高超出控件的高的程度.所以必须要满足对于宽的压缩,即以宽为
     *      压缩基准.反之同理,不再赘述.
     *   1.2 在以宽为基准压缩图片后,图片的宽即为ZoomImageView控件的宽,但是图片的高必然小于
     *      ZoomImageView控件的高.所以在Y方向位移,使得图片在控件中心位置绘制.
     *      反之同理,不再赘述
     * 2  图片的宽或高均不大于ZoomImageView控件本身的宽或者高.
     *   则在ZoomImageView控件中心位置绘制图片
     */
    private void initZoomImageView(Canvas canvas){
        if (mRawBitmap!=null) {
            Matrix matrix=new Matrix();
            int rawBitmapWidth=mRawBitmap.getWidth();
            int rawBitmapHeight=mRawBitmap.getHeight();
            Log.i(TAG, "控件本身宽="+zoomImageViewWidth+",控件本身高="+zoomImageViewHeight);
            Log.i(TAG, "图片宽="+rawBitmapWidth+",图片高="+rawBitmapHeight);
            if (rawBitmapWidth>zoomImageViewWidth||rawBitmapHeight>zoomImageViewHeight) {
                Log.i(TAG, "rawBitmapWidth-zoomImageViewWidth="+(rawBitmapWidth-zoomImageViewWidth));
                Log.i(TAG, "rawBitmapHeight-zoomImageViewHeight="+(rawBitmapHeight-zoomImageViewHeight));
                //以宽为基准压缩
                if (rawBitmapWidth-zoomImageViewWidth>rawBitmapHeight-zoomImageViewHeight) {
                    //1 压缩
                    float scaleXY=zoomImageViewWidth/(rawBitmapWidth*1.0f);
                    matrix.postScale(scaleXY, scaleXY);
                    //2在Y方向上平移,使图片居中
                    float translateY=(zoomImageViewHeight-rawBitmapHeight*scaleXY)/2.0f;
                    matrix.postTranslate(0, translateY);
                    Log.i(TAG, "以宽为基准压缩 scaleXY="+scaleXY+",translateY="+translateY);
                    //以高为基准压缩
                } else {
                    //1 压缩
                    float scaleXY=zoomImageViewHeight/(rawBitmapHeight*1.0f);
                    matrix.postScale(scaleXY, scaleXY);
                    //2在X方向上平移,使图片居中
                    float translateX=(zoomImageViewWidth-rawBitmapWidth*scaleXY)/2.0f;
                    matrix.postTranslate(translateX, 0);
                    Log.i(TAG, "以高为基准压缩 scaleXY="+scaleXY+",translateX="+translateX);
                }
            } else {
                float translateX=(zoomImageViewWidth-rawBitmapWidth)/2.0f;
                float translateY=(zoomImageViewHeight-rawBitmapHeight)/2.0f;
                matrix.postTranslate(translateX, translateY);
                Log.i(TAG, "不压缩,图片居中显示 translateX="+translateX+",translateY="+translateY);
            }
            canvas.drawBitmap(mRawBitmap, matrix, null);
            //将图片初始化完成后的matrix保存到mMatrix.
            //后续进行的操作都是在mMatrix上进行的
            mMatrix.set(matrix);
        }
    }

    // 计算两点之间的距离
    public static float getTwoPointsDistance(MotionEvent event) {
//        float disX = event.getX(1) - event.getX(0);
//        float disY = event.getY(1) - event.getY(0);
//        return FloatMath.sqrt(disX * disX + disY * disY);

        return 0;
    }

    // 计算两点之间的中间点
    public static PointF getMiddlePoint(MotionEvent event) {
        float midX = (event.getX(0) + event.getX(1)) / 2;
        float midY = (event.getY(0) + event.getY(1)) / 2;
        return new PointF(midX, midY);
    }

}