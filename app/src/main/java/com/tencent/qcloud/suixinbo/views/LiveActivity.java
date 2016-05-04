package com.tencent.qcloud.suixinbo.views;

import android.app.Activity;
import android.app.Dialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Display;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.tencent.av.sdk.AVView;
import com.tencent.qcloud.suixinbo.R;
import com.tencent.qcloud.suixinbo.adapters.ChatMsgListAdapter;
import com.tencent.qcloud.suixinbo.avcontrollers.AvConstants;
import com.tencent.qcloud.suixinbo.avcontrollers.QavsdkControl;
import com.tencent.qcloud.suixinbo.model.ChatEntity;
import com.tencent.qcloud.suixinbo.model.LiveInfoJson;
import com.tencent.qcloud.suixinbo.model.MyCurrentLiveInfo;
import com.tencent.qcloud.suixinbo.model.MySelfInfo;
import com.tencent.qcloud.suixinbo.presenters.EnterLiveHelper;
import com.tencent.qcloud.suixinbo.presenters.LiveHelper;
import com.tencent.qcloud.suixinbo.presenters.viewinface.EnterQuiteRoomView;
import com.tencent.qcloud.suixinbo.presenters.viewinface.LiveView;
import com.tencent.qcloud.suixinbo.utils.Constants;
import com.tencent.qcloud.suixinbo.views.customviews.HeartLayout;
import com.tencent.qcloud.suixinbo.views.customviews.InputTextMsgDialog;
import com.tencent.qcloud.suixinbo.views.customviews.MembersDialog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Timer;
import java.util.TimerTask;


/**
 * Live直播类
 */
public class LiveActivity extends Activity implements EnterQuiteRoomView, LiveView, View.OnClickListener {
    private EnterLiveHelper mEnterRoomProsscessHelper;
    private LiveHelper mLiveHelper;
    private static final String TAG = LiveActivity.class.getSimpleName();

    private ArrayList<ChatEntity> mArrayListChatEntity;
    private ChatMsgListAdapter mChatMsgListAdapter;
    private static final int MINFRESHINTERVAL = 500;
    private boolean mBoolRefreshLock = false;
    private boolean mBoolNeedRefresh = false;
    private final Timer mTimer = new Timer();
    private ArrayList<ChatEntity> mTmpChatList = new ArrayList<ChatEntity>();//缓冲队列
    private TimerTask mTimerTask = null;
    private static final int REFRESH_LISTVIEW = 5;
    private Dialog mMemberDialog;
    private HeartLayout mHeartLayout;
    private TextView mLikeTv;
	private int requestCount = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);//去掉标题栏
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);//去掉信息栏
        setContentView(R.layout.activity_live);

        //进出房间的协助类
        mEnterRoomProsscessHelper = new EnterLiveHelper(this, this);
        //房间内的交互协助类
        mLiveHelper = new LiveHelper(this, this);

        initView();
        registerReceiver();

        //进入房间流程
        mEnterRoomProsscessHelper.startEnterRoom();

