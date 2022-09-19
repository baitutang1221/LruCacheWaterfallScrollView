package xiazhenjie.lrucache.waterfall;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.BitmapFactory.Options;
import android.os.Environment;
import android.util.Log;

/**
 * @ClassName Utils
 * @Description TODO
 * @Author xiazhenjie
 * @Date 2022/9/8 17:32
 * @Version 1.0
 */
public class Utils {

    /**
     * 判断SD卡是否存在
     */
    public static boolean isExistSDCard() {
        boolean isExist = false;
        if (Environment.getExternalStorageState().equals(Environment.MEDIA_MOUNTED)) {
            isExist = true;
        }
        return isExist;
    }

    /**
     * 从SD卡中获取图片
     *
     * 注意事项:
     * 这里采用BitmapFactory.decodeFileDescriptor()的方式来避免内存溢出.
     * 而不是用BitmapFactory.decodeFile()的方式
     */
    public static Bitmap getBitmapFromSDCard(String filePath,int requestWidth){
        Bitmap bitmap=null;
        try {
            Options options=new Options();
            options.inJustDecodeBounds=true;
            BitmapFactory.decodeFile(filePath, options);
            options.inSampleSize=calculateInSampleSize(options,requestWidth);
            options.inJustDecodeBounds=false;
            FileInputStream fileInputStream=new FileInputStream(filePath);
            FileDescriptor fileDescriptor=fileInputStream.getFD();
            bitmap=BitmapFactory.decodeFileDescriptor(fileDescriptor, null, options);
            fileInputStream.close();
        } catch (Exception e) {

        }
        return bitmap;
    }

    public static Bitmap fixBitmap(){
        return null;
    }

    /**
     * 计算图片的缩放比例
     */
    public static int calculateInSampleSize(Options options,int requestWidth){
        int inSampleSize=1;
        //SD卡中图片的宽
        int outWidth=options.outWidth;
        if (outWidth>requestWidth) {
            inSampleSize=Math.round((float) outWidth / (float) requestWidth);
        }
        return inSampleSize;
    }



    /**
     * 从网络获取图片且保存至SD卡
     */
    public static void getBitmapFromNetWorkAndSaveToSDCard(String imageUrl,String filePath){
        URL url=null;
        File imageFile=null;
        HttpURLConnection httpURLConnection=null;
        FileOutputStream fileOutputStream=null;
        BufferedOutputStream bufferedOutputStream=null;
        InputStream inputStream=null;
        BufferedInputStream bufferedInputStream=null;
        try {
            url=new URL(imageUrl);
            httpURLConnection=(HttpURLConnection) url.openConnection();
            httpURLConnection.setConnectTimeout(5*1000);
            httpURLConnection.setReadTimeout(10*1000);
            httpURLConnection.setDoInput(true);
            httpURLConnection.setDoOutput(true);
//            if (httpURLConnection.getResponseCode()==HttpStatus.SC_OK) {
            if (httpURLConnection.getResponseCode()==200) {
                imageFile=new File(filePath);
                if (!imageFile.getParentFile().exists()) {
                    imageFile.getParentFile().mkdirs();
                }
                if (!imageFile.exists()) {
                    imageFile.createNewFile();
                }
                fileOutputStream=new FileOutputStream(imageFile);
                bufferedOutputStream=new BufferedOutputStream(fileOutputStream);
                inputStream=httpURLConnection.getInputStream();
                bufferedInputStream=new BufferedInputStream(inputStream);
                int len=0;
                byte [] buffer=new byte[1024];
                while((len=bufferedInputStream.read(buffer))!=-1){
                    bufferedOutputStream.write(buffer, 0, len);
                    bufferedOutputStream.flush();
                }
            } else {
                System.out.println("图片请求失败");
            }
        } catch (Exception e) {
            System.out.println("e="+e.toString());
        }finally{
            try {
                if (fileOutputStream!=null) {
                    fileOutputStream.close();
                }
                if (bufferedOutputStream!=null) {
                    bufferedOutputStream.close();
                }
                if (inputStream!=null) {
                    inputStream.close();
                }
                if (bufferedInputStream!=null) {
                    bufferedInputStream.close();
                }
                if (httpURLConnection!=null) {
                    httpURLConnection.disconnect();
                }
            } catch (Exception e) {
                System.out.println("e="+e.toString());
            }
        }

    }



