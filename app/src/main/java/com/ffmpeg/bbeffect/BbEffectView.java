package com.ffmpeg.bbeffect;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.text.TextUtils;
import android.util.AttributeSet;
import android.view.Surface;
import android.view.SurfaceView;
import android.view.View;

import java.lang.ref.WeakReference;

/**
 * @auther SuZhanFeng
 * @date 2020/2/24
 * @desc
 */
public class BbEffectView extends SurfaceView {

    static {
        System.loadLibrary("native-lib");
    }
    private EffectPlayListener mListener;
    private int mPriority;
    private HandlerThread handlerThread;
    private MsgHandler mHandler;
    private Handler glesHandler;

    private boolean isEffectPlaying;
    private boolean isNeedStop;
    private String mEffectPath;

    public BbEffectView(Context context) {
        super(context);
        init();
    }

    public BbEffectView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public BbEffectView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }
    private void init(){
        setVisibility(GONE);
        getHolder().setFormat(PixelFormat.TRANSLUCENT);
        setZOrderOnTop(true);
        initEffectPlay();
        handlerThread = new HandlerThread("com.ffmpeng.effect");
        handlerThread.start();
        mHandler = new MsgHandler(this);
        glesHandler = new Handler(handlerThread.getLooper());
    }

    public void setEffectPath(int priority, final String path) {
        if(TextUtils.isEmpty(path)){
            // 表示需要中止当前动效
            if(isEffectPlaying){
                isNeedStop = true;
                stopEffect();
            }
        } else {
            mEffectPath = path;
            mPriority = priority;
            if(isNeedStop){
                return;
            }
            setVisibility(View.VISIBLE);
            if(glesHandler == null){
                return;
            }
            glesHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    BbEffectView.this.videoPlay(path, getHolder().getSurface());
                }
            },200);
        }

    }

    private void stopEffect(){
        setVisibility(View.GONE);
        videoStop();
    }

    public native void videoPlay(String path, Surface surface);

    public native void videoStop();

    public native void initEffectPlay();

    public void setEffectPlayListener(EffectPlayListener listener){
        mListener = listener;
    }

    public void onAnimEvent(int type, int ret) {
        if (mHandler == null) {
            return;
        }
        Message message = mHandler.obtainMessage();
        message.arg1 = type;
        message.arg2 = ret;
        mHandler.sendMessage(message);
    }

    private void callBackEvent(int type, int ret){
        if (type == EffectConst.MSG_TYPE_INFO) {
            if (ret == EffectConst.MSG_STAT_EFFECTS_END) {
                isEffectPlaying = false;
                setVisibility(View.GONE);
                if (isNeedStop) {
                    isNeedStop = false;
                    setEffectPath(mPriority, mEffectPath);
                } else {
                    callBack(type, ret);
                }
            } else if(ret == EffectConst.MSG_STAT_EFFECTS_START){
                isEffectPlaying = true;
                callBack(type,ret);
            }
        } else {
            // 表示发生错误
            callBack(type,ret);
        }
    }

    private void callBack(int type, int ret){
        if(mListener!=null){
            mListener.onAnimEvent(mPriority,type,ret);
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        if (mHandler != null) {
            mHandler.removeCallbacksAndMessages(null);
        }
        if (glesHandler != null) {
            glesHandler.removeCallbacksAndMessages(null);
        }

        if (handlerThread != null) {
            handlerThread.interrupt();
            handlerThread.quit();
        }
    }

    /**
     * 销毁环境，可在页面销毁时调用
     */
    public void release() {

    }

    private static class MsgHandler extends Handler {

        private final WeakReference<BbEffectView> effectWeakReference;

        private MsgHandler(BbEffectView effectView) {
            effectWeakReference = new WeakReference<>(effectView);
        }

        @Override
        public void handleMessage(Message msg) {
            BbEffectView effectHelper = effectWeakReference.get();
            if (effectHelper != null) {
                effectHelper.callBackEvent(msg.arg1,msg.arg2);
                super.handleMessage(msg);
            }
        }
    }

}