//        QavsdkControl.getInstance().setCameraPreviewChangeCallback();

    }

    private static class MyHandler extends Handler {
        private final WeakReference<LiveActivity> mActivity;

        public MyHandler(LiveActivity activity) {
            mActivity = new WeakReference<LiveActivity>(activity);
        }

        @Override
        public void handleMessage(Message msg) {
            if (null == mActivity.get()) {
                return;
            }
            mActivity.get().processInnerMsg(msg);
        }
    }


    private void processInnerMsg(Message msg) {
        switch (msg.what) {
            case REFRESH_LISTVIEW:
                doRefreshListView();
                break;
        }
        return;
    }


    private final MyHandler mHandler = new MyHandler(this);


    private BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            //AvSurfaceView 初始化成功
            if (action.equals(AvConstants.ACTION_SURFACE_CREATED)) {
                //打开摄像头
                if (MySelfInfo.getInstance().getIdStatus() == Constants.HOST) {
                    mLiveHelper.OpenCameraAndMic();
                } else {
                    //成员请求主播画面
                    String host = MyCurrentLiveInfo.getHostID();
                    mLiveHelper.RequestView(host, 0, 1, AVView.VIDEO_SRC_TYPE_CAMERA, AVView.VIEW_SIZE_TYPE_BIG);
                    MyCurrentLiveInfo.setCurrentRequestCount(1);
                    MyCurrentLiveInfo.setIndexView(0);
                }

            }
            //主播数据OK
            if (action.equals(AvConstants.ACTION_HOST_ENTER)) {
                //主播上线才开始渲染视频
                //主播上线 请求主画面
                if (MySelfInfo.getInstance().getIdStatus() == Constants.MEMBER) {
                    mEnterRoomProsscessHelper.initAvUILayer(avView);

//                        String host = MyCurrentLiveInfo.getHostID();
//                        mLiveHelper.RequestView(host, 0, AVView.VIDEO_SRC_TYPE_CAMERA, AVView.VIEW_SIZE_TYPE_BIG);
                }
            }
            if (action.equals(AvConstants.ACTION_MEMBER_CAMERA_OPEN)) {
                String id = intent.getStringExtra("id");
                Log.i(TAG, "member open camera " + id);
                if (id.equals(MySelfInfo.getInstance().getId())) {//自己直接渲染
                    showVideoView(true,id);
                }else{//
//                    int viewIndex = MyCurrentLiveInfo.getIndexView();
//                    int requestCount = MyCurrentLiveInfo.getCurrentRequestCount();

                    mLiveHelper.RequestView(id, 0, 1, AVView.VIDEO_SRC_TYPE_CAMERA, AVView.VIEW_SIZE_TYPE_BIG);
                }
            }


        }
    };

    private void registerReceiver() {
        IntentFilter intentFilter = new IntentFilter();
        intentFilter.addAction(AvConstants.ACTION_SURFACE_CREATED);
        intentFilter.addAction(AvConstants.ACTION_HOST_ENTER);
        intentFilter.addAction(AvConstants.ACTION_MEMBER_CAMERA_OPEN);
        registerReceiver(mBroadcastReceiver, intentFilter);

    }

    private void unregisterReceiver() {
        unregisterReceiver(mBroadcastReceiver);
    }

    /**
     * 初始化UI
     */
    private View avView;
    private TextView BtnBack, BtnInput, Btnflash, BtnSwitch, BtnBeauty, BtnMic, BtnScreen, BtnHeart, BtnNormal, mVideoChat;
    private ListView mListViewMsgItems;
    private LinearLayout mHostbottomLy, mMemberbottomLy;
    private FrameLayout mFullControllerUi;

    private void initView() {

        mHostbottomLy = (LinearLayout) findViewById(R.id.host_bottom_layout);
        mMemberbottomLy = (LinearLayout) findViewById(R.id.member_bottom_layout);
        mVideoChat = (TextView) findViewById(R.id.video_interact);
        mHeartLayout = (HeartLayout) findViewById(R.id.heart_layout);
        if (MySelfInfo.getInstance().getIdStatus() == Constants.HOST) {
            mHostbottomLy.setVisibility(View.VISIBLE);
            mMemberbottomLy.setVisibility(View.GONE);
            Btnflash = (TextView) findViewById(R.id.flash_btn);
            BtnSwitch = (TextView) findViewById(R.id.switch_cam);
            BtnBeauty = (TextView) findViewById(R.id.beauty_btn);
            BtnMic = (TextView) findViewById(R.id.mic_btn);
            BtnScreen = (TextView) findViewById(R.id.fullscreen_btn);
            mVideoChat.setVisibility(View.VISIBLE);
            Btnflash.setOnClickListener(this);
            BtnSwitch.setOnClickListener(this);
            BtnBeauty.setOnClickListener(this);
            BtnMic.setOnClickListener(this);
            BtnScreen.setOnClickListener(this);
            mVideoChat.setOnClickListener(this);

            mMemberDialog = new MembersDialog(this, R.style.dialog);

        } else {
            mMemberbottomLy.setVisibility(View.VISIBLE);
            mHostbottomLy.setVisibility(View.GONE);
            BtnInput = (TextView) findViewById(R.id.message_input);
            BtnInput.setOnClickListener(this);
            mLikeTv = (TextView) findViewById(R.id.member_send_good);
            mLikeTv.setOnClickListener(this);
            mVideoChat.setVisibility(View.GONE);
        }
        BtnNormal = (TextView) findViewById(R.id.normal_btn);
        BtnNormal.setOnClickListener(this);
        mFullControllerUi = (FrameLayout) findViewById(R.id.controll_ui);

        avView = findViewById(R.id.av_video_layer_ui);
        //直接初始化SurfaceView
//        mEnterRoomProsscessHelper.initAvUILayer(avView);
        BtnBack = (TextView) findViewById(R.id.btn_back);
        BtnBack.setOnClickListener(this);

        mListViewMsgItems = (ListView) findViewById(R.id.im_msg_listview);
        mArrayListChatEntity = new ArrayList<ChatEntity>();
        mChatMsgListAdapter = new ChatMsgListAdapter(this, mListViewMsgItems, mArrayListChatEntity);
        mListViewMsgItems.setAdapter(mChatMsgListAdapter);
    }


    @Override
    protected void onResume() {
        super.onResume();
        QavsdkControl.getInstance().onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        QavsdkControl.getInstance().onPause();
    }


    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver();
        QavsdkControl.getInstance().onDestroy();
    }


    /**
     * 点击Back键
     */
    @Override
    public void onBackPressed() {
        mEnterRoomProsscessHelper.QuiteLive();

    }

    /**
     * 完成进出房间流程
     *
     * @param id_status
     * @param isSucc
     */
    @Override
    public void EnterRoomComplete(int id_status, boolean isSucc) {
        Toast.makeText(LiveActivity.this, "EnterRoomComplete " + id_status + " isSucc " + isSucc, Toast.LENGTH_SHORT).show();
        if (isSucc == true) {
            //IM初始化
            mLiveHelper.initTIMGroup("" + MyCurrentLiveInfo.getRoomNum());

            if (id_status == Constants.HOST) {//主播方式加入房间成功
                //开启摄像头渲染画面
                Log.i(TAG, "createlive EnterRoomComplete isSucc" + isSucc);
                mEnterRoomProsscessHelper.initAvUILayer(avView);
            } else {//以成员方式加入房间成功
            }
        }
    }


    @Override
    public void QuiteRoomComplete(int id_status, boolean succ, LiveInfoJson liveinfo) {
//        Toast.makeText(LiveActivity.this, "" + liveinfo.getTitle()+"end", Toast.LENGTH_SHORT).show();
        finish();
    }

    /**
     * 开启本地渲染
     */
    @Override
    public void showVideoView(boolean isLocal, String id) {
        //渲染本地界面
        if (isLocal == true) {
            Log.i(TAG, "showVideoView host :" + MySelfInfo.getInstance().getId());
            QavsdkControl.getInstance().setSelfId(MySelfInfo.getInstance().getId());
            QavsdkControl.getInstance().setLocalHasVideo(true, MySelfInfo.getInstance().getId());
            //通知用户服务器
            mEnterRoomProsscessHelper.notifyServerCreateRoom();

        } else {
            QavsdkControl.getInstance().setRemoteHasVideo(true, id, AVView.VIDEO_SRC_TYPE_CAMERA);
        }

    }


    @Override
    public void showInviteDialog() {
        Toast.makeText(LiveActivity.this, "yes i receive a host invitation and open my Camera", Toast.LENGTH_SHORT).show();
        mLiveHelper.OpenCameraAndMic();
        mLiveHelper.sendC2CMessage(Constants.AVIMCMD_MUlTI_JOIN, MyCurrentLiveInfo.getHostID());
    }

    @Override
    public void refreshText(String text, String name) {
        if (text != null) {
            refreshTextListView(name, text, Constants.TEXT_TYPE);
        }
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_back:
                mEnterRoomProsscessHelper.QuiteLive();
                break;
            case R.id.message_input:
                inputMsgDialog();
                break;
            case R.id.member_send_good:
                // 添加飘星动画
                mHeartLayout.addFavor();
                break;
            case R.id.flash_btn:

                if (mLiveHelper.isFrontCamera() == true) {
                    Toast.makeText(LiveActivity.this, "this is front cam", Toast.LENGTH_SHORT).show();
                } else {
                    mLiveHelper.toggleFlashLight();
                }
                break;
            case R.id.switch_cam:
                mLiveHelper.switchCamera();
                break;
            case R.id.beauty_btn:

                break;
            case R.id.mic_btn:
                if (mLiveHelper.isMicOpen() == true) {
                    BtnMic.setBackgroundResource(R.drawable.icon_mic_close);
                    mLiveHelper.muteMic();
                } else {
                    BtnMic.setBackgroundResource(R.drawable.icon_mic_open);
                    mLiveHelper.openMic();
                }

                break;
            case R.id.fullscreen_btn:
                mFullControllerUi.setVisibility(View.INVISIBLE);
                BtnNormal.setVisibility(View.VISIBLE);
                break;
            case R.id.normal_btn:
                mFullControllerUi.setVisibility(View.VISIBLE);
                BtnNormal.setVisibility(View.GONE);
                break;
            case R.id.video_interact:
                mMemberDialog.show();
                break;
        }
    }


    /**
     * 发消息弹出框
     */
    private void inputMsgDialog() {
        InputTextMsgDialog inputMsgDialog = new InputTextMsgDialog(this, R.style.inputdialog, mLiveHelper, this);
        WindowManager windowManager = getWindowManager();
        Display display = windowManager.getDefaultDisplay();
        WindowManager.LayoutParams lp = inputMsgDialog.getWindow().getAttributes();

        lp.width = (int) (display.getWidth()); //设置宽度
        inputMsgDialog.getWindow().setAttributes(lp);
        inputMsgDialog.setCancelable(true);
        inputMsgDialog.show();
    }


    /**
     * 消息刷新显示
     *
     * @param name    发送者
     * @param context 内容
     * @param type    类型 （上线线消息和 聊天消息）
     */
    public void refreshTextListView(String name, String context, int type) {
        ChatEntity entity = new ChatEntity();
        entity.setSenderName(name);
        entity.setContext(context);
        entity.setType(type);
        //mArrayListChatEntity.add(entity);
        notifyRefreshListView(entity);
        //mChatMsgListAdapter.notifyDataSetChanged();

        mListViewMsgItems.setVisibility(View.VISIBLE);
        Log.d(TAG, "refreshTextListView height " + mListViewMsgItems.getHeight());

        if (mListViewMsgItems.getCount() > 1) {
            if (true)
                mListViewMsgItems.setSelection(0);
            else
                mListViewMsgItems.setSelection(mListViewMsgItems.getCount() - 1);
        }
    }


    /**
     * 通知刷新消息ListView
     */
    private void notifyRefreshListView(ChatEntity entity) {
        mBoolNeedRefresh = true;
        mTmpChatList.add(entity);
        if (mBoolRefreshLock) {
            return;
        } else {
            doRefreshListView();
        }
    }

    /**
     * 刷新ListView并重置状态
     */
    private void doRefreshListView() {
        if (mBoolNeedRefresh) {
            mBoolRefreshLock = true;
            mBoolNeedRefresh = false;
            mArrayListChatEntity.addAll(mTmpChatList);
            mTmpChatList.clear();
            mChatMsgListAdapter.notifyDataSetChanged();

            if (null != mTimerTask) {
                mTimerTask.cancel();
            }
            mTimerTask = new TimerTask() {
                @Override
                public void run() {
                    Log.v(TAG, "doRefreshListView->task enter with need:" + mBoolNeedRefresh);
                    mHandler.sendEmptyMessage(REFRESH_LISTVIEW);
                }
            };
            //mTimer.cancel();
            mTimer.schedule(mTimerTask, MINFRESHINTERVAL);
        } else {
            mBoolRefreshLock = false;
        }
    }
}
