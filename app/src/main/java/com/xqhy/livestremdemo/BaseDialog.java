package com.xqhy.livestremdemo;

import android.app.Dialog;
import android.content.Context;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;

import com.xqhy.livestremdemo.databinding.BaseDialogBinding;


public class BaseDialog extends Dialog {
    private BaseDialogBinding mBinding;
    private String mConfirmBtnText;
    private String mCancelBtnText;
    private String mRemindContext;
    private int mConfirmBtnTextColor = -1;
    private int mCancelBtnTextColor = -1;
    private boolean isCancelVisible = true;
    private boolean isConfirmVisible = true;
    private OnClickListener mOnClickListener;
    private String mTitleContext;

    public BaseDialog(@NonNull Context context) {
        super(context, R.style.Dialog);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        mBinding = BaseDialogBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());

        Window window = getWindow();
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);

        setCanceledOnTouchOutside(false);

        initView();
        clickListener();
    }

    private void initView() {
        if(!TextUtils.isEmpty(mCancelBtnText)) {
            mBinding.btnCancel.setText(mCancelBtnText);
        }
        if(!TextUtils.isEmpty(mConfirmBtnText)) {
            mBinding.btnConfirm.setText(mConfirmBtnText);
        }
        if(!TextUtils.isEmpty(mRemindContext)) {
            mBinding.tvRemindContent.setText(mRemindContext);
        }

        if (mConfirmBtnTextColor != -1) {
            mBinding.btnConfirm.setTextColor(mConfirmBtnTextColor);
        }

        if(mCancelBtnTextColor != -1){
            mBinding.btnCancel.setTextColor(mCancelBtnTextColor);
        }
        if(!TextUtils.isEmpty(mTitleContext)){
            mBinding.tvTitle.setVisibility(View.VISIBLE);
            mBinding.tvTitle.setText(mTitleContext);
        }
        mBinding.btnCancel.setVisibility(isCancelVisible? View.VISIBLE: View.GONE);
        mBinding.btnConfirm.setVisibility(isConfirmVisible? View.VISIBLE: View.GONE);
    }

    private void clickListener() {
        mBinding.btnCancel.setOnClickListener(v -> {
            dismiss();
            if (mOnClickListener != null) {
                mOnClickListener.clickCancel();
            }
        });
        mBinding.btnConfirm.setOnClickListener(v -> {
            dismiss();
            if (mOnClickListener != null) {
                mOnClickListener.clickConfirm();
            }
        });
    }

    /**
     * 设置内容
     */
    public void setRemindContent(String text) {
        mRemindContext = text;
    }

    /**
     * 设置确认按钮文案
     */
    public void setConfirmBtnText(String text) {
        mConfirmBtnText = text;
    }

    /**
     * 设置取消按钮文案
     */
    public void setCancelBtnText(String text) {
        mCancelBtnText = text;
    }

    /**
     * 设置提醒内容居中
     */
    public void setRemindContenttCenter() {
        mBinding.tvRemindContent.setGravity(Gravity.CENTER);
    }

    public void setConfirmBtnTextColor(int color) {
        mConfirmBtnTextColor = color;
    }

    public void setCancelBtnTextColor(int color) {
        mCancelBtnTextColor = color;
    }

    public void setOnClickListener(OnClickListener btnClickEvents) {
        mOnClickListener = btnClickEvents;
    }

    public interface OnClickListener {
        void clickConfirm();

        void clickCancel();
    }
}
