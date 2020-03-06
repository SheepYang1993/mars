/*
 * Tencent is pleased to support the open source community by making Mars available.
 * Copyright (C) 2016 THL A29 Limited, a Tencent company. All rights reserved.
 *
 * Licensed under the MIT License (the "License"); you may not use this file except in
 * compliance with the License. You may obtain a copy of the License at
 * http://opensource.org/licenses/MIT
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License is
 * distributed on an "AS IS" basis, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND,
 * either express or implied. See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.tencent.mars.sample.chat;

import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.widget.Toast;

import com.blankj.utilcode.util.ConvertUtils;
import com.tencent.mars.sample.SampleApplicaton;
import com.tencent.mars.sample.chat.proto.Chat;
import com.tencent.mars.sample.proto.Main;
import com.tencent.mars.sample.utils.print.MemoryDump;
import com.tencent.mars.sample.wrapper.TaskProperty;
import com.tencent.mars.sample.wrapper.remote.NanoMarsTaskWrapper;
import com.tencent.mars.stn.StnLogic;
import com.tencent.mars.xlog.Log;

import java.util.Arrays;

/**
 * Text messaging task
 * <p/>
 * Created by zhaoyuan on 16/2/29.
 */
@TaskProperty(
        host = "192.168.8.108",
        path = "/mars/sendmessage",
        cmdID = Main.CmdID.CMD_ID_SEND_MESSAGE_VALUE,
        longChannelSupport = true,
        shortChannelSupport = false
)
public class TextMessageTask extends NanoMarsTaskWrapper<Chat.SendMessageRequest.Builder, Chat.SendMessageResponse.Builder> {

    //主信令
    private int mainId;
    //手机号
    private String phone;
    private byte[] body;


    private Runnable callback = null;

    private Runnable onOK = null;
    private Runnable onError = null;

    private Handler uiHandler = new Handler(Looper.getMainLooper());

    public TextMessageTask(int mainId, String phone, byte[] body) {
        super(Chat.SendMessageRequest.newBuilder(), Chat.SendMessageResponse.newBuilder());
        this.mainId = mainId;
        this.phone = phone;
        this.body = body;
        request.setAccessToken("test_token");
        request.setFrom(SampleApplicaton.accountInfo.userName);
        request.setTo("all");
        request.setText("text");
        request.setTopic("topicName");

    }

    @Override
    public void onPreEncode(Chat.SendMessageRequest.Builder request) {
        // TODO: Not thread-safe here
    }

    @Override
    public void onPostDecode(Chat.SendMessageResponse.Builder response) {
//        if (response.getErrCode() == Chat.SendMessageResponse.Error.ERR_OK_VALUE) {
        callback = onOK;
//
//        } else {
//            callback = onError;
//        }
    }

    @Override
    public byte[] req2buf() {
        byte[] byPhone = null;
        if (!TextUtils.isEmpty(phone) && phone.length() == 11) {
            byPhone = str2Bcd(phone);
        }
        int byteSize = 5;
        if (body != null && body.length > 0) {
            byteSize += body.length;
        }
        if (byPhone != null && byPhone.length > 0) {
            byteSize += byPhone.length;
        }
        byte[] result = new byte[byteSize];
        //主信令，1个字节
        result[0] = (byte) mainId;
        //包长，2个字节
        byte[] bodyLength = getBodyLength(byteSize - 3);
        System.arraycopy(bodyLength, 0, result, 1, bodyLength.length);
        //手机号码，6个字节
        if (byPhone != null) {
            System.arraycopy(byPhone, 0, result, 3, byPhone.length);
        }
        //body，任意字节
        if (body != null) {
            System.arraycopy(body, 0, result, byteSize - 2 - body.length, body.length);
        }
        //校验码，1个字节
        result[byteSize - 2] = 0x03;
        //包尾，1个字节
        result[byteSize - 1] = 0x0d;
        return result;
    }

    /**
     * 获取包长
     *
     * @param bodySize
     * @return
     */
    private byte[] getBodyLength(int bodySize) {
        byte[] size = new byte[]{0x00, 0x00};
        try {
            size[0] = (byte) (bodySize >> 8);
            size[1] = (byte) bodySize;
        } catch (Exception e) {
            e.printStackTrace();
            size = new byte[]{0x00, 0x00};
        }
        return size;
    }

    @Override
    public int buf2resp(byte[] bytesOrigin) {
        try {
            int[] bytes = new int[bytesOrigin.length];
            for (int i = 0; i < bytesOrigin.length; i++) {
                bytes[i] = 0xff & bytesOrigin[i];
            }
            Log.v("测试测试", "服务端返回结果:" + Arrays.toString(bytes));
            onPostDecode(null);
            return StnLogic.RESP_FAIL_HANDLE_NORMAL;

        } catch (Exception e) {
            e.printStackTrace();
            Log.v("测试测试", "服务端返回结果:异常了");
        }

        return StnLogic.RESP_FAIL_HANDLE_TASK_END;
    }

    @Override
    public void onTaskEnd(int errType, int errCode) {
        if (callback == null) {
            callback = onError;
        }

        uiHandler.post(callback);
    }

    public TextMessageTask onOK(Runnable onOK) {
        this.onOK = onOK;
        return this;
    }

    public TextMessageTask onError(Runnable onError) {
        this.onError = onError;
        return this;
    }

    /**
     * @功能: 10进制串转为BCD码
     * @参数: 10进制串
     * @结果: BCD码
     */
    public static byte[] str2Bcd(String asc) {
        int len = asc.length();
        int mod = len % 2;
        if (mod != 0) {
            asc = "0" + asc;
            len = asc.length();
        }
        byte abt[] = new byte[len];
        if (len >= 2) {
            len = len / 2;
        }
        byte bbt[] = new byte[len];
        abt = asc.getBytes();
        int j, k;
        for (int p = 0; p < asc.length() / 2; p++) {
            if ((abt[2 * p] >= '0') && (abt[2 * p] <= '9')) {
                j = abt[2 * p] - '0';
            } else if ((abt[2 * p] >= 'a') && (abt[2 * p] <= 'z')) {
                j = abt[2 * p] - 'a' + 0x0a;
            } else {
                j = abt[2 * p] - 'A' + 0x0a;
            }
            if ((abt[2 * p + 1] >= '0') && (abt[2 * p + 1] <= '9')) {
                k = abt[2 * p + 1] - '0';
            } else if ((abt[2 * p + 1] >= 'a') && (abt[2 * p + 1] <= 'z')) {
                k = abt[2 * p + 1] - 'a' + 0x0a;
            } else {
                k = abt[2 * p + 1] - 'A' + 0x0a;
            }
            int a = (j << 4) + k;
            byte b = (byte) a;
            bbt[p] = b;
        }
        return bbt;
    }
}
