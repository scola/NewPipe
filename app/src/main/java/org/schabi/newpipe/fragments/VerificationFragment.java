package org.schabi.newpipe.fragments;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.github.shadowsocks.Core;
import com.mukeshsolanki.OtpView;

import org.schabi.newpipe.BaseFragment;
import org.schabi.newpipe.R;
import org.schabi.newpipe.util.Constants;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

public class VerificationFragment extends BaseFragment {

    private static final String TAG = "VerificationFragment";

    private OtpView otpView;
    private TextView resultText;

    private final OkHttpClient client = new OkHttpClient();
    private Context appContext;

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState
    ) {
        return inflater.inflate(R.layout.fragment_verification, container, false);
    }

    @Override
    protected void initViews(@NonNull View rootView, @Nullable Bundle savedInstanceState) {
        super.initViews(rootView, savedInstanceState);

        otpView = rootView.findViewById(R.id.otp_view);
        resultText = rootView.findViewById(R.id.text_result);

        // 获取邀请码按钮
        TextView getInvitationBtn = rootView.findViewById(R.id.btn_get_invitation);
        getInvitationBtn.setOnClickListener(v -> {
            try {
                Context context = requireContext();
                String url = "http://xhslink.com/o/QMeYFScfKu";
                android.content.Intent intent = new android.content.Intent(android.content.Intent.ACTION_VIEW);
                intent.setData(android.net.Uri.parse(url));
                context.startActivity(intent);
            } catch (Exception e) {
                safeShowResult("无法打开链接", false);
            }
        });

        otpView.setOtpCompletionListener(otp -> {
            if (TextUtils.isEmpty(otp)) {
                safeShowResult("请输入完整的邀请码", false);
            } else {
                verifyInvitation(otp.toUpperCase());
            }
        });
    }


    private void verifyInvitation(String key) {
        String url = "http://104.194.78.15:8000/verify-invitation/?key=" + key;

        Request request = new Request.Builder()
                .url(url)
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                Log.e(TAG, "网络请求失败", e);
                safeShowResult("网络错误，请重试\n" + e.getClass().getSimpleName() + ": " + e.getMessage(), false);
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (!response.isSuccessful()) {
                    Log.e(TAG, "服务器返回错误: " + response.code());
                    safeShowResult("服务器错误: " + response.code(), false);
                    return;
                }

                String body = response.body() != null ? response.body().string() : "";
                Log.d(TAG, "服务器返回: " + body);

                try {
                    JSONObject json = new JSONObject(body);
                    boolean valid = json.optBoolean("valid", false);
                    final String message;

                    if (valid) {
                        message = "邀请码验证通过，请稍等";

                        if (isAdded()) {
                            // 保存验证结果
                            PreferenceManager.getDefaultSharedPreferences(getActivity())
                                    .edit()
                                    .putBoolean(Constants.KEY_VERIFY_RESULT, true)
                                    .apply();

                            // 检查是否返回了新的邀请码列表
                            if (json.has("new_invitations")) {
                                try {
                                    // 可能是数组或字符串
                                    Object invites = json.get("new_invitations");
                                    String invitesStr;
                                    if (invites instanceof org.json.JSONArray) {
                                        invitesStr = ((org.json.JSONArray) invites).toString();
                                    } else {
                                        invitesStr = String.valueOf(invites);
                                    }

                                    // 保存新邀请码字符串
                                    PreferenceManager.getDefaultSharedPreferences(getActivity())
                                            .edit()
                                            .putString(Constants.KEY_NEW_INVITATION, invitesStr)
                                            .apply();

//                                    new Handler(Looper.getMainLooper()).postDelayed(() -> {
//                                        if (appContext != null) {
//                                            Toast.makeText(appContext, "已获得5个邀请码，在关于页中查看", Toast.LENGTH_LONG).show();
//                                        }
//                                    }, 3000);

                                    Log.d(TAG, "已保存新邀请码: " + invitesStr);
                                } catch (Exception e) {
                                    Log.e(TAG, "保存新邀请码失败", e);
                                }
                            }

                            Core.INSTANCE.startService();
                        }
                    } else {
                        String error = json.optString("error", "Invalid");
                        if ("Used".equalsIgnoreCase(error)) {
                            message = "邀请码已使用";
                        } else {
                            message = "邀请码无效";
                        }
                    }

                    safeShowResult(message, valid);

                } catch (Exception e) {
                    Log.e(TAG, "解析 JSON 失败", e);
                    safeShowResult("解析错误，请重试", false);
                }
            }
        });
    }



    @Override
    public void onResume() {
        super.onResume();

        otpView.postDelayed(() -> {
            if (!isAdded() || otpView == null) return;

            otpView.setFocusableInTouchMode(true);
            otpView.requestFocus();

            Context context = getContext();
            if (context != null) {
                InputMethodManager imm = (InputMethodManager)
                        context.getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) {
                    imm.showSoftInput(otpView, InputMethodManager.SHOW_IMPLICIT);
                }
            }
        }, 300);
    }

    @Override
    public void onPause() {
        super.onPause();

        if (!isAdded() || otpView == null) return;

        Context context = getContext();
        if (context != null) {
            InputMethodManager imm = (InputMethodManager)
                    context.getSystemService(Context.INPUT_METHOD_SERVICE);
            if (imm != null) {
                imm.hideSoftInputFromWindow(otpView.getWindowToken(), 0);
            }
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();

        if (appContext != null && PreferenceManager.getDefaultSharedPreferences(appContext).getBoolean(Constants.KEY_VERIFY_RESULT, false)) {
            new Handler(Looper.getMainLooper()).post(() -> {
                if (appContext != null) {
                    Toast.makeText(appContext, "已获得5个邀请码，在关于页中查看", Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    @Override
    public void onAttach(@NonNull Context context) {
        super.onAttach(context);
        appContext = context.getApplicationContext();
    }

    private void safeShowResult(String message, boolean success) {
        if (!isAdded() || resultText == null) return;

        getActivity().runOnUiThread(() -> {
            resultText.setText(message);
            int color = getResources().getColor(
                    success ? android.R.color.holo_green_light : android.R.color.holo_red_light
            );
            resultText.setTextColor(color);
        });
    }
}