    /**
     * 获取DiskLruCache的缓存文件夹
     * 注意第二个参数dataType
     * DiskLruCache用一个String类型的唯一值对不同类型的数据进行区分.
     * 比如bitmap,object等文件夹.其中在bitmap文件夹中缓存了图片.
     * card/Android/data/<application package>/cache
     * 如果
     * 缓存数据的存放位置为:
     * /sdSD卡不存在时缓存存放位置为:
     * /data/data/<application package>/cache
     *
     */
    public static File getDiskLruCacheDir(Context context, String dataType) {
        String dirPath;
        File cacheFile = null;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState()) ||
                !Environment.isExternalStorageRemovable()) {
            dirPath=context.getExternalCacheDir().getPath();
        } else {
            dirPath=context.getCacheDir().getPath();
        }
        cacheFile=new File(dirPath+File.separator+dataType);
        return cacheFile;
    }



    /**
     * 获取APP当前版本号
     * @param context
     * @return
     */
    public static int getAppVersionCode(Context context){
        int versionCode=1;
        try {
            String packageName=context.getPackageName();
            PackageManager packageManager=context.getPackageManager();
            PackageInfo packageInfo=packageManager.getPackageInfo(packageName, 0);
            versionCode=packageInfo.versionCode;
        } catch (NameNotFoundException e) {
            e.printStackTrace();
        }
        return versionCode;
    }


    /**
     * 将字符串用MD5编码.
     * 比如在该示例中将url进行MD5编码
     */
    public static String getStringByMD5(String string) {
        String md5String = null;
        try {
            // Create MD5 Hash
            MessageDigest messageDigest = MessageDigest.getInstance("MD5");
            messageDigest.update(string.getBytes());
            byte messageDigestByteArray[] = messageDigest.digest();
            if (messageDigestByteArray == null || messageDigestByteArray.length == 0) {
                return md5String;
            }

            // Create hexadecimal String
            StringBuffer hexadecimalStringBuffer = new StringBuffer();
            int length = messageDigestByteArray.length;
            for (int i = 0; i < length; i++) {
                hexadecimalStringBuffer.append(Integer.toHexString(0xFF & messageDigestByteArray[i]));
            }
            md5String = hexadecimalStringBuffer.toString();
            return md5String;
        } catch (NoSuchAlgorithmException e) {
            e.printStackTrace();
        }
        return md5String;
    }


    /**
     * 从网络获取图片且保存至SD卡中的缓存
     */
    public static boolean getBitmapFromNetWorkAndSaveToDiskLruCache(String imageUrl,OutputStream outputStream){
        boolean isSuccessfull = false;
        URL url=null;
        HttpURLConnection httpURLConnection=null;
        BufferedOutputStream bufferedOutputStream=null;
        InputStream inputStream=null;
        BufferedInputStream bufferedInputStream=null;
        try {
            url = new URL(imageUrl);
            httpURLConnection = (HttpURLConnection) url.openConnection();
            // 设置它的请求方式
            httpURLConnection.setRequestMethod("GET");
            // 设置它的请求超时时间
            httpURLConnection.setConnectTimeout(5000);
            // 设置超时读取时间
            httpURLConnection.setReadTimeout(3000);

            if (httpURLConnection.getResponseCode()==200) {
                bufferedOutputStream=new BufferedOutputStream(outputStream);
                inputStream=httpURLConnection.getInputStream();
                bufferedInputStream=new BufferedInputStream(inputStream);
                int len=0;
                byte [] buffer=new byte[1024];
                while((len=bufferedInputStream.read(buffer))!=-1){
                    bufferedOutputStream.write(buffer, 0, len);
                    bufferedOutputStream.flush();
                }
                isSuccessfull=true;
            } else {
                System.out.println("图片请求失败");
            }
        } catch (Exception e) {
            System.out.println("e="+e.toString());
        }finally{
            try {
                if (bufferedOutputStream!=null) {
                    bufferedOutputStream.close();
                }
                if (inputStream!=null) {
                    inputStream.close();
                }
                if (bufferedInputStream!=null) {
                    bufferedInputStream.close();
                }
                if (httpURLConnection!=null) {
                    httpURLConnection.disconnect();
                }
            } catch (Exception e) {
                System.out.println("e="+e.toString());
            }
        }
        return isSuccessfull;
    }

}