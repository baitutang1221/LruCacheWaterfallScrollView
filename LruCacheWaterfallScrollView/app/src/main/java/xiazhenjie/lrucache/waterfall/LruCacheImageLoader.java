package xiazhenjie.lrucache.waterfall;



import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.LruCache;

/**
 * @ClassName LruCacheImageLoader
 * @Description TODO
 * @Author xiazhenjie
 * @Date 2022/9/8 17:31
 * @Version 1.0
 */
public class LruCacheImageLoader {

    private static LruCacheImageLoader mLruCacheImageLoader;

    private static LruCache<String, Bitmap> mLruCache;

    private static DiskLruCache mDiskLruCache;

    private Context mContext;

    //DiskLruCache中对于图片的最大缓存值.
    private int maxSize = 20 * 1024 * 1024;

    private LruCacheImageLoader(Context context){
        mContext=context;

        //初始化LruCache.
        //设定LruCache的缓存为可用内存的六分之一
        int maxMemory = (int) Runtime.getRuntime().maxMemory();
        int size = maxMemory / 6;
        mLruCache = new LruCache<String, Bitmap>(size){
            @Override
            protected int sizeOf(String key, Bitmap bitmap) {
                return bitmap.getByteCount();
            }
        };

        // 初始化DiskLruCache
        try {
            File dirFile = Utils.getDiskLruCacheDir(context, "image");
            if (!dirFile.exists()) {
                dirFile.mkdirs();
            }
            mDiskLruCache = DiskLruCache.open(dirFile,Utils.getAppVersionCode(mContext), 1, maxSize);
        } catch (Exception e) {
        }

    }

    public static LruCacheImageLoader getLruCacheImageLoaderInstance(Context context){
        if (mLruCacheImageLoader==null) {
            mLruCacheImageLoader=new LruCacheImageLoader(context);
        }
        return mLruCacheImageLoader;
    }

    /**
     * 从LruCache中获取图片,若不存在返回null
     */
    public Bitmap getBitmapFromLruCache(String key){
        return mLruCache.get(key);
    }

    /**
     * 往LruCache中添加图片.
     * 当然要首先判断LruCache中是否已经存在该图片,若不存在再添加
     */
    public void addBitmapToLruCache(String key,Bitmap bitmap){
        if (getBitmapFromLruCache(key)==null) {
            mLruCache.put(key, bitmap);
        }
    }

    /**
     * 依据key获取其对应的Snapshot
     * @param key
     * @return
     */
    public DiskLruCache.Snapshot getSnapShotByKey(String key){
        DiskLruCache.Snapshot snapshot=null;
        try {
            snapshot=mDiskLruCache.get(key);
        } catch (Exception e) {
        }

        return snapshot;

    }

    /**
     * 依据key获取其对应的Editor
     * @param key
     * @return
     */
    public DiskLruCache.Editor getEditorByKey(String key){
        DiskLruCache.Editor editor=null;
        try {
            editor=mDiskLruCache.edit(key);
        } catch (Exception e) {
        }
        return editor;
    }

    public void flushDiskLruCache(){
        try {
            mDiskLruCache.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static Bitmap getBitmapFromDiskLruCache(String url){
        Bitmap bitmap=null;
        DiskLruCache.Snapshot snapshot=null;
        String key=null;
        FileInputStream fileInputStream=null;
        try {
            key=Utils.getStringByMD5(url);
            snapshot=mDiskLruCache.get(key);
            fileInputStream = (FileInputStream) snapshot.getInputStream(0);
            bitmap=BitmapFactory.decodeStream(fileInputStream);
        } catch (Exception e) {
            System.out.println(""+e.toString());
        }finally{
            if (fileInputStream!=null) {
                try {
                    fileInputStream.close();
                } catch (IOException e) {
                    // TODO Auto-generated catch block
                    e.printStackTrace();
                }
            }
        }

        return bitmap;
    }

}